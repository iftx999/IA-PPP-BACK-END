package dev.aurora.auditoria.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.PrazoDeRetencao;
import dev.aurora.auditoria.RetencaoDaTrilha;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.auditoria.model.Expurgo;
import dev.aurora.auditoria.repository.RetencaoRepository;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import org.jspecify.annotations.Nullable;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prazo de retenção da trilha por tenant e expurgo do que passou dele (doc 08, 8.7; ADR
 * 0029). O mínimo e o máximo também estão no banco (CHECK), e só a função do banco apaga
 * eventos.
 */
@Service
public class RetencaoAuditoriaService implements RetencaoDaTrilha {

	public static final int MINIMO_MESES = 12;

	public static final int MAXIMO_MESES = 240;

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	/** Partições criadas com antecedência: o ano atual e os próximos. */
	private static final int ANOS_A_FRENTE = 2;

	private final RetencaoRepository retencao;

	private final Auditoria auditoria;

	private final Clock relogio;

	RetencaoAuditoriaService(RetencaoRepository retencao, Auditoria auditoria, Clock relogio) {
		this.retencao = retencao;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	@Override
	@Transactional(readOnly = true)
	public PrazoDeRetencao consultar() {
		exigirTenantInteiro();
		var ultimo = retencao.ultimoExpurgo();
		return new PrazoDeRetencao(retencao.meses(), MINIMO_MESES, MAXIMO_MESES,
				ultimo.map(Expurgo::executadoEm).orElse(null), ultimo.map(Expurgo::limite).orElse(null),
				retencao.totalExpurgado());
	}

	@Override
	@Transactional
	public PrazoDeRetencao alterar(int meses) {
		exigirTenantInteiro();
		if (meses < MINIMO_MESES || meses > MAXIMO_MESES) {
			throw new RegraVioladaException(
					"O prazo de retenção deve ficar entre " + MINIMO_MESES + " e " + MAXIMO_MESES + " meses.");
		}
		var atual = retencao.meses();
		if (atual != meses) {
			retencao.alterarMeses(meses);
			auditoria.registrar(TipoEvento.RETENCAO_AUDITORIA_ALTERADA, "auditoria/retencao",
					Map.of("de", atual, "para", meses));
		}
		return consultar();
	}

	@Override
	@Transactional
	public long expurgarVencidos() {
		var apagados = retencao.expurgarVencidos();
		if (apagados > 0) {
			var expurgo = retencao.ultimoExpurgo().orElseThrow();
			var detalhes = new LinkedHashMap<String, Object>();
			detalhes.put("quantidade", apagados);
			detalhes.put("ateSequencia", expurgo.ateSequencia());
			detalhes.put("anterioresA", expurgo.limite().toString());
			detalhes.put("prazoMeses", retencao.meses());
			auditoria.registrar(TipoEvento.TRILHA_EXPURGADA, "auditoria/retencao", detalhes);
		}
		return apagados;
	}

	@Override
	@Transactional
	public int prepararParticoes() {
		var ano = relogio.instant().atZone(FUSO).getYear();
		int criadas = 0;
		for (int i = 0; i <= ANOS_A_FRENTE; i++) {
			if (retencao.garantirParticao(ano + i)) {
				criadas++;
			}
		}
		return criadas;
	}

	private static void exigirTenantInteiro() {
		if (!ContextoAcesso.exigir().abrangeTenantInteiro()) {
			throw new AccessDeniedException("A trilha de auditoria exige a permissão no tenant inteiro.");
		}
	}

}
