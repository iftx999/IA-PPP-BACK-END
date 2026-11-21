package dev.aurora.operacao.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.operacao.model.EquipeResumo;
import dev.aurora.operacao.model.Fato;
import dev.aurora.operacao.model.Gatilho;
import dev.aurora.operacao.model.NovaSugestao;
import dev.aurora.operacao.repository.OperacaoRepository;
import dev.aurora.operacao.repository.SugestaoRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gatilhos de reotimização (J7; doc 10, 10.8; ADR 0049), num tenant (contexto do
 * sistema): emergencial aberta fora das rotas, OS paralisada além do limite, OS devolvida
 * por equipe que segue com outras OS, equipe atrasada além do limite. Abre uma sugestão
 * por fato e expira as abertas cujo fato deixou de valer. Nunca recalcula sozinho.
 */
@Service
public class DetectorDeReotimizacao {

	/** Resultado de uma passada num tenant. */
	public record Passada(int abertas, int expiradas) {
	}

	private final OperacaoRepository operacao;

	private final SugestaoRepository sugestoes;

	private final Clock relogio;

	private final Duration paralisacaoLonga;

	private final int atrasoMin;

	DetectorDeReotimizacao(OperacaoRepository operacao, SugestaoRepository sugestoes, Clock relogio,
			@Value("${aurora.operacao.paralisacao-longa:PT30M}") Duration paralisacaoLonga,
			@Value("${aurora.operacao.atraso-min:30}") int atrasoMin) {
		this.operacao = operacao;
		this.sugestoes = sugestoes;
		this.relogio = relogio;
		this.paralisacaoLonga = paralisacaoLonga;
		this.atrasoMin = atrasoMin;
	}

	@Transactional
	public Passada detectar() {
		var tenant = ContextoAcesso.exigir().tenantId();
		var agora = relogio.instant();
		var hoje = LocalDate.now(relogio);
		var inicioDoDia = hoje.atStartOfDay(relogio.getZone()).toInstant();
		var vigentes = new HashSet<String>();
		var novas = new ArrayList<NovaSugestao>();

		for (var f : sugestoes.emergenciais(hoje)) {
			var chave = "EMERGENCIAL:" + f.osId() + ":" + hoje;
			vigentes.add(chave);
			novas.add(new NovaSugestao(f.unidadeId(), hoje, Gatilho.EMERGENCIAL, f.equipeId(), f.osId(), chave,
					"OS emergencial nº " + f.osNumero() + " aberta fora das rotas do dia.", null));
		}
		for (var f : sugestoes.paralisadas(hoje, agora.minus(paralisacaoLonga))) {
			var chave = "PARALISACAO:" + f.chave();
			vigentes.add(chave);
			var minutos = f.momento() == null ? paralisacaoLonga.toMinutes()
					: Duration.between(f.momento(), agora).toMinutes();
			novas.add(new NovaSugestao(
					f.unidadeId(), hoje, Gatilho.PARALISACAO_LONGA, f.equipeId(), f.osId(), chave, "OS nº "
							+ f.osNumero() + " paralisada há " + minutos + " min" + motivo(f) + " · " + equipe(f) + ".",
					null));
		}
		for (var f : sugestoes.devolvidas(hoje, inicioDoDia)) {
			var chave = "DEVOLUCAO:" + f.chave();
			vigentes.add(chave);
			novas.add(new NovaSugestao(f.unidadeId(), hoje, Gatilho.DEVOLUCAO, f.equipeId(), f.osId(), chave,
					"OS nº " + f.osNumero() + " devolvida" + motivo(f) + " · " + equipe(f) + ".", null));
		}

		var emJornada = operacao.equipesEmJornada();
		var equipes = new HashMap<UUID, EquipeResumo>();
		sugestoes.equipes(emJornada.keySet()).forEach(e -> equipes.put(e.id(), e));
		for (var rota : operacao.rotasPublicadas(emJornada.keySet(), hoje)) {
			var equipe = equipes.get(rota.equipeId());
			if (equipe == null) {
				continue;
			}
			int atraso = EstimativaDeAtraso.minutos(rota.paradas(), emJornada.get(rota.equipeId()), agora);
			var chave = "ATRASO:" + rota.rotaId();
			// Histerese: a sugestão aberta continua valendo até o atraso cair abaixo da
			// metade
			// do limite (não some e volta a cada oscilação).
			if (atraso >= atrasoMin / 2) {
				vigentes.add(chave);
			}
			if (atraso >= atrasoMin) {
				novas.add(new NovaSugestao(equipe.unidadeId(), hoje, Gatilho.ATRASO, equipe.id(), null, chave,
						equipe.nome() + " com atraso estimado de " + atraso + " min em relação à rota publicada.",
						atraso));
			}
		}

		int expiradas = sugestoes.expirar(hoje, vigentes);
		int abertas = 0;
		for (var nova : novas) {
			if (sugestoes.abrir(tenant, nova)) {
				abertas++;
			}
		}
		return new Passada(abertas, expiradas);
	}

	private static String motivo(Fato f) {
		return f.motivo() == null ? "" : " (" + f.motivo() + ")";
	}

	private static String equipe(Fato f) {
		@Nullable String nome = f.equipeNome();
		return nome == null ? "equipe" : nome;
	}

}
