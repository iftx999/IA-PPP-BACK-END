package dev.aurora.mapa.controller;

import java.time.Duration;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.mapa.controller.MapaResponses.CamadasMapa;
import dev.aurora.mapa.controller.MapaResponses.ImovelNoPonto;
import dev.aurora.mapa.model.FiltroImoveis;
import dev.aurora.mapa.model.SituacaoNoMapa;
import dev.aurora.mapa.repository.TileRepository;
import dev.aurora.mapa.service.MapaService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * E1 e E2: camadas leves (municípios pela adesão, setores, estações), tiles vetoriais de
 * imóveis e rede e o detalhe do imóvel clicado.
 */
@RestController
@RequestMapping("/api/v1/mapa")
@ExigePermissao("indicador:ler")
class MapaController {

	static final MediaType MVT = MediaType.parseMediaType("application/vnd.mapbox-vector-tile");

	private final MapaService mapa;

	MapaController(MapaService mapa) {
		this.mapa = mapa;
	}

	@GetMapping("/camadas")
	CamadasMapa camadas(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") Optional<YearMonth> competencia) {
		return CamadasMapa.de(mapa.camadas(competencia), TileRepository.ZOOM_IMOVEIS, TileRepository.ZOOM_REDE);
	}

	/**
	 * Tile z/x/y (esquema XYZ). Camadas {@code agrupamentos} (até o zoom de imóveis) ou
	 * {@code imoveis}, e {@code rede}. Sem matrícula: o detalhe vem de {@code /imoveis}.
	 * @param situacoes situações visíveis (todas, se não informado)
	 */
	@GetMapping(value = "/tiles/{z}/{x}/{y}.mvt", produces = "application/vnd.mapbox-vector-tile")
	ResponseEntity<byte[]> tile(@PathVariable @Min(0) @Max(20) int z, @PathVariable int x, @PathVariable int y,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") Optional<YearMonth> competencia,
			@RequestParam Optional<Set<SituacaoNoMapa>> situacoes,
			@RequestParam(defaultValue = "true") boolean aproximadas,
			@RequestParam(defaultValue = "true") boolean rede) {
		var cache = CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate();
		var mes = mapa.competenciaSeHouver(competencia);
		if (mes.isEmpty()) {
			// Cliente ainda sem carga: mapa sem imóveis, sem erro.
			return ResponseEntity.noContent().cacheControl(cache).build();
		}
		var filtro = filtro(mes, situacoes, aproximadas);
		var tile = mapa.tile(z, x, y, filtro, rede);
		if (tile.length == 0) {
			return ResponseEntity.noContent().cacheControl(cache).build();
		}
		return ResponseEntity.ok().contentType(MVT).cacheControl(cache).body(tile);
	}

	/**
	 * Imóveis a até {@code raio} metros do clique, do mais próximo ao mais distante
	 * (máximo de 10).
	 */
	@GetMapping("/imoveis")
	List<ImovelNoPonto> imoveis(@RequestParam @DecimalMin("-75") @DecimalMax("-28") double longitude,
			@RequestParam @DecimalMin("-35") @DecimalMax("7") double latitude,
			@RequestParam(defaultValue = "15") @Min(1) @Max(500) int raio,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") Optional<YearMonth> competencia,
			@RequestParam Optional<Set<SituacaoNoMapa>> situacoes,
			@RequestParam(defaultValue = "true") boolean aproximadas) {
		return mapa.imoveisPerto(longitude, latitude, raio, filtro(competencia, situacoes, aproximadas))
			.stream()
			.map(ImovelNoPonto::de)
			.toList();
	}

	private FiltroImoveis filtro(Optional<YearMonth> competencia, Optional<Set<SituacaoNoMapa>> situacoes,
			boolean aproximadas) {
		return new FiltroImoveis(mapa.competencia(competencia),
				situacoes.filter(s -> !s.isEmpty()).orElse(EnumSet.allOf(SituacaoNoMapa.class)), aproximadas);
	}

}
