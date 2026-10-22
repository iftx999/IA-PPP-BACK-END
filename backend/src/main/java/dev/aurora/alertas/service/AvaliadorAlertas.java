package dev.aurora.alertas.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import dev.aurora.alertas.model.Condicao;
import dev.aurora.alertas.model.TipoAlerta;
import dev.aurora.alertas.repository.AlertaRepository;
import dev.aurora.auditoria.VerificacaoDaTrilha;
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
			TipoAlerta.FONTE_ATRASADA);

	private final AlertaRepository alertas;

	private final Metas metas;

	private final Fontes fontes;

	private final VerificacaoDaTrilha trilha;

	private final Clock relogio;

	AvaliadorAlertas(AlertaRepository alertas, Metas metas, Fontes fontes, VerificacaoDaTrilha trilha, Clock relogio) {
		this.alertas = alertas;
		this.metas = metas;
		this.fontes = fontes;
		this.trilha = trilha;
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
		condicoes.addAll(RegrasDeAlerta.deFontes(fontes.atrasadas(), relogio.getZone()));
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
