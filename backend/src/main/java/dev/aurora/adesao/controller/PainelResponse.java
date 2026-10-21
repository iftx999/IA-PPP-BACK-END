package dev.aurora.adesao.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.model.Adesao;
import dev.aurora.adesao.model.LinhaUnidade;
import dev.aurora.adesao.model.MetaVigente;
import dev.aurora.adesao.model.Painel;
import dev.aurora.adesao.model.PontoSerie;
import dev.aurora.adesao.model.Projecao;
import dev.aurora.adesao.model.Situacao;
import dev.aurora.adesao.model.UnidadeResumo;
import org.jspecify.annotations.Nullable;

/**
 * Resposta de GET /api/v1/painel. Percentuais com 4 casas; a formatação (pt-BR, 1 casa) é
 * do front. Campos {@link Nullable} saem como {@code tipo | null} na OpenAPI.
 */
record PainelResponse(UnidadeDto unidade, @Nullable UnidadeDto acima, YearMonth competencia,
		List<YearMonth> competenciasDisponiveis, AdesaoDto adesao, long factiveis, long cortados,
		@Nullable BigDecimal variacaoPp, Situacao situacao, @Nullable MetaDto meta, @Nullable ProjecaoDto projecao,
		List<PontoDto> serie, List<LinhaDto> unidades) {

	record UnidadeDto(UUID id, String nome, @Nullable String tipo) {

		static @Nullable UnidadeDto de(@Nullable UnidadeResumo u) {
			return u == null ? null : new UnidadeDto(u.id(), u.nome(), u.tipo());
		}
	}

	record AdesaoDto(@Nullable BigDecimal percentual, long ligados, long comRede, long semLigacao) {

		static AdesaoDto de(Adesao a) {
			return new AdesaoDto(a.percentual(), a.ligados(), a.comRede(), a.semLigacao());
		}
	}

	/**
	 * {@code seguidaDe} preenchida quando a meta é herdada ("Segue Cidade A · 85,0%");
	 * {@code origem} nula = meta manual.
	 */
	record MetaDto(BigDecimal valor, @Nullable BigDecimal alerta, LocalDate vigenciaInicio,
			@Nullable LocalDate vigenciaFim, @Nullable String origem, @Nullable UnidadeDto seguidaDe) {

		static @Nullable MetaDto de(@Nullable MetaVigente m) {
			if (m == null) {
				return null;
			}
			var seguida = m.herdada() ? new UnidadeDto(m.unidadeId(), m.unidadeNome(), null) : null;
			return new MetaDto(m.valorMeta(), m.valorAlerta(), m.vigenciaInicio(), m.vigenciaFim(), m.origem(),
					seguida);
		}
	}

	record ProjecaoDto(@Nullable BigDecimal ritmoAtual, int mesesNaBase, long faltam, @Nullable Integer mesesRestantes,
			@Nullable BigDecimal ritmoNecessario, @Nullable BigDecimal projecaoNoPrazo, @Nullable YearMonth prazo,
			@Nullable YearMonth mesAtingimento, boolean atinge) {

		static @Nullable ProjecaoDto de(@Nullable Projecao p) {
			if (p == null) {
				return null;
			}
			return new ProjecaoDto(p.ritmoAtual(), p.mesesNaBase(), p.faltam(), p.mesesRestantes(), p.ritmoNecessario(),
					p.projecaoNoPrazo(), p.prazo(), p.mesAtingimento(), p.atinge());
		}
	}

	record PontoDto(YearMonth competencia, @Nullable BigDecimal percentual, long ligados, long comRede) {

		static PontoDto de(PontoSerie p) {
			return new PontoDto(p.competencia(), p.adesao().percentual(), p.ligados(), p.comRede());
		}
	}

	record LinhaDto(UnidadeDto unidade, AdesaoDto adesao, @Nullable BigDecimal variacaoPp, Situacao situacao,
			@Nullable MetaDto meta) {

		static LinhaDto de(LinhaUnidade l) {
			return new LinhaDto(UnidadeDto.de(l.unidade()), AdesaoDto.de(l.adesao()), l.variacaoPp(), l.situacao(),
					MetaDto.de(l.meta()));
		}
	}

	static PainelResponse de(Painel p) {
		return new PainelResponse(UnidadeDto.de(p.unidade()), UnidadeDto.de(p.acima()), p.competencia(),
				p.competenciasDisponiveis(), AdesaoDto.de(p.adesao()), p.composicao().factiveis(),
				p.composicao().cortados(), p.variacaoPp(), p.situacao(), MetaDto.de(p.meta()),
				ProjecaoDto.de(p.projecao()), p.serie().stream().map(PontoDto::de).toList(),
				p.filhas().stream().map(LinhaDto::de).toList());
	}

}
