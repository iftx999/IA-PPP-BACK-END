package dev.aurora.unidades;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Unidades do escopo do usuário (as atribuídas e as descendentes). */
@RestController
@RequestMapping("/api/v1/unidades")
class UnidadeController {

	record Unidade(UUID id, String tipo, UUID unidadePaiId, String codigo, String nome, String codigoIbge) {
	}

	private static final String SELECAO = """
			SELECT id, tipo, unidade_pai_id, codigo, nome, codigo_ibge
			  FROM unidade
			 WHERE ativo AND caminho <@ ANY (app_escopos())""";

	private final JdbcClient jdbc;

	UnidadeController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('unidade:ler')")
	List<Unidade> listar() {
		return jdbc.sql(SELECAO + " ORDER BY caminho").query(Unidade.class).list();
	}

	/** Unidade de outro tenant ou fora do escopo responde 404, sem revelar que existe. */
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('unidade:ler')")
	Unidade detalhar(@PathVariable UUID id) {
		return jdbc.sql(SELECAO + " AND id = ?")
			.param(id)
			.query(Unidade.class)
			.optional()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

}
