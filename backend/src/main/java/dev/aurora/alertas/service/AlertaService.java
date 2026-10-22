package dev.aurora.alertas.service;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.alertas.model.Alerta;
import dev.aurora.alertas.model.EstadoAlerta;
import dev.aurora.alertas.repository.AlertaRepository;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.integracoes.FonteAtrasada;
import dev.aurora.integracoes.Fontes;
import dev.aurora.metas.Metas;
import dev.aurora.metas.SituacaoDaUnidade;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alertas como a pessoa os vê (F3): lista, contador e reconhecimento. */
@Service
public class AlertaService {

	private final AlertaRepository alertas;

	private final Metas metas;

	private final Fontes fontes;

	private final AvaliadorAlertas avaliador;

	private final ContextosDoSistema contextos;

	private final Auditoria auditoria;

	AlertaService(AlertaRepository alertas, Metas metas, Fontes fontes, AvaliadorAlertas avaliador,
			ContextosDoSistema contextos, Auditoria auditoria) {
		this.alertas = alertas;
		this.metas = metas;
		this.fontes = fontes;
		this.avaliador = avaliador;
		this.contextos = contextos;
		this.auditoria = auditoria;
	}

	/**
	 * Para o sino e o rodapé do menu (RN-08): alertas que pedem ação, competência mais
	 * recente dos dados e fontes atrasadas.
	 */
	public record Resumo(long criticos, long atencao, long reconhecidos, @Nullable YearMonth competencia,
			List<FonteAtrasada> fontesAtrasadas) {
	}

	@Transactional(readOnly = true)
	public List<Alerta> listar(EstadoAlerta estado, int limite) {
		return alertas.listar(estado, Math.clamp(limite, 1, 500));
	}

	@Transactional(readOnly = true)
	public Resumo resumo() {
		var contagem = alertas.contar();
		var competencia = metas.situacoes()
			.stream()
			.map(SituacaoDaUnidade::competencia)
			.filter(Objects::nonNull)
			.max(Comparator.naturalOrder())
			.orElse(null);
		return new Resumo(contagem.criticos(), contagem.atencao(), contagem.reconhecidos(), competencia,
				fontes.atrasadas());
	}

	@Transactional
	public void reconhecer(UUID id) {
		var alerta = alertas.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (!alertas.reconhecer(id, ContextoAcesso.exigir().usuarioId())) {
			throw new ConflitoException(
					alerta.resolvidoEm() != null ? "O alerta já foi resolvido." : "O alerta já foi reconhecido.");
		}
		auditoria.registrar(TipoEvento.ALERTA_RECONHECIDO, "alerta/" + id,
				Map.of("tipo", alerta.tipo().name(), "chave", alerta.chave()));
	}

	/**
	 * Reavalia agora, sem esperar a próxima rodada (ex.: depois de corrigir uma meta).
	 */
	public AvaliadorAlertas.Resultado avaliarAgora() throws Exception {
		var sistema = contextos.doTenant(ContextoAcesso.exigir().tenantId())
			.orElseThrow(RecursoNaoEncontradoException::new);
		return ContextoAcesso.executar(sistema, avaliador::avaliarIndicadores);
	}

}
