package dev.aurora.adesao.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.adesao.service.ExportacaoPainelService;
import dev.aurora.adesao.service.PainelService;
import dev.aurora.contexto.ExigePermissao;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/painel")
class PainelController {

	private final PainelService painel;

	private final ExportacaoPainelService exportacao;

	PainelController(PainelService painel, ExportacaoPainelService exportacao) {
		this.painel = painel;
		this.exportacao = exportacao;
	}

	/**
	 * @param unidade unidade do escopo (padrão: a mais alta)
	 * @param competencia mês no formato yyyy-MM (padrão: o mais recente com medição)
	 */
	@GetMapping
	@ExigePermissao("indicador:ler")
	PainelResponse painel(@RequestParam Optional<UUID> unidade,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") Optional<YearMonth> competencia) {
		return PainelResponse.de(painel.montar(unidade, competencia));
	}

	/**
	 * B6: o painel da unidade em CSV (para o Excel) ou PDF (para anexar); a emissão fica
	 * na auditoria. O arquivo é gerado antes da resposta e apagado ao ser enviado.
	 * @param formato csv ou pdf
	 */
	@GetMapping("/exportacao.{formato:csv|pdf}")
	@ExigePermissao("relatorio:exportar")
	ResponseEntity<Resource> exportar(@PathVariable String formato, @RequestParam Optional<UUID> unidade,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") Optional<YearMonth> competencia) throws IOException {
		var tipo = ExportacaoPainelService.Formato.valueOf(formato.toUpperCase(Locale.ROOT));
		var arquivo = Files.createTempFile("aurora-painel-", "." + formato);
		try {
			ExportacaoPainelService.Arquivo gerado;
			try (var saida = Files.newOutputStream(arquivo)) {
				gerado = exportacao.exportar(unidade, competencia, tipo, saida);
			}
			var tamanho = Files.size(arquivo);
			var conteudo = Files.newInputStream(arquivo, StandardOpenOption.DELETE_ON_CLOSE);
			return ResponseEntity.ok()
				.contentType(tipo == ExportacaoPainelService.Formato.PDF ? MediaType.APPLICATION_PDF
						: new MediaType("text", "csv", StandardCharsets.UTF_8))
				.contentLength(tamanho)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(gerado.nome()).build().toString())
				.body(new InputStreamResource(conteudo));
		}
		catch (IOException | RuntimeException erro) {
			Files.deleteIfExists(arquivo);
			throw erro;
		}
	}

}
