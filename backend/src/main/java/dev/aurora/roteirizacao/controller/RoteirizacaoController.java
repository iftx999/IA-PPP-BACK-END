package dev.aurora.roteirizacao.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.roteirizacao.model.Calculo;
import dev.aurora.roteirizacao.model.Coordenada;
import dev.aurora.roteirizacao.model.MotivoPendente;
import dev.aurora.roteirizacao.model.OrigemRoteirizacao;
import dev.aurora.roteirizacao.model.PedidoRoteirizacao;
import dev.aurora.roteirizacao.model.Roteirizacao;
import dev.aurora.roteirizacao.model.SituacaoRoteirizacao;
import dev.aurora.roteirizacao.service.RoteirizacaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roteirização das OS (J6, J7; ADR 0046). O cálculo é assíncrono: o pedido devolve a
 * roteirização em CALCULANDO e o cliente consulta até ficar PRONTA ou FALHOU. Resultados
 * são estimativas (RN-25).
 */
@RestController
@RequestMapping("/api/v1/roteirizacoes")
@ExigePermissao("os:programar")
class RoteirizacaoController {

	record PontoDaRotaDto(double latitude, double longitude) {

		static @Nullable PontoDaRotaDto de(@Nullable Coordenada c) {
			return c == null ? null : new PontoDaRotaDto(c.latitude(), c.longitude());
		}
	}

	/**
	 * @param minutosAte deslocamento desde a parada anterior (ou a partida)
	 * @param fixada o recálculo mantém na equipe e no começo da rota
	 * @param atrasa a chegada prevista passa do prazo da OS (RN-21)
	 */
	record ParadaDaRotaResponse(UUID ordemServicoId, long numero, String servicoNome, String prioridade, int ordem,
			Instant chegadaPrevista, Instant saidaPrevista, double kmAte, int minutosAte, boolean fixada,
			boolean atrasa, @Nullable PontoDaRotaDto local) {

		static ParadaDaRotaResponse de(Roteirizacao.ParadaCalculada p) {
			return new ParadaDaRotaResponse(p.ordemServicoId(), p.numero(), p.servicoNome(), p.prioridade(), p.ordem(),
					p.chegadaPrevista(), p.saidaPrevista(), p.kmAte(), p.minutosAte(), p.fixada(), p.atrasa(),
					PontoDaRotaDto.de(p.local()));
		}
	}

	/**
	 * @param tracado linhas pela malha viária (longitude, latitude), para o mapa; vazio
	 * quando o cálculo foi em linha reta
	 */
	record RotaDaEquipeResponse(UUID equipeId, String equipeNome, @Nullable PontoDaRotaDto partida, Instant inicio,
			Instant fimPrevisto, double km, int minutosDeslocamento, int minutosServico, List<List<double[]>> tracado,
			List<ParadaDaRotaResponse> paradas) {

		static RotaDaEquipeResponse de(Roteirizacao.RotaCalculada r) {
			return new RotaDaEquipeResponse(r.equipeId(), r.equipeNome(), PontoDaRotaDto.de(r.partida()), r.inicio(),
					r.fimPrevisto(), r.km(), r.minutosDeslocamento(), r.minutosServico(),
					r.tracado()
						.stream()
						.map(linha -> linha.stream().map(c -> new double[] { c.longitude(), c.latitude() }).toList())
						.toList(),
					r.paradas().stream().map(ParadaDaRotaResponse::de).toList());
		}
	}

	record OsSemRotaResponse(UUID ordemServicoId, long numero, String servicoNome, String prioridade,
			MotivoPendente motivo) {

		static OsSemRotaResponse de(Roteirizacao.Pendente p) {
			return new OsSemRotaResponse(p.ordemServicoId(), p.numero(), p.servicoNome(), p.prioridade(), p.motivo());
		}
	}

	/**
	 * @param calculo VALHALLA (malha viária) ou LINHA_RETA (estimativa grosseira: serviço
	 * de rotas indisponível); nulo enquanto calcula
	 * @param erro motivo da falha, quando FALHOU
	 */
	record RoteirizacaoResponse(UUID id, UUID unidadeId, String unidadeNome, LocalDate data, OrigemRoteirizacao origem,
			SituacaoRoteirizacao situacao, @Nullable Calculo calculo, @Nullable String erro, @Nullable UUID anteriorId,
			Instant criadaEm, @Nullable String criadaPor, @Nullable Instant concluidaEm, @Nullable Instant publicadaEm,
			List<RotaDaEquipeResponse> rotas, List<OsSemRotaResponse> semRota) {

		static RoteirizacaoResponse de(Roteirizacao r) {
			return new RoteirizacaoResponse(r.id(), r.unidadeId(), r.unidadeNome(), r.data(), r.origem(), r.situacao(),
					r.calculo(), r.erro(), r.anteriorId(), r.criadaEm(), r.criadaPor(), r.concluidaEm(),
					r.publicadaEm(), r.rotas().stream().map(RotaDaEquipeResponse::de).toList(),
					r.pendentes().stream().map(OsSemRotaResponse::de).toList());
		}
	}

	record RoteirizacaoRequest(@NotNull UUID unidadeId, @NotNull LocalDate data,
			@NotNull @Size(min = 1, max = 50) List<@NotNull UUID> equipes,
			@NotNull @Size(min = 1, max = 1500) List<@NotNull UUID> ordens) {
	}

	/** OS que ficam no começo da rota da equipe, nesta ordem. */
	record FixacaoRequest(@NotNull UUID equipeId, @NotNull @Size(max = 200) List<@NotNull UUID> ordens) {
	}

	record RecalculoRequest(@NotNull @Size(max = 50) List<@NotNull @Valid FixacaoRequest> fixadas) {
	}

	record ReotimizacaoRequest(@NotNull UUID unidadeId, @NotNull @Size(min = 1, max = 50) List<@NotNull UUID> equipes) {
	}

	private final RoteirizacaoService roteirizacoes;

	RoteirizacaoController(RoteirizacaoService roteirizacoes) {
		this.roteirizacoes = roteirizacoes;
	}

	/** J6: calcula as rotas do dia (prévia, a partir da base das equipes). */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	RoteirizacaoResponse calcular(@Valid @RequestBody RoteirizacaoRequest pedido) {
		return RoteirizacaoResponse.de(roteirizacoes.calcular(new PedidoRoteirizacao(pedido.unidadeId(), pedido.data(),
				OrigemRoteirizacao.PREVIA, pedido.equipes(), pedido.ordens(), java.util.Map.of(), null)));
	}

	/** Roteirizações do dia (sem as rotas), da mais nova para a mais antiga. */
	@GetMapping
	List<RoteirizacaoResponse> listar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
		return roteirizacoes.listar(data).stream().map(RoteirizacaoResponse::de).toList();
	}

	@GetMapping("/{id}")
	RoteirizacaoResponse buscar(@PathVariable UUID id) {
		return RoteirizacaoResponse.de(roteirizacoes.buscar(id));
	}

	/** J6: recalcula mantendo as OS fixadas (ou arrastadas) no começo de cada rota. */
	@PostMapping("/{id}/recalcular")
	@ResponseStatus(HttpStatus.ACCEPTED)
	RoteirizacaoResponse recalcular(@PathVariable UUID id, @Valid @RequestBody RecalculoRequest pedido) {
		var fixadas = new LinkedHashMap<UUID, List<UUID>>();
		pedido.fixadas().forEach(f -> fixadas.merge(f.equipeId(), f.ordens(), (a, b) -> {
			var junta = new java.util.ArrayList<>(a);
			junta.addAll(b);
			return junta;
		}));
		return RoteirizacaoResponse.de(roteirizacoes.recalcular(id, fixadas));
	}

	/**
	 * J7: recalcula hoje, da última posição de cada equipe; OS em execução ficam fixas.
	 */
	@PostMapping("/reotimizar")
	@ResponseStatus(HttpStatus.ACCEPTED)
	RoteirizacaoResponse reotimizar(@Valid @RequestBody ReotimizacaoRequest pedido) {
		return RoteirizacaoResponse.de(roteirizacoes.reotimizar(pedido.unidadeId(), pedido.equipes()));
	}

	/** Publica: as rotas viram a programação das equipes no dia, na ordem calculada. */
	@PostMapping("/{id}/publicar")
	RoteirizacaoResponse publicar(@PathVariable UUID id) {
		return RoteirizacaoResponse.de(roteirizacoes.publicar(id));
	}

	@PostMapping("/{id}/descartar")
	RoteirizacaoResponse descartar(@PathVariable UUID id) {
		return RoteirizacaoResponse.de(roteirizacoes.descartar(id));
	}

}
