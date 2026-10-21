package dev.aurora.imoveis.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.imoveis.model.SituacaoImovel;
import dev.aurora.imoveis.service.SemLigacaoService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/imoveis/sem-ligacao")
class SemLigacaoController {

	private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

	private final SemLigacaoService semLigacao;

	SemLigacaoController(SemLigacaoService semLigacao) {
		this.semLigacao = semLigacao;
	}

	/**
	 * D1: lista paginada por cursor, ordenada por matrícula.
	 * @param matricula início da matrícula (letras, dígitos, ponto, hífen ou barra)
	 * @param cursor valor de {@code proximo} da página anterior
	 */
	@GetMapping
	@PreAuthorize("hasAuthority('indicador:ler')")
	SemLigacaoResponse listar(@RequestParam Optional<UUID> unidade,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") Optional<YearMonth> competencia,
			@RequestParam Optional<SituacaoImovel> situacao,
			@RequestParam Optional<@Pattern(regexp = "[\\p{Alnum}./-]{1,40}") String> matricula,
			@RequestParam Optional<String> cursor, @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limite) {
		return SemLigacaoResponse.de(semLigacao.listar(unidade, competencia, situacao, matricula, cursor, limite));
	}

	/**
	 * D2: CSV para o Excel. O arquivo é gerado antes da resposta (a emissão só é auditada
	 * se o arquivo ficou completo) e apagado assim que é enviado.
	 */
	@GetMapping("/exportacao.csv")
	@PreAuthorize("hasAuthority('relatorio:exportar')")
	ResponseEntity<Resource> exportar(@RequestParam UUID unidade,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") Optional<YearMonth> competencia,
			@RequestParam Optional<SituacaoImovel> situacao) throws IOException {
		var arquivo = Files.createTempFile("aurora-sem-ligacao-", ".csv");
		try {
			try (var saida = Files.newOutputStream(arquivo)) {
				semLigacao.exportarCsv(unidade, competencia, situacao, saida);
			}
			var tamanho = Files.size(arquivo);
			var conteudo = Files.newInputStream(arquivo, StandardOpenOption.DELETE_ON_CLOSE);
			var nome = "imoveis-sem-ligacao-" + competencia.map(YearMonth::toString).orElse("atual") + ".csv";
			return ResponseEntity.ok()
				.contentType(CSV)
				.contentLength(tamanho)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(nome).build().toString())
				.body(new InputStreamResource(conteudo));
		}
		catch (IOException | RuntimeException erro) {
			Files.deleteIfExists(arquivo);
			throw erro;
		}
	}

}
