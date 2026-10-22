package dev.aurora.alertas.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.aurora.adesao.Situacao;
import dev.aurora.alertas.model.Condicao;
import dev.aurora.alertas.model.Severidade;
import dev.aurora.alertas.model.TipoAlerta;
import dev.aurora.integracoes.FonteAtrasada;
import dev.aurora.metas.SituacaoDaUnidade;
import org.jspecify.annotations.Nullable;

/** RN-07: quais condições pedem alerta agora e com que texto. */
final class RegrasDeAlerta {

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private static final Set<String> OPERACIONAIS = Set.of("ETE", "EEE");

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private RegrasDeAlerta() {
	}

	/** Abaixo do limite de alerta → Crítico; abaixo da meta → Atenção (RN-03). */
	static List<Condicao> deAdesao(List<SituacaoDaUnidade> situacoes) {
		var condicoes = new ArrayList<Condicao>();
		for (var s : situacoes) {
			if (OPERACIONAIS.contains(s.tipo()) || s.adesao() == null || s.meta() == null) {
				continue;
			}
			var critico = s.situacao() == Situacao.ABAIXO_DO_ALERTA;
			if (!critico && s.situacao() != Situacao.ATENCAO) {
				continue;
			}
			var titulo = (critico ? "Adesão abaixo do limite de alerta em " : "Adesão abaixo da meta em ")
					+ s.unidadeNome();
			var descricao = new StringBuilder("Adesão de ").append(percentual(s.adesao()))
				.append(s.competencia() == null ? "" : " em " + competencia(s.competencia()))
				.append("; meta de ")
				.append(percentual(s.meta()));
			if (s.alerta() != null) {
				descricao.append(" (alerta abaixo de ").append(percentual(s.alerta())).append(')');
			}
			descricao.append('.');
			if (!s.metaPropria()) {
				descricao.append(" Meta herdada da unidade acima.");
			}
			condicoes.add(new Condicao("ADESAO:" + s.unidadeId(), TipoAlerta.INDICADOR_ABAIXO_DA_META,
					critico ? Severidade.CRITICO : Severidade.ATENCAO, s.unidadeId(), titulo, descricao.toString(),
					"/painel?unidade=" + s.unidadeId()));
		}
		return condicoes;
	}

	/** RN-08: fonte sem carga dentro do atraso máximo. */
	static List<Condicao> deFontes(List<FonteAtrasada> atrasadas, ZoneId fuso) {
		return atrasadas.stream()
			.map(f -> new Condicao("FONTE:" + f.id(), TipoAlerta.FONTE_ATRASADA, Severidade.ATENCAO, null,
					"Fonte de dados atrasada: " + f.nome(),
					(f.ultimaCarga() == null ? "Nenhuma carga concluída ainda"
							: "Última carga concluída em " + DATA.format(f.ultimaCarga().atZone(fuso)))
							+ " (atraso máximo: " + duracao(f.atrasoMaximo())
							+ "). Os indicadores que dependem dela podem estar desatualizados.",
					"/admin/integracoes"))
			.toList();
	}

	static Condicao trilhaAlterada(long primeiraQuebra) {
		return new Condicao("TRILHA", TipoAlerta.TRILHA_ALTERADA, Severidade.CRITICO, null,
				"Trilha de auditoria alterada", "A verificação periódica encontrou o evento nº " + primeiraQuebra
						+ " alterado ou fora de ordem. Acione a segurança da informação.",
				"/admin/auditoria");
	}

	static String percentual(BigDecimal valor) {
		var formato = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(PT_BR));
		return formato.format(valor) + "%";
	}

	static String competencia(java.time.YearMonth competencia) {
		return competencia.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "") + "/"
				+ competencia.getYear();
	}

	static String duracao(Duration duracao) {
		var horas = duracao.toHours();
		if (horas % 24 == 0) {
			var dias = horas / 24;
			return dias + (dias == 1 ? " dia" : " dias");
		}
		return horas + (horas == 1 ? " hora" : " horas");
	}

}
