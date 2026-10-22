package dev.aurora.unidades.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.unidades.Unidade;
import dev.aurora.unidades.model.DadosUnidade;
import dev.aurora.unidades.model.TipoUnidade;
import dev.aurora.unidades.model.UnidadeAdministrada;
import dev.aurora.unidades.service.UnidadeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/unidades")
class UnidadeController {

	record UnidadeResponse(UUID id, String tipo, @Nullable UUID unidadePaiId, String codigo, String nome,
			@Nullable String codigoIbge) {

		static UnidadeResponse de(Unidade u) {
			return new UnidadeResponse(u.id(), u.tipo(), u.unidadePaiId(), u.codigo(), u.nome(), u.codigoIbge());
		}
	}

	record NoArvoreResponse(UUID id, TipoUnidade tipo, @Nullable UUID unidadePaiId, String codigo, String nome,
			@Nullable String codigoIbge, @Nullable BigDecimal latitude, @Nullable BigDecimal longitude, boolean ativo,
			int nivel, long filhasAtivas, long acessos) {

		static NoArvoreResponse de(UnidadeAdministrada u) {
			return new NoArvoreResponse(u.id(), u.tipo(), u.unidadePaiId(), u.codigo(), u.nome(), u.codigoIbge(),
					u.latitude(), u.longitude(), u.ativo(), u.nivel(), u.filhasAtivas(), u.acessos());
		}
	}

	record TipoUnidadeResponse(TipoUnidade codigo, String descricao, List<TipoUnidade> tiposPai,
			boolean exigeCodigoIbge) {

		static TipoUnidadeResponse de(TipoUnidade t) {
			return new TipoUnidadeResponse(t, t.descricao(), List.copyOf(t.tiposPai()), t.exigeCodigoIbge());
		}
	}

	record DadosUnidadeRequest(@NotBlank @Size(max = 40) String codigo, @NotBlank @Size(max = 120) String nome,
			@Nullable @Size(max = 7) String codigoIbge,
			@Nullable @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
			@Nullable @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {

		DadosUnidade dados() {
			return new DadosUnidade(codigo, nome, codigoIbge, latitude, longitude);
		}
	}

	record NovaUnidadeRequest(@NotNull TipoUnidade tipo, @NotNull UUID unidadePaiId,
			@NotNull @Valid DadosUnidadeRequest dados) {
	}

	record MoverUnidadeRequest(@NotNull UUID unidadePaiId) {
	}

	record SituacaoUnidadeRequest(boolean ativo) {
	}

	record UnidadeCriada(UUID id) {
	}

	private final UnidadeService unidades;

	UnidadeController(UnidadeService unidades) {
		this.unidades = unidades;
	}

	@GetMapping
	@ExigePermissao("unidade:ler")
	List<UnidadeResponse> listar() {
		return unidades.listar().stream().map(UnidadeResponse::de).toList();
	}

	@GetMapping("/{id}")
	@ExigePermissao("unidade:ler")
	UnidadeResponse detalhar(@PathVariable UUID id) {
		return UnidadeResponse.de(unidades.buscar(id));
	}

	/**
	 * Árvore do escopo, com inativas e o que prende cada unidade (para a administração).
	 */
	@GetMapping("/arvore")
	@ExigePermissao("unidade:gerenciar")
	List<NoArvoreResponse> arvore() {
		return unidades.arvore().stream().map(NoArvoreResponse::de).toList();
	}

	/** Tipos e a hierarquia permitida (quem pode ficar abaixo de quem). */
	@GetMapping("/tipos")
	@ExigePermissao("unidade:ler")
	List<TipoUnidadeResponse> tipos() {
		return Arrays.stream(TipoUnidade.values()).map(TipoUnidadeResponse::de).toList();
	}

	@PostMapping
	@ExigePermissao("unidade:gerenciar")
	ResponseEntity<UnidadeCriada> criar(@Valid @RequestBody NovaUnidadeRequest pedido) {
		var id = unidades.criar(pedido.tipo(), pedido.unidadePaiId(), pedido.dados().dados());
		return ResponseEntity.created(URI.create("/api/v1/unidades/" + id)).body(new UnidadeCriada(id));
	}

	@PutMapping("/{id}")
	@ExigePermissao("unidade:gerenciar")
	ResponseEntity<Void> alterar(@PathVariable UUID id, @Valid @RequestBody DadosUnidadeRequest pedido) {
		unidades.alterar(id, pedido.dados());
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/pai")
	@ExigePermissao("unidade:gerenciar")
	ResponseEntity<Void> mover(@PathVariable UUID id, @Valid @RequestBody MoverUnidadeRequest pedido) {
		unidades.mover(id, pedido.unidadePaiId());
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/situacao")
	@ExigePermissao("unidade:gerenciar")
	ResponseEntity<Void> definirSituacao(@PathVariable UUID id, @RequestBody SituacaoUnidadeRequest pedido) {
		unidades.definirAtiva(id, pedido.ativo());
		return ResponseEntity.noContent().build();
	}

}
