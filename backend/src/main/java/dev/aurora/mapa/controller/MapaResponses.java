package dev.aurora.mapa.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.Situacao;
import dev.aurora.mapa.model.Area;
import dev.aurora.mapa.model.Camadas;
import dev.aurora.mapa.model.ImovelNoMapa;
import dev.aurora.mapa.model.Marcacao;
import dev.aurora.mapa.model.Precisao;
import dev.aurora.mapa.model.SituacaoNoMapa;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Respostas da API do mapa. Geometrias em GeoJSON (SIRGAS 2000, lon/lat). */
final class MapaResponses {

	private MapaResponses() {
	}

	record AreaDto(double oeste, double sul, double leste, double norte) {
		static @Nullable AreaDto de(@Nullable Area a) {
			return a == null ? null : new AreaDto(a.oeste(), a.sul(), a.leste(), a.norte());
		}
	}

	record MunicipioDto(UUID unidadeId, String nome, String codigoIbge, @Nullable BigDecimal adesao, Situacao situacao,
			JsonNode geometria) {
	}

	record SetorDto(UUID unidadeId, String nome, String origem, JsonNode geometria) {
	}

	record OperacionalDto(UUID unidadeId, String tipo, String nome, double longitude, double latitude) {
	}

	/**
	 * Camadas leves do mapa; imóveis e rede vêm pelos tiles a partir do zoom indicado.
	 */
	record CamadasMapa(@Nullable YearMonth competencia, List<YearMonth> competencias, List<MunicipioDto> municipios,
			List<SetorDto> setores, List<OperacionalDto> operacionais, @Nullable AreaDto enquadramento, int zoomImoveis,
			int zoomRede) {

		static CamadasMapa de(Camadas c, int zoomImoveis, int zoomRede) {
			return new CamadasMapa(c.competencia(), c.competencias(),
					c.municipios()
						.stream()
						.map(m -> new MunicipioDto(m.unidadeId(), m.nome(), m.codigoIbge(), m.adesao(), m.situacao(),
								m.geometria()))
						.toList(),
					c.setores()
						.stream()
						.map(s -> new SetorDto(s.unidadeId(), s.nome(), s.origem(), s.geometria()))
						.toList(),
					c.operacionais()
						.stream()
						.map(o -> new OperacionalDto(o.unidadeId(), o.tipo(), o.nome(), o.longitude(), o.latitude()))
						.toList(),
					AreaDto.de(c.enquadramento()), zoomImoveis, zoomRede);
		}
	}

	/** Imóvel no ponto clicado: matrícula mascarada sem {@code dados_pessoais:ler}. */
	record ImovelNoPonto(String matricula, UUID unidadeId, String unidadeNome, SituacaoNoMapa situacao,
			Precisao precisao, boolean aproximada, double longitude, double latitude, Instant localizadoEm) {

		static ImovelNoPonto de(ImovelNoMapa i) {
			return new ImovelNoPonto(i.matricula(), i.unidadeId(), i.unidadeNome(), i.situacao(), i.precisao(),
					i.precisao().aproximada(), i.longitude(), i.latitude(), i.localizadoEm());
		}
	}

	record MarcacaoDto(UUID id, UUID unidadeId, String unidadeNome, double longitude, double latitude, String titulo,
			@Nullable String observacao, String autorNome, Instant criadaEm, boolean podeRemover) {

		static MarcacaoDto de(Marcacao m, @Nullable UUID usuario) {
			return new MarcacaoDto(m.id(), m.unidadeId(), m.unidadeNome(), m.longitude(), m.latitude(), m.titulo(),
					m.observacao(), m.autorNome(), m.criadaEm(), m.autorId().equals(usuario));
		}
	}

}
