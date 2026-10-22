package dev.aurora.auditoria.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.auditoria.VerificacaoDaTrilha;
import dev.aurora.auditoria.model.ResultadoVerificacao;
import dev.aurora.auditoria.repository.TrilhaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditoriaService implements Auditoria, VerificacaoDaTrilha {

	private final TrilhaRepository trilha;

	private final AssinaturaEvento assinatura;

	private final JsonMapper json;

	AuditoriaService(TrilhaRepository trilha, AssinaturaEvento assinatura, JsonMapper json) {
		this.trilha = trilha;
		this.assinatura = assinatura;
		this.json = json;
	}

	@Override
	@Transactional
	public void registrar(TipoEvento tipo, String recurso, Map<String, ?> detalhes) {
		var contexto = ContextoAcesso.exigir();
		var elo = trilha.travarUltimoElo(contexto.tenantId());
		var sequencia = elo.sequencia() + 1;
		var ocorridoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
		var ip = trilha.normalizarIp(ipDaRequisicao());
		var hash = assinatura.assinar(elo.hash(), contexto.tenantId(), sequencia, ocorridoEm, contexto.usuarioId(),
				tipo.name(), recurso, detalhes, ip);
		trilha.inserir(UUID.randomUUID(), contexto.tenantId(), sequencia, ocorridoEm, contexto.usuarioId(), tipo.name(),
				recurso, json.writeValueAsString(detalhes), ip, elo.hash(), hash, AssinaturaEvento.FORMATO);
	}

	/**
	 * Confere a trilha do tenant atual: cada evento precisa apontar para o hash do
	 * anterior e, nos eventos do formato atual, o HMAC recalculado precisa bater. Eventos
	 * legados só têm o elo conferido.
	 */
	@Transactional(readOnly = true)
	public ResultadoVerificacao verificar() {
		var estado = new Object() {
			long eventos;

			long verificados;

			long legados;

			byte @Nullable [] hashAnterior;

			@Nullable Long quebra;

		};
		trilha.percorrer(evento -> {
			estado.eventos++;
			if (estado.quebra != null) {
				return;
			}
			var eloOk = estado.hashAnterior == null || Arrays.equals(estado.hashAnterior, evento.hashAnterior());
			boolean hashOk = true;
			if (evento.formatoHash() == null) {
				estado.legados++;
			}
			else {
				Map<String, Object> detalhes = json.readValue(evento.detalhes(), new TypeReference<>() {
				});
				var esperado = assinatura.assinar(evento.hashAnterior(), evento.tenantId(), evento.sequencia(),
						evento.ocorridoEm(), evento.usuarioId(), evento.tipo(), evento.recurso(), detalhes,
						evento.ipOrigem());
				hashOk = Arrays.equals(esperado, evento.hash());
				estado.verificados++;
			}
			if (!eloOk || !hashOk) {
				estado.quebra = evento.sequencia();
			}
			estado.hashAnterior = evento.hash();
		});
		return new ResultadoVerificacao(estado.eventos, estado.verificados, estado.legados, estado.quebra);
	}

	@Override
	@Transactional(readOnly = true)
	public @Nullable Long primeiraQuebra() {
		return verificar().primeiraQuebra();
	}

	private static @Nullable String ipDaRequisicao() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
			HttpServletRequest requisicao = atributos.getRequest();
			return requisicao.getRemoteAddr();
		}
		return null;
	}

}
