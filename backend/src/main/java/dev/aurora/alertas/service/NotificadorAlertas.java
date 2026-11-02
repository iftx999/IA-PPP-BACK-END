package dev.aurora.alertas.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.aurora.acesso.Destinatarios;
import dev.aurora.acesso.Destinatarios.Destinatario;
import dev.aurora.alertas.model.Alerta;
import dev.aurora.alertas.model.PreferenciaEmail;
import dev.aurora.alertas.model.TipoAlerta;
import dev.aurora.alertas.repository.AlertaRepository;
import dev.aurora.alertas.repository.PreferenciaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Envia por e-mail os alertas novos (ADR 0024): um resumo por pessoa com os alertas que
 * ela pode ver e que a preferência dela pede. Roda no contexto do sistema de um tenant,
 * depois de cada avaliação. Um alerta só sai da fila quando todos os seus destinatários
 * receberam; com falha, volta na próxima execução.
 */
@Service
public class NotificadorAlertas {

	private static final Logger LOG = LoggerFactory.getLogger(NotificadorAlertas.class);

	/** Alerta mais antigo que isso não vira e-mail (ex.: depois de uma parada longa). */
	static final Duration JANELA = Duration.ofHours(24);

	private final AlertaRepository alertas;

	private final PreferenciaRepository preferencias;

	private final Destinatarios destinatarios;

	private final EnvioDeEmail envio;

	private final Clock relogio;

	private final MeterRegistry metricas;

	private final String enderecoFront;

	NotificadorAlertas(AlertaRepository alertas, PreferenciaRepository preferencias, Destinatarios destinatarios,
			EnvioDeEmail envio, Clock relogio, MeterRegistry metricas,
			@Value("${aurora.identidade.url-retorno}") String enderecoFront) {
		this.alertas = alertas;
		this.preferencias = preferencias;
		this.destinatarios = destinatarios;
		this.envio = envio;
		this.relogio = relogio;
		this.metricas = metricas;
		this.enderecoFront = enderecoFront;
	}

	public record Resultado(int alertas, int emails, int falhas) {

		@Override
		public String toString() {
			return alertas + " alertas notificados, " + emails + " e-mails, " + falhas + " falhas";
		}
	}

	/** Permissão que dá acesso à tela de cada tipo de alerta. */
	static String permissaoDo(TipoAlerta tipo) {
		return switch (tipo) {
			case TRILHA_ALTERADA -> "auditoria:ler";
			case FONTE_ATRASADA -> "integracao:gerenciar";
			case PRAZO_CONTRATUAL -> "contrato:ler";
			case ORDEM_DIVERGENTE -> "campanha:gerenciar";
			case INDICADOR_ABAIXO_DA_META, META_FORA_DO_RITMO -> "indicador:ler";
		};
	}

	public Resultado notificar() {
		if (!envio.ligado()) {
			return new Resultado(0, 0, 0);
		}
		var pendentes = alertas.aNotificar(relogio.instant().minus(JANELA));
		if (pendentes.isEmpty()) {
			return new Resultado(0, 0, 0);
		}

		var cache = new HashMap<String, List<Destinatario>>();
		var pessoas = new HashMap<UUID, Destinatario>();
		var candidatos = new LinkedHashMap<Alerta, List<Destinatario>>();
		for (var alerta : pendentes) {
			var permissao = permissaoDo(alerta.tipo());
			var lista = cache.computeIfAbsent(permissao + "|" + alerta.unidadeId(),
					k -> destinatarios.comPermissao(permissao, alerta.unidadeId()));
			lista.forEach(d -> pessoas.put(d.usuarioId(), d));
			candidatos.put(alerta, lista);
		}
		var preferenciaDe = preferencias.listar(pessoas.keySet());

		var porPessoa = new LinkedHashMap<UUID, List<Alerta>>();
		var destinatariosDoAlerta = new HashMap<UUID, Set<UUID>>();
		candidatos.forEach((alerta, lista) -> {
			var ids = new HashSet<UUID>();
			for (var d : lista) {
				if (preferenciaDe.getOrDefault(d.usuarioId(), PreferenciaEmail.CRITICOS).recebe(alerta.severidade())) {
					porPessoa.computeIfAbsent(d.usuarioId(), k -> new ArrayList<>()).add(alerta);
					ids.add(d.usuarioId());
				}
			}
			destinatariosDoAlerta.put(alerta.id(), ids);
		});

		var falharam = new HashSet<UUID>();
		int enviados = 0;
		for (var entrada : porPessoa.entrySet()) {
			var pessoa = pessoas.get(entrada.getKey());
			var mensagem = MensagemDeAlertas.montar(pessoa.nome(), entrada.getValue(), enderecoFront);
			if (enviar(pessoa, mensagem)) {
				enviados++;
			}
			else {
				falharam.add(pessoa.usuarioId());
			}
		}

		var concluidos = pendentes.stream()
			.map(Alerta::id)
			.filter(id -> destinatariosDoAlerta.get(id).stream().noneMatch(falharam::contains))
			.toList();
		alertas.marcarNotificados(concluidos);
		return new Resultado(concluidos.size(), enviados, falharam.size());
	}

	private boolean enviar(Destinatario pessoa, MensagemDeAlertas.Mensagem mensagem) {
		var resultado = "enviado";
		try {
			envio.enviar(pessoa.email(), mensagem.assunto(), mensagem.texto(), mensagem.html());
			return true;
		}
		catch (RuntimeException erro) {
			resultado = "falha";
			// Sem o e-mail no log (LGPD): o id identifica a pessoa para quem opera.
			LOG.warn("Falha ao enviar alertas por e-mail ao usuário {}: {}", pessoa.usuarioId(), erro.getMessage());
			return false;
		}
		finally {
			metricas.counter("aurora.alertas.emails", "resultado", resultado).increment();
		}
	}

}
