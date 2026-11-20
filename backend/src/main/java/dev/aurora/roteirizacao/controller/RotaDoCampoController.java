package dev.aurora.roteirizacao.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.aurora.campanhas.JornadasDoCampo;
import dev.aurora.contexto.ExigePermissao;
import dev.aurora.roteirizacao.model.Coordenada;
import dev.aurora.roteirizacao.model.Roteirizacao;
import dev.aurora.roteirizacao.service.RoteirizacaoService;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rota do dia no app de campo (J8, ADR 0048): a publicada mais recente da equipe da
 * jornada aberta, com o traçado para o mapa. ETag = id da roteirização: o celular só
 * baixa de novo quando a rota muda (304 Not Modified). Sem rota publicada: 204.
 */
@RestController
@RequestMapping("/api/v1/campo")
@ExigePermissao("os:executar")
class RotaDoCampoController {

	private final JornadasDoCampo jornadas;

	private final RoteirizacaoService roteirizacoes;

	private final Clock relogio;

	RotaDoCampoController(JornadasDoCampo jornadas, RoteirizacaoService roteirizacoes, Clock relogio) {
		this.jornadas = jornadas;
		this.roteirizacoes = roteirizacoes;
		this.relogio = relogio;
	}

	/**
	 * @param local imóvel ou início do trecho; nulo para OS sem localização
	 */
	record ParadaResponse(UUID ordemServicoId, int ordem, Instant chegadaPrevista, Instant saidaPrevista,
			@Nullable Double latitude, @Nullable Double longitude) {
	}

	/**
	 * @param calculo VALHALLA (pela malha) ou LINHA_RETA (estimativa grosseira, sem
	 * traçado)
	 * @param tracado linhas do traçado, cada ponto como [longitude, latitude] (ordem do
	 * GeoJSON); vazio sem Valhalla
	 */
	record RotaResponse(UUID roteirizacaoId, String origem, String calculo, @Nullable Instant publicadaEm,
			Instant inicio, Instant fimPrevisto, double km, int minutosDeslocamento, int minutosServico,
			@Nullable Double partidaLatitude, @Nullable Double partidaLongitude, List<List<double[]>> tracado,
			List<ParadaResponse> paradas) {
	}

	@GetMapping("/rota")
	ResponseEntity<RotaResponse> rota(
			@RequestHeader(name = "X-Aurora-Dispositivo", required = false) @Nullable UUID dispositivo,
			@RequestHeader(name = "If-None-Match", required = false) @Nullable String conhecida) {
		var equipe = jornadas.equipeEmJornada(dispositivo);
		var publicada = roteirizacoes.publicadaDaEquipe(equipe, LocalDate.now(relogio)).orElse(null);
		var rota = publicada == null ? null
				: publicada.rotas().stream().filter(r -> r.equipeId().equals(equipe)).findFirst().orElse(null);
		if (publicada == null || rota == null) {
			return ResponseEntity.noContent().build();
		}
		var etag = "\"" + publicada.id() + "\"";
		if (etag.equals(conhecida)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
		}
		return ResponseEntity.ok().eTag(etag).body(resposta(publicada, rota));
	}

	private static RotaResponse resposta(Roteirizacao r, Roteirizacao.RotaCalculada rota) {
		var partida = rota.partida();
		return new RotaResponse(r.id(), r.origem().name(), r.calculo() == null ? "LINHA_RETA" : r.calculo().name(),
				r.publicadaEm(), rota.inicio(), rota.fimPrevisto(), rota.km(), rota.minutosDeslocamento(),
				rota.minutosServico(), partida == null ? null : partida.latitude(),
				partida == null ? null : partida.longitude(),
				rota.tracado()
					.stream()
					.map(linha -> linha.stream().map(c -> new double[] { c.longitude(), c.latitude() }).toList())
					.toList(),
				rota.paradas()
					.stream()
					.map(p -> new ParadaResponse(p.ordemServicoId(), p.ordem(), p.chegadaPrevista(), p.saidaPrevista(),
							latitude(p.local()), longitude(p.local())))
					.toList());
	}

	private static @Nullable Double latitude(@Nullable Coordenada c) {
		return c == null ? null : c.latitude();
	}

	private static @Nullable Double longitude(@Nullable Coordenada c) {
		return c == null ? null : c.longitude();
	}

}
