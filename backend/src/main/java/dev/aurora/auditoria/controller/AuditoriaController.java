package dev.aurora.auditoria.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.auditoria.TipoEvento;
import dev.aurora.auditoria.model.Autor;
import dev.aurora.auditoria.model.EventoConsultado;
import dev.aurora.auditoria.model.ResultadoVerificacao;
import dev.aurora.auditoria.service.ConsultaAuditoriaService;
import dev.aurora.contexto.ExigePermissao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta da trilha de auditoria (S8, doc 08, 8.7). */
@RestController
@RequestMapping("/api/v1/auditoria")
@ExigePermissao("auditoria:ler")
class AuditoriaController {

	private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

	record EventoDto(long sequencia, Instant ocorridoEm, String tipo, @Nullable String recurso,
			@Nullable String recursoNome, @Nullable UUID usuarioId, @Nullable String usuarioNome,
			@Nullable String usuarioEmail, @Nullable String ip, @Nullable UUID unidadeId, @Nullable String unidadeNome,
			JsonNode detalhes) {
	}

	record EventosResponse(List<EventoDto> eventos, @Nullable String proximo) {
	}

	record AutorDto(UUID id, String nome, String email) {

		static AutorDto de(Autor a) {
			return new AutorDto(a.id(), a.nome(), a.email());
		}
	}

	record VerificacaoResponse(long eventos, long verificados, long legados, @Nullable Long primeiraQuebra,
			boolean integra) {

		static VerificacaoResponse de(ResultadoVerificacao r) {
			return new VerificacaoResponse(r.eventos(), r.verificados(), r.legados(), r.primeiraQuebra(), r.integra());
		}
	}

	private final ConsultaAuditoriaService consulta;

	private final JsonMapper json;

	AuditoriaController(ConsultaAuditoriaService consulta, JsonMapper json) {
		this.consulta = consulta;
		this.json = json;
	}

	/**
	 * @param tipo tipos de evento (repetir o parâmetro para vários)
	 * @param de primeiro dia do período (inclusivo)
	 * @param ate último dia do período (inclusivo)
	 * @param unidade unidade relacionada ao evento, incluindo as abaixo dela
	 * @param cursor valor de {@code proximo} da página anterior
	 */
	@GetMapping("/eventos")
	EventosResponse eventos(@RequestParam Optional<UUID> usuario, @RequestParam Optional<List<String>> tipo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> de,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> ate,
			@RequestParam Optional<UUID> unidade, @RequestParam Optional<String> cursor,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limite) {
		var pagina = consulta.consultar(pedido(usuario, tipo, de, ate, unidade), cursor, limite);
		return new EventosResponse(pagina.eventos().stream().map(this::dto).toList(), pagina.proximo());
	}

	@GetMapping("/eventos/exportacao.csv")
	ResponseEntity<Resource> exportar(@RequestParam Optional<UUID> usuario, @RequestParam Optional<List<String>> tipo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> de,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> ate,
			@RequestParam Optional<UUID> unidade) throws IOException {
		var arquivo = Files.createTempFile("aurora-auditoria-", ".csv");
		try {
			try (var saida = Files.newOutputStream(arquivo)) {
				consulta.exportarCsv(pedido(usuario, tipo, de, ate, unidade), saida);
			}
			var tamanho = Files.size(arquivo);
			var conteudo = Files.newInputStream(arquivo, StandardOpenOption.DELETE_ON_CLOSE);
			return ResponseEntity.ok()
				.contentType(CSV)
				.contentLength(tamanho)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("auditoria.csv").build().toString())
				.body(new InputStreamResource(conteudo));
		}
		catch (IOException | RuntimeException erro) {
			Files.deleteIfExists(arquivo);
			throw erro;
		}
	}

	@GetMapping("/tipos")
	List<String> tipos() {
		return Arrays.stream(TipoEvento.values()).map(TipoEvento::name).toList();
	}

	@GetMapping("/autores")
	List<AutorDto> autores() {
		return consulta.autores().stream().map(AutorDto::de).toList();
	}

	/** Confere a cadeia inteira do tenant (elo e HMAC de cada evento). */
	@GetMapping("/verificacao")
	VerificacaoResponse verificar() {
		return VerificacaoResponse.de(consulta.verificar());
	}

	private EventoDto dto(EventoConsultado e) {
		return new EventoDto(e.sequencia(), e.ocorridoEm(), e.tipo(), e.recurso(), e.recursoNome(), e.usuarioId(),
				e.usuarioNome(), e.usuarioEmail(), e.ip(), e.unidadeId(), e.unidadeNome(), json.readTree(e.detalhes()));
	}

	private static ConsultaAuditoriaService.Pedido pedido(Optional<UUID> usuario, Optional<List<String>> tipo,
			Optional<LocalDate> de, Optional<LocalDate> ate, Optional<UUID> unidade) {
		return new ConsultaAuditoriaService.Pedido(usuario.orElse(null), tipo.orElse(List.of()), de.orElse(null),
				ate.orElse(null), unidade.orElse(null));
	}

}
