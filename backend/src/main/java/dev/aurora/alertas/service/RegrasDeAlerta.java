package dev.aurora.alertas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.aurora.adesao.ProjecaoDaUnidade;
import dev.aurora.adesao.Situacao;
import dev.aurora.alertas.model.Condicao;
import dev.aurora.alertas.model.Severidade;
import dev.aurora.alertas.model.TipoAlerta;
import dev.aurora.contratos.PrazoContratual;
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

	/** Item de prazo: alerta a partir de 60 dias antes; crítico a 15 dias ou vencido. */
	static final int ANTECEDENCIA_PRAZO_DIAS = 60;

	static final int PRAZO_CRITICO_DIAS = 15;

	/** Fim da vigência do contrato: alerta a partir de 180 dias; crítico a 30. */
	static final int ANTECEDENCIA_CONTRATO_DIAS = 180;

	static final int CONTRATO_CRITICO_DIAS = 30;

	/**
	 * RN-05: a meta com prazo não sai no ritmo atual. Crítico quando a projeção no prazo
	 * fica abaixo do limite de alerta. Unidades que já estão na meta, no mês do prazo ou
	 * operacionais ficam de fora (o alerta de adesão cobre o resto).
	 */
	static List<Condicao> deRitmo(List<ProjecaoDaUnidade> projecoes) {
		var condicoes = new ArrayList<Condicao>();
		for (var p : projecoes) {
			if (OPERACIONAIS.contains(p.tipo()) || p.atinge() || p.mesesRestantes() == 0) {
				continue;
			}
			var critico = p.alerta() != null && p.projecaoNoPrazo().compareTo(p.alerta()) < 0;
			var descricao = new StringBuilder();
			if (p.ritmoAtual() == null || p.ritmoAtual().signum() <= 0) {
				descricao.append("Sem novas ligações no período comparável; a adesão fica em ")
					.append(percentual(p.adesao()));
			}
			else {
				descricao.append("No ritmo atual (")
					.append(inteiro(p.ritmoAtual()))
					.append("/mês), a adesão chega a ")
					.append(percentual(p.projecaoNoPrazo()))
					.append(" em ")
					.append(competencia(p.prazo()));
			}
			descricao.append("; meta de ").append(percentual(p.meta())).append(" até ").append(competencia(p.prazo()));
			descricao.append(". Faltam ").append(inteiro(BigDecimal.valueOf(p.faltam()))).append(" ligações");
			if (p.ritmoNecessario() != null) {
				descricao.append(": ritmo necessário de ").append(inteiro(p.ritmoNecessario())).append("/mês");
			}
			descricao.append('.');
			if (p.mesAtingimento() != null) {
				descricao.append(" No ritmo atual, a meta sai em ").append(competencia(p.mesAtingimento())).append('.');
			}
			if (!p.metaPropria()) {
				descricao.append(" Meta herdada da unidade acima.");
			}
			condicoes.add(new Condicao("RITMO:" + p.unidadeId(), TipoAlerta.META_FORA_DO_RITMO,
					critico ? Severidade.CRITICO : Severidade.ATENCAO, p.unidadeId(),
					"Meta fora do ritmo em " + p.unidadeNome(), descricao.toString(),
					"/painel?unidade=" + p.unidadeId()));
		}
		return condicoes;
	}

	/**
	 * Prazos contratuais dentro da antecedência; item vencido continua em alerta até ser
	 * marcado como cumprido.
	 */
	static List<Condicao> dePrazos(List<PrazoContratual> prazos, LocalDate hoje) {
		var condicoes = new ArrayList<Condicao>();
		for (var p : prazos) {
			var dias = ChronoUnit.DAYS.between(hoje, p.data());
			var antecedencia = p.fimDoContrato() ? ANTECEDENCIA_CONTRATO_DIAS : ANTECEDENCIA_PRAZO_DIAS;
			if (dias > antecedencia || (p.fimDoContrato() && dias < 0)) {
				continue;
			}
			var critico = dias <= (p.fimDoContrato() ? CONTRATO_CRITICO_DIAS : PRAZO_CRITICO_DIAS);
			var quando = dias < 0 ? "venceu em " + DATA.format(p.data()) + " (há " + diasPorExtenso(-dias) + ")"
					: dias == 0 ? "vence hoje (" + DATA.format(p.data()) + ")"
							: "vence em " + DATA.format(p.data()) + " (faltam " + diasPorExtenso(dias) + ")";
			String titulo;
			String descricao;
			String chave;
			if (p.fimDoContrato()) {
				chave = "CONTRATO:" + p.contratoId();
				titulo = "Vigência do contrato " + p.contratoNumero() + " termina em breve";
				descricao = "O contrato " + p.contratoNumero() + " " + quando
						+ ". Verifique renovação, aditivo ou transição.";
			}
			else {
				chave = "PRAZO:" + p.itemId();
				titulo = (dias < 0 ? "Prazo contratual vencido: " : "Prazo contratual: ") + p.titulo();
				descricao = "Contrato " + p.contratoNumero()
						+ (p.clausula() == null ? "" : ", cláusula " + p.clausula()) + ": " + quando
						+ ". Quando for cumprido, marque o item no contrato.";
			}
			condicoes
				.add(new Condicao(chave, TipoAlerta.PRAZO_CONTRATUAL, critico ? Severidade.CRITICO : Severidade.ATENCAO,
						p.unidadeId(), titulo, descricao, "/contratos/" + p.contratoId()));
		}
		return condicoes;
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

	static String inteiro(BigDecimal valor) {
		return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(PT_BR))
			.format(valor.setScale(0, RoundingMode.HALF_UP));
	}

	private static String diasPorExtenso(long dias) {
		return dias + (dias == 1 ? " dia" : " dias");
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
