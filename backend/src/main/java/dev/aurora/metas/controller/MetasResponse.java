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
record MetasResponse(LocalDate referencia, ResumoDto resumo, List<LinhaDto> linhas) {

	record ResumoDto(long cadastradas, long doContrato, long seguemAcima) {
	}

	record PropriaDto(UUID id, BigDecimal valor, @Nullable BigDecimal alerta, LocalDate vigenciaInicio,
			@Nullable LocalDate vigenciaFim, @Nullable String origem, boolean doContrato) {

		static @Nullable PropriaDto de(@Nullable MetaDaUnidade m) {
			return m == null ? null : new PropriaDto(m.id(), m.valor(), m.alerta(), m.vigenciaInicio(), m.vigenciaFim(),
					m.origem(), m.doContrato());
		}
	}

	record HerdadaDto(UUID unidadeId, String unidadeNome, BigDecimal valor, @Nullable BigDecimal alerta) {

		static @Nullable HerdadaDto de(@Nullable MetaHerdada m) {
			return m == null ? null : new HerdadaDto(m.unidadeId(), m.unidadeNome(), m.valor(), m.alerta());
		}
	}

	record LinhaDto(UUID unidadeId, String unidadeNome, String tipo, @Nullable UUID unidadePaiId, int nivel,
			@Nullable PropriaDto propria, @Nullable HerdadaDto herdada, @Nullable BigDecimal adesao,
			@Nullable YearMonth competenciaAdesao, Situacao situacao) {

		static LinhaDto de(LinhaMeta l) {
			return new LinhaDto(l.unidadeId(), l.unidadeNome(), l.tipo(), l.unidadePaiId(), l.nivel(),
					PropriaDto.de(l.propria()), HerdadaDto.de(l.herdada()), l.adesao(), l.competenciaAdesao(),
					l.situacao());
		}
	}

	static MetasResponse de(MetaService.Arvore a) {
		var r = a.resumo();
		return new MetasResponse(a.referencia(), new ResumoDto(r.cadastradas(), r.doContrato(), r.seguemAcima()),
				a.linhas().stream().map(LinhaDto::de).toList());
	}

}
