package dev.aurora.mapa.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ExigePermissao;
import dev.aurora.mapa.model.FiltroImoveis;
import dev.aurora.mapa.model.FormatoExportacao;
import dev.aurora.mapa.model.PedidoExportacao;
import dev.aurora.mapa.model.SituacaoNoMapa;
import dev.aurora.mapa.service.ExportacaoMapaService;
import dev.aurora.mapa.service.MapaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E5: exporta a área visível em CSV ou PDF. POST porque leva as camadas ligadas e, no
 * PDF, a imagem do mapa. O arquivo é gerado antes da resposta (só é auditado se ficou
 * completo) e apagado ao ser enviado.
 */
@RestController
@RequestMapping("/api/v1/mapa/exportacao")
class ExportacaoMapaController {

	/** Imagem do mapa: até 6 MB e 4096 px por lado. */
	static final int MAXIMO_IMAGEM = 6 * 1024 * 1024;

	static final int LADO_MAXIMO_IMAGEM = 4096;

	private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private static final byte[] ASSINATURA_PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

	/**
	 * @param imoveis camada de imóveis como está no mapa; nulo se desligada
	 * @param imagemPng mapa como a pessoa o vê, em PNG e Base64 (só no PDF)
	 */
	record ExportacaoRequest(@NotNull FormatoExportacao formato, @NotNull @Valid AreaParametro area,
			@Valid @Nullable CamadaImoveis imoveis, boolean marcacoes,
			@Size(max = 8_400_000) @Nullable String imagemPng) {
	}

	record CamadaImoveis(@Nullable YearMonth competencia, @Nullable Set<SituacaoNoMapa> situacoes,
			boolean aproximadas) {
	}

	private final ExportacaoMapaService exportacao;

	private final MapaService mapa;

	ExportacaoMapaController(ExportacaoMapaService exportacao, MapaService mapa) {
		this.exportacao = exportacao;
		this.mapa = mapa;
	}

	@PostMapping
	@ExigePermissao("relatorio:exportar")
	ResponseEntity<Resource> exportar(@Valid @RequestBody ExportacaoRequest pedido) throws IOException {
		var camada = pedido.imoveis();
		FiltroImoveis filtro = null;
		if (camada != null) {
			var situacoes = camada.situacoes() == null || camada.situacoes().isEmpty()
					? EnumSet.allOf(SituacaoNoMapa.class) : camada.situacoes();
			filtro = new FiltroImoveis(mapa.competencia(Optional.ofNullable(camada.competencia())), situacoes,
					camada.aproximadas());
		}
		var png = pedido.formato() == FormatoExportacao.PDF ? imagem(pedido.imagemPng()) : null;
		var completo = new PedidoExportacao(pedido.formato(), pedido.area().area(), filtro, pedido.marcacoes(), png);

		var pdf = pedido.formato() == FormatoExportacao.PDF;
		var arquivo = Files.createTempFile("aurora-mapa-", pdf ? ".pdf" : ".csv");
		try {
			try (var saida = Files.newOutputStream(arquivo)) {
				exportacao.exportar(completo, saida);
			}
			var tamanho = Files.size(arquivo);
			var conteudo = Files.newInputStream(arquivo, StandardOpenOption.DELETE_ON_CLOSE);
			var nome = "area-do-mapa" + (filtro == null ? "" : "-" + filtro.competencia()) + (pdf ? ".pdf" : ".csv");
			return ResponseEntity.ok()
				.contentType(pdf ? MediaType.APPLICATION_PDF : CSV)
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

	/**
	 * Confere assinatura e dimensões no cabeçalho do PNG antes de decodificar (evita
	 * imagens enormes descompactadas na memória).
	 */
	static byte @Nullable [] imagem(@Nullable String base64) {
		if (base64 == null || base64.isBlank()) {
			return null;
		}
		byte[] png;
		try {
			png = Base64.getDecoder().decode(base64.replaceFirst("^data:image/png;base64,", ""));
		}
		catch (IllegalArgumentException erro) {
			throw new RegraVioladaException("A imagem do mapa não está em Base64.");
		}
		if (png.length > MAXIMO_IMAGEM || png.length < 24) {
			throw new RegraVioladaException("A imagem do mapa precisa ter até 6 MB.");
		}
		for (int i = 0; i < ASSINATURA_PNG.length; i++) {
			if (png[i] != ASSINATURA_PNG[i]) {
				throw new RegraVioladaException("A imagem do mapa precisa ser PNG.");
			}
		}
		long largura = inteiro(png, 16);
		long altura = inteiro(png, 20);
		if (largura < 1 || altura < 1 || largura > LADO_MAXIMO_IMAGEM || altura > LADO_MAXIMO_IMAGEM) {
			throw new RegraVioladaException("A imagem do mapa precisa ter até 4096 pixels por lado.");
		}
		return png;
	}

	private static long inteiro(byte[] dados, int posicao) {
		return ((dados[posicao] & 0xFFL) << 24) | ((dados[posicao + 1] & 0xFFL) << 16)
				| ((dados[posicao + 2] & 0xFFL) << 8) | (dados[posicao + 3] & 0xFFL);
	}

}
