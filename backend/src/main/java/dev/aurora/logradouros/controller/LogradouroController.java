package dev.aurora.logradouros.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.logradouros.model.Cobertura;
import dev.aurora.logradouros.model.DadosLogradouro;
import dev.aurora.logradouros.model.DadosTrecho;
import dev.aurora.logradouros.model.ImovelNoLogradouro;
import dev.aurora.logradouros.model.Importacao;
import dev.aurora.logradouros.model.Lado;
import dev.aurora.logradouros.model.Logradouro;
import dev.aurora.logradouros.model.ResultadoImportacao;
import dev.aurora.logradouros.model.TipoImportacao;
import dev.aurora.logradouros.model.Trecho;
import dev.aurora.logradouros.service.ImportacaoService;
import dev.aurora.logradouros.service.LogradouroService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Logradouros, trechos e endereços dos imóveis (J3, ADR 0043). */
@RestController
@RequestMapping("/api/v1/logradouros")
@ExigePermissao("logradouro:gerenciar")
class LogradouroController {

	record LogradouroResponse(UUID id, UUID unidadeId, String unidadeNome, String codigo, @Nullable String tipo,
			String nome, @Nullable String bairro, long trechos, BigDecimal extensaoM, long imoveis) {

		static LogradouroResponse de(Logradouro l) {
			return new LogradouroResponse(l.id(), l.unidadeId(), l.unidadeNome(), l.codigo(), l.tipo(), l.nome(),
					l.bairro(), l.trechos(), l.extensaoM(), l.imoveis());
		}
	}

	record PaginaLogradourosResponse(List<LogradouroResponse> itens, long total, int pagina, int tamanho) {
	}

	/**
	 * @param coordenadas [longitude, latitude] de cada ponto da linha
	 */
	record TrechoResponse(UUID id, String codigo, BigDecimal extensaoM, @Nullable Integer numeroInicial,
			@Nullable Integer numeroFinal, Lado lado, long imoveis, List<double[]> coordenadas) {

		static TrechoResponse de(Trecho t) {
			return new TrechoResponse(t.id(), t.codigo(), t.extensaoM(), t.numeroInicial(), t.numeroFinal(), t.lado(),
					t.imoveis(), t.coordenadas());
		}
	}

	record DetalheLogradouroResponse(LogradouroResponse logradouro, List<TrechoResponse> trechos) {

		static DetalheLogradouroResponse de(LogradouroService.Detalhe d) {
			return new DetalheLogradouroResponse(LogradouroResponse.de(d.logradouro()),
					d.trechos().stream().map(TrechoResponse::de).toList());
		}
	}

	/**
	 * @param matricula inteira só para quem pode ver dados pessoais; senão mascarada
	 */
	record ImovelDoLogradouroResponse(String matricula, @Nullable String numero, @Nullable String complemento,
			@Nullable String trecho, @Nullable String precisao) {

		static ImovelDoLogradouroResponse de(ImovelNoLogradouro i) {
			return new ImovelDoLogradouroResponse(i.matricula(), i.numero(), i.complemento(), i.trecho(), i.precisao());
		}
	}

	record CoberturaResponse(long imoveis, long comEndereco, long comTrecho, long aproximados) {

		static CoberturaResponse de(Cobertura c) {
			return new CoberturaResponse(c.imoveis(), c.comEndereco(), c.comTrecho(), c.aproximados());
		}
	}

	record RejeicaoResponse(int posicao, String motivo) {
	}

	record ResultadoImportacaoResponse(TipoImportacao tipo, boolean simulacao, int lidos, int novos, int atualizados,
			int inalterados, int rejeitados, List<RejeicaoResponse> rejeicoes, int semTrecho, int ausentes) {

		static ResultadoImportacaoResponse de(ResultadoImportacao r) {
			return new ResultadoImportacaoResponse(r.tipo(), r.simulacao(), r.lidos(), r.novos(), r.atualizados(),
					r.inalterados(), r.rejeitados(),
					r.rejeicoes().stream().map(x -> new RejeicaoResponse(x.posicao(), x.motivo())).toList(),
					r.semTrecho(), r.ausentes());
		}
	}

	record ImportacaoResponse(UUID id, TipoImportacao tipo, @Nullable String unidadeNome, String arquivo, int lidos,
			int novos, int atualizados, int rejeitados, String feitaPor, Instant feitaEm) {

		static ImportacaoResponse de(Importacao i) {
			return new ImportacaoResponse(i.id(), i.tipo(), i.unidadeNome(), i.arquivo(), i.lidos(), i.novos(),
					i.atualizados(), i.rejeitados(), i.feitaPor(), i.feitaEm());
		}
	}

	record LogradouroRequest(@Nullable @Size(max = 30) String tipo, @NotNull @Size(max = 120) String nome,
			@Nullable @Size(max = 80) String bairro) {
	}

	record TrechoRequest(@Nullable Integer numeroInicial, @Nullable Integer numeroFinal, @NotNull Lado lado) {
	}

	private final LogradouroService logradouros;

	private final ImportacaoService importacoes;

	LogradouroController(LogradouroService logradouros, ImportacaoService importacoes) {
		this.logradouros = logradouros;
		this.importacoes = importacoes;
	}

	/**
	 * Logradouros sob a unidade (todos do escopo sem ela), por nome ou código, 50 por
	 * página.
	 */
	@GetMapping
	PaginaLogradourosResponse buscar(@RequestParam(required = false) @Nullable UUID unidade,
			@RequestParam(defaultValue = "") @Size(max = 80) String busca,
			@RequestParam(defaultValue = "0") int pagina) {
		var p = logradouros.buscar(unidade, busca, pagina);
		return new PaginaLogradourosResponse(p.itens().stream().map(LogradouroResponse::de).toList(), p.total(),
				p.pagina(), p.tamanho());
	}

	/** Imóveis da unidade com endereço, com trecho e com localização aproximada. */
	@GetMapping("/cobertura")
	CoberturaResponse cobertura(@RequestParam UUID unidade) {
		return CoberturaResponse.de(logradouros.cobertura(unidade));
	}

	@GetMapping("/{id}")
	DetalheLogradouroResponse detalhe(@PathVariable UUID id) {
		return DetalheLogradouroResponse.de(logradouros.detalhe(id));
	}

	/** Até 2.000 imóveis na ordem da numeração. */
	@GetMapping("/{id}/imoveis")
	List<ImovelDoLogradouroResponse> imoveis(@PathVariable UUID id) {
		return logradouros.imoveis(id).stream().map(ImovelDoLogradouroResponse::de).toList();
	}

	@PutMapping("/{id}")
	DetalheLogradouroResponse corrigir(@PathVariable UUID id, @Valid @RequestBody LogradouroRequest pedido) {
		return DetalheLogradouroResponse
			.de(logradouros.corrigir(id, new DadosLogradouro(pedido.tipo(), pedido.nome(), pedido.bairro())));
	}

	@PutMapping("/trechos/{id}")
	DetalheLogradouroResponse corrigirTrecho(@PathVariable UUID id, @Valid @RequestBody TrechoRequest pedido) {
		return DetalheLogradouroResponse.de(logradouros.corrigirTrecho(id,
				new DadosTrecho(pedido.numeroInicial(), pedido.numeroFinal(), pedido.lado())));
	}

	/** Trechos do município em GeoJSON; com {@code simular} nada é gravado. */
	@PostMapping(path = "/importacoes/trechos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResultadoImportacaoResponse importarTrechos(@RequestParam UUID unidade,
			@RequestParam(defaultValue = "false") boolean simular, @RequestPart("arquivo") MultipartFile arquivo)
			throws IOException {
		return ResultadoImportacaoResponse
			.de(importacoes.importarTrechos(unidade, nome(arquivo), arquivo.getBytes(), simular));
	}

	/** Endereços dos imóveis em CSV (matricula;codigo_logradouro;numero;complemento). */
	@PostMapping(path = "/importacoes/enderecos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResultadoImportacaoResponse importarEnderecos(@RequestParam(defaultValue = "false") boolean simular,
			@RequestPart("arquivo") MultipartFile arquivo) throws IOException {
		return ResultadoImportacaoResponse
			.de(importacoes.importarEnderecos(nome(arquivo), arquivo.getBytes(), simular));
	}

	@GetMapping("/importacoes")
	List<ImportacaoResponse> importacoes() {
		return importacoes.recentes().stream().map(ImportacaoResponse::de).toList();
	}

	private static String nome(MultipartFile arquivo) {
		var nome = Objects.requireNonNullElse(arquivo.getOriginalFilename(), "arquivo");
		var limpo = nome.replaceAll("[\\\\/]", "_").strip();
		return limpo.isEmpty() ? "arquivo" : limpo.length() > 255 ? limpo.substring(0, 255) : limpo;
	}

}
