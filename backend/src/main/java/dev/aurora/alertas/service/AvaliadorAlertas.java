package dev.aurora.alertas.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import dev.aurora.adesao.Medicoes;
import dev.aurora.alertas.model.Condicao;
import dev.aurora.alertas.model.TipoAlerta;
import dev.aurora.alertas.repository.AlertaRepository;
import dev.aurora.auditoria.VerificacaoDaTrilha;
import dev.aurora.campanhas.Campanhas;
import dev.aurora.contratos.PrazosContratuais;
import dev.aurora.integracoes.Fontes;
import dev.aurora.metas.Metas;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincroniza as condições atuais com os alertas abertos (RN-07): abre o que surgiu,
 * atualiza texto e severidade do que mudou e resolve sozinho o que deixou de valer. Roda
 * no contexto do sistema de um tenant (todas as unidades).
 */
@Service
public class AvaliadorAlertas {

	static final Set<TipoAlerta> DE_INDICADORES = EnumSet.of(TipoAlerta.INDICADOR_ABAIXO_DA_META,
			TipoAlerta.META_FORA_DO_RITMO, TipoAlerta.FONTE_ATRASADA, TipoAlerta.PRAZO_CONTRATUAL,
			TipoAlerta.ORDEM_DIVERGENTE);

	private final AlertaRepository alertas;

	private final Metas metas;

	private final Fontes fontes;

	private final Medicoes medicoes;

	private final PrazosContratuais prazos;

	private final VerificacaoDaTrilha trilha;

	private final Campanhas campanhas;

	private final Clock relogio;

	AvaliadorAlertas(AlertaRepository alertas, Metas metas, Fontes fontes, Medicoes medicoes, PrazosContratuais prazos,
			VerificacaoDaTrilha trilha, Campanhas campanhas, Clock relogio) {
		this.alertas = alertas;
		this.metas = metas;
		this.fontes = fontes;
		this.medicoes = medicoes;
		this.prazos = prazos;
		this.trilha = trilha;
		this.campanhas = campanhas;
		this.relogio = relogio;
	}

	public record Resultado(int abertos, int atualizados, int resolvidos) {

		@Override
		public String toString() {
			return abertos + " abertos, " + atualizados + " atualizados, " + resolvidos + " resolvidos";
		}
	}

	@Transactional
	public Resultado avaliarIndicadores() {
		var condicoes = new ArrayList<Condicao>();
		condicoes.addAll(RegrasDeAlerta.deAdesao(metas.situacoes()));
		condicoes.addAll(RegrasDeAlerta.deRitmo(medicoes.projecoes()));
		condicoes.addAll(RegrasDeAlerta.deFontes(fontes.atrasadas(), relogio.getZone()));
		var hoje = LocalDate.now(relogio);
		condicoes.addAll(
				RegrasDeAlerta.dePrazos(prazos.pendentes(hoje, hoje.plusDays(RegrasDeAlerta.ANTECEDENCIA_PRAZO_DIAS),
						hoje.plusDays(RegrasDeAlerta.ANTECEDENCIA_CONTRATO_DIAS)), hoje));
		condicoes.addAll(RegrasDeAlerta.deOrdensDivergentes(campanhas.comOrdensDivergentes()));
		return sincronizar(DE_INDICADORES, condicoes);
	}

	/** Verificação periódica da cadeia de auditoria; alerta crítico se houver quebra. */
	@Transactional
	public Resultado verificarTrilha() {
		var quebra = trilha.primeiraQuebra();
		return sincronizar(EnumSet.of(TipoAlerta.TRILHA_ALTERADA),
				quebra == null ? List.of() : List.of(RegrasDeAlerta.trilhaAlterada(quebra)));
	}

	private Resultado sincronizar(Set<TipoAlerta> tipos, List<Condicao> condicoes) {
		var abertos = new HashMap<String, dev.aurora.alertas.model.Alerta>();
		alertas.abertosDosTipos(tipos).forEach(a -> abertos.put(a.chave(), a));
		int novos = 0;
		int atualizados = 0;
		for (var condicao : condicoes) {
			var atual = abertos.remove(condicao.chave());
			if (atual == null) {
				novos += alertas.abrir(condicao) ? 1 : 0;
			}
			else if (!atual.mesmoConteudo(condicao)) {
				alertas.atualizar(atual.id(), condicao);
				atualizados++;
			}
		}
		alertas.resolver(abertos.values().stream().map(dev.aurora.alertas.model.Alerta::id).toList());
		return new Resultado(novos, atualizados, abertos.size());
	}

}
