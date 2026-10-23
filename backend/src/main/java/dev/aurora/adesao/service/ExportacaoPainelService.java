package dev.aurora.adesao.service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.adesao.Situacao;
import dev.aurora.adesao.model.Adesao;
import dev.aurora.adesao.model.MetaVigente;
import dev.aurora.adesao.model.Painel;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.EscritorCsv;
import dev.aurora.comum.RelatorioPdf;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B6: exporta o painel da unidade (a própria unidade e as de baixo) em CSV ou PDF, com os
 * mesmos números da tela. A emissão vai para a auditoria com linhas e SHA-256 (RN-12).
 * Números no formato brasileiro, para o Excel em português.
 */
@Service
public class ExportacaoPainelService {

	public enum Formato {

		CSV, PDF

	}

	public record Arquivo(String nome, long linhas) {
	}

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMM/yyyy", PT_BR);

	private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR);

	private static final Map<Situacao, String> SITUACOES = Map.of(Situacao.META_ATINGIDA, "Meta atingida",
			Situacao.ATENCAO, "Atenção", Situacao.ABAIXO_DO_ALERTA, "Abaixo do alerta", Situacao.SEM_META, "Sem meta");

	private final PainelService painel;

	private final Auditoria auditoria;

	private final Clock relogio;

	ExportacaoPainelService(PainelService painel, Auditoria auditoria, Clock relogio) {
		this.painel = painel;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	@Transactional
	public Arquivo exportar(Optional<UUID> unidade, Optional<YearMonth> competencia, Formato formato,
			OutputStream destino) {
		var p = painel.montar(unidade, competencia);
		var resumo = sha256();
		var saida = new DigestOutputStream(destino, resumo);
		long linhas = formato == Formato.CSV ? csv(p, saida) : pdf(p, saida);

		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("formato", formato.name());
		detalhes.put("unidade", p.unidade().id().toString());
		detalhes.put("competencia", p.competencia().toString());
		detalhes.put("linhas", linhas);
		detalhes.put("sha256", HexFormat.of().formatHex(resumo.digest()));
		auditoria.registrar(TipoEvento.RELATORIO_EXPORTADO, "painel", detalhes);
		var nome = "painel-" + slug(p.unidade().nome()) + "-" + p.competencia()
				+ (formato == Formato.CSV ? ".csv" : ".pdf");
		return new Arquivo(nome, linhas);
	}

	private long csv(Painel p, OutputStream saida) {
		try (var csv = new EscritorCsv(saida)) {
			csv.linha("unidade", "tipo", "competencia", "adesao_pct", "ligados", "com_rede", "sem_ligacao",
					"variacao_pp", "meta_pct", "alerta_pct", "situacao", "meta_herdada_de", "origem_meta");
			csv.linha(linhaCsv(p.unidade().nome(), p.unidade().tipo(), p.competencia(), p.adesao(), p.variacaoPp(),
					p.meta(), p.situacao()));
			for (var filha : p.filhas()) {
				csv.linha(linhaCsv(filha.unidade().nome(), filha.unidade().tipo(), p.competencia(), filha.adesao(),
						filha.variacaoPp(), filha.meta(), filha.situacao()));
			}
		}
		return 1L + p.filhas().size();
	}

	private static Object[] linhaCsv(String nome, String tipo, YearMonth competencia, Adesao adesao,
			@Nullable BigDecimal variacao, @Nullable MetaVigente meta, Situacao situacao) {
		return new Object[] { nome, tipo, competencia, decimal(adesao.percentual()), adesao.ligados(), adesao.comRede(),
				adesao.semLigacao(), decimal(variacao), meta == null ? null : decimal(meta.valorMeta()),
				meta == null ? null : decimal(meta.valorAlerta()), SITUACOES.get(situacao),
				meta != null && meta.herdada() ? meta.unidadeNome() : null, meta == null ? null : meta.origem() };
	}

	private long pdf(Painel p, OutputStream saida) {
		try (var pdf = new RelatorioPdf("Aurora - Painel de adesão")) {
			pdf.titulo("Painel de adesão · " + p.unidade().nome())
				.linha("Dados de " + mes(p.competencia()) + " · Emitido em "
						+ DATA_HORA.format(ZonedDateTime.now(relogio)), false, 9)
				.espaco(8)
				.linha("Adesão: " + percentual(p.adesao().percentual()) + " (" + inteiro(p.adesao().ligados())
						+ " ligados de " + inteiro(p.adesao().comRede()) + " imóveis com rede) · "
						+ SITUACOES.get(p.situacao()), true, 12);
			if (p.variacaoPp() != null) {
				pdf.linha("Variação sobre o mês anterior: " + sinal(p.variacaoPp()) + " p.p.", false, 10);
			}
			if (p.meta() != null) {
				var m = p.meta();
				pdf.linha("Meta: " + percentual(m.valorMeta()) + " · alerta em " + percentual(m.valorAlerta())
						+ (m.vigenciaFim() == null ? " · sem fim" : " · até " + m.vigenciaFim())
						+ (m.herdada() ? " · segue " + m.unidadeNome() : "")
						+ (m.origem() == null ? " · meta manual" : " · " + m.origem()), false, 10);
			}
			if (p.projecao() != null) {
				var pr = p.projecao();
				pdf.linha("Faltam " + inteiro(pr.faltam()) + " ligações"
						+ (pr.ritmoAtual() == null ? "" : " · ritmo atual " + decimal(pr.ritmoAtual()) + "/mês")
						+ (pr.ritmoNecessario() == null ? ""
								: " · necessário " + decimal(pr.ritmoNecessario()) + "/mês")
						+ (pr.projecaoNoPrazo() == null ? ""
								: " · projeção no prazo " + percentual(pr.projecaoNoPrazo()))
						+ (pr.atinge() ? " · no ritmo" : " · fora do ritmo"), false, 10);
			}
			pdf.linha("Sem ligação: " + inteiro(p.composicao().factiveis()) + " factíveis e "
					+ inteiro(p.composicao().cortados()) + " cortados", false, 10);

			if (!p.filhas().isEmpty()) {
				var linhas = new ArrayList<List<String>>();
				for (var f : p.filhas()) {
					linhas.add(List.of(f.unidade().nome(), percentual(f.adesao().percentual()),
							inteiro(f.adesao().ligados()), inteiro(f.adesao().comRede()),
							f.variacaoPp() == null ? "—" : sinal(f.variacaoPp()),
							f.meta() == null ? "—" : percentual(f.meta().valorMeta()), SITUACOES.get(f.situacao())));
				}
				pdf.subtitulo("Unidades abaixo")
					.tabela(new float[] { 230, 80, 80, 80, 80, 80, 140 },
							List.of("Unidade", "Adesão", "Ligados", "Com rede", "Variação (p.p.)", "Meta", "Situação"),
							linhas);
			}
			if (!p.serie().isEmpty()) {
				pdf.subtitulo("Série dos últimos meses")
					.tabela(new float[] { 120, 100, 100, 100 }, List.of("Mês", "Adesão", "Ligados", "Com rede"),
							p.serie()
								.stream()
								.map(s -> List.of(mes(s.competencia()), percentual(s.adesao().percentual()),
										inteiro(s.ligados()), inteiro(s.comRede())))
								.toList());
			}
			pdf.salvar(saida);
		}
		return 1L + p.filhas().size() + p.serie().size();
	}

	private static @Nullable String decimal(@Nullable BigDecimal valor) {
		if (valor == null) {
			return null;
		}
		var formato = NumberFormat.getNumberInstance(PT_BR);
		formato.setGroupingUsed(false);
		formato.setMinimumFractionDigits(2);
		formato.setMaximumFractionDigits(2);
		return formato.format(valor);
	}

	private static String percentual(@Nullable BigDecimal valor) {
		return valor == null ? "—" : decimal(valor) + "%";
	}

	private static String sinal(BigDecimal valor) {
		return (valor.signum() > 0 ? "+" : "") + decimal(valor);
	}

	private static String inteiro(long valor) {
		return NumberFormat.getIntegerInstance(PT_BR).format(valor);
	}

	private static String mes(YearMonth competencia) {
		return MES.format(competencia).replace(".", "");
	}

	static String slug(String nome) {
		var semAcento = java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		var slug = semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		return slug.isEmpty() ? "unidade" : slug.substring(0, Math.min(40, slug.length()));
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException erro) {
			throw new IllegalStateException(erro);
		}
	}

}
