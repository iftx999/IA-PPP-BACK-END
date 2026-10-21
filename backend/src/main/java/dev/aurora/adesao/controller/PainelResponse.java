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

/**
 * Resposta de GET /api/v1/painel. Percentuais com 4 casas; a formatação (pt-BR, 1 casa) é
 * do front.
 */
record PainelResponse(UnidadeDto unidade, YearMonth competencia, List<YearMonth> competenciasDisponiveis,
		AdesaoDto adesao, long factiveis, long cortados, BigDecimal variacaoPp, Situacao situacao, MetaDto meta,
		ProjecaoDto projecao, List<PontoDto> serie, List<LinhaDto> unidades) {

	record UnidadeDto(UUID id, String nome, String tipo) {

		static UnidadeDto de(UnidadeResumo u) {
			return new UnidadeDto(u.id(), u.nome(), u.tipo());
		}
	}

	record AdesaoDto(BigDecimal percentual, long ligados, long comRede, long semLigacao) {

		static AdesaoDto de(Adesao a) {
			return new AdesaoDto(a.percentual(), a.ligados(), a.comRede(), a.semLigacao());
		}
	}

	/**
	 * {@code seguidaDe} preenchida quando a meta é herdada ("Segue Cidade A · 85,0%").
	 */
	record MetaDto(BigDecimal valor, BigDecimal alerta, LocalDate vigenciaInicio, LocalDate vigenciaFim, String origem,
			UnidadeDto seguidaDe) {

		static MetaDto de(MetaVigente m) {
			if (m == null) {
				return null;
			}
			var seguida = m.herdada() ? new UnidadeDto(m.unidadeId(), m.unidadeNome(), null) : null;
			return new MetaDto(m.valorMeta(), m.valorAlerta(), m.vigenciaInicio(), m.vigenciaFim(), m.origem(),
					seguida);
		}
	}

	record ProjecaoDto(BigDecimal ritmoAtual, int mesesNaBase, long faltam, Integer mesesRestantes,
			BigDecimal ritmoNecessario, BigDecimal projecaoNoPrazo, YearMonth prazo, YearMonth mesAtingimento,
			boolean atinge) {

		static ProjecaoDto de(Projecao p) {
			if (p == null) {
				return null;
			}
			return new ProjecaoDto(p.ritmoAtual(), p.mesesNaBase(), p.faltam(), p.mesesRestantes(), p.ritmoNecessario(),
					p.projecaoNoPrazo(), p.prazo(), p.mesAtingimento(), p.atinge());
		}
	}

	record PontoDto(YearMonth competencia, BigDecimal percentual, long ligados, long comRede) {

		static PontoDto de(PontoSerie p) {
			return new PontoDto(p.competencia(), p.adesao().percentual(), p.ligados(), p.comRede());
		}
	}

	record LinhaDto(UnidadeDto unidade, AdesaoDto adesao, BigDecimal variacaoPp, Situacao situacao, MetaDto meta) {

		static LinhaDto de(LinhaUnidade l) {
			return new LinhaDto(UnidadeDto.de(l.unidade()), AdesaoDto.de(l.adesao()), l.variacaoPp(), l.situacao(),
					MetaDto.de(l.meta()));
		}
	}

	static PainelResponse de(Painel p) {
		return new PainelResponse(UnidadeDto.de(p.unidade()), p.competencia(), p.competenciasDisponiveis(),
				AdesaoDto.de(p.adesao()), p.composicao().factiveis(), p.composicao().cortados(), p.variacaoPp(),
				p.situacao(), MetaDto.de(p.meta()), ProjecaoDto.de(p.projecao()),
				p.serie().stream().map(PontoDto::de).toList(), p.filhas().stream().map(LinhaDto::de).toList());
	}

}
