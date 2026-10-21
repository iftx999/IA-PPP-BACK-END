package dev.aurora.metas.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.Situacao;
import dev.aurora.metas.model.LinhaMeta;
import dev.aurora.metas.model.MetaDaUnidade;
import dev.aurora.metas.model.MetaHerdada;
import dev.aurora.metas.service.MetaService;
import org.jspecify.annotations.Nullable;

/** Árvore de metas (GET /api/v1/metas). */
record MetasResponse(LocalDate referencia, ResumoMetasDto resumo, List<LinhaMetaDto> linhas) {

	record ResumoMetasDto(long cadastradas, long doContrato, long seguemAcima) {
	}

	record MetaPropriaDto(UUID id, BigDecimal valor, @Nullable BigDecimal alerta, LocalDate vigenciaInicio,
			@Nullable LocalDate vigenciaFim, @Nullable String origem, boolean doContrato) {

		static @Nullable MetaPropriaDto de(@Nullable MetaDaUnidade m) {
			return m == null ? null : new MetaPropriaDto(m.id(), m.valor(), m.alerta(), m.vigenciaInicio(),
					m.vigenciaFim(), m.origem(), m.doContrato());
		}
	}

	record MetaHerdadaDto(UUID unidadeId, String unidadeNome, BigDecimal valor, @Nullable BigDecimal alerta) {

		static @Nullable MetaHerdadaDto de(@Nullable MetaHerdada m) {
			return m == null ? null : new MetaHerdadaDto(m.unidadeId(), m.unidadeNome(), m.valor(), m.alerta());
		}
	}

	record LinhaMetaDto(UUID unidadeId, String unidadeNome, String tipo, @Nullable UUID unidadePaiId, int nivel,
			@Nullable MetaPropriaDto propria, @Nullable MetaHerdadaDto herdada, @Nullable BigDecimal adesao,
			@Nullable YearMonth competenciaAdesao, Situacao situacao) {

		static LinhaMetaDto de(LinhaMeta l) {
			return new LinhaMetaDto(l.unidadeId(), l.unidadeNome(), l.tipo(), l.unidadePaiId(), l.nivel(),
					MetaPropriaDto.de(l.propria()), MetaHerdadaDto.de(l.herdada()), l.adesao(), l.competenciaAdesao(),
					l.situacao());
		}
	}

	static MetasResponse de(MetaService.Arvore a) {
		var r = a.resumo();
		return new MetasResponse(a.referencia(), new ResumoMetasDto(r.cadastradas(), r.doContrato(), r.seguemAcima()),
				a.linhas().stream().map(LinhaMetaDto::de).toList());
	}

}
