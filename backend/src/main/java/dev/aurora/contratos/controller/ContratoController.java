package dev.aurora.contratos.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.contratos.model.Contrato;
import dev.aurora.contratos.model.DadosContrato;
import dev.aurora.contratos.model.DadosItem;
import dev.aurora.contratos.model.Documento;
import dev.aurora.contratos.model.ItemContratual;
import dev.aurora.contratos.model.SituacaoDocumento;
import dev.aurora.contratos.model.StatusItem;
import dev.aurora.contratos.model.TipoDocumento;
import dev.aurora.contratos.model.TipoItem;
import dev.aurora.contratos.service.ContratoService;
import dev.aurora.contratos.service.RevisaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/** Contratos, documentos e revisão de itens (épico A). */
@RestController
@RequestMapping("/api/v1/contratos")
@ExigePermissao("contrato:ler")
class ContratoController {

	private static final String GERENCIAR = "contrato:gerenciar";

	record ContratoResponse(UUID id, String numero, UUID unidadeId, String unidadeNome, @Nullable String objeto,
			@Nullable String poderConcedente, @Nullable String regulador, @Nullable LocalDate dataAssinatura,
			@Nullable LocalDate vigenciaFim, long documentos, long itensPendentes, long itensAprovados) {

		static ContratoResponse de(Contrato c) {
			return new ContratoResponse(c.id(), c.numero(), c.unidadeId(), c.unidadeNome(), c.objeto(),
					c.poderConcedente(), c.regulador(), c.dataAssinatura(), c.vigenciaFim(), c.documentos(),
					c.itensPendentes(), c.itensAprovados());
		}
	}

	record DocumentoResponse(UUID id, UUID contratoId, TipoDocumento tipo, String titulo, String nomeArquivo,
			String sha256, long tamanhoBytes, @Nullable Integer paginas, SituacaoDocumento situacao,
			@Nullable String mensagem, @Nullable String modeloIa, int itensPropostos, int itensDescartados,
			Instant enviadoEm) {

		static DocumentoResponse de(Documento d) {
			return new DocumentoResponse(d.id(), d.contratoId(), d.tipo(), d.titulo(), d.nomeArquivo(), d.sha256(),
					d.tamanhoBytes(), d.paginas(), d.situacao(), d.erro(), d.modeloIa(), d.itensPropostos(),
					d.itensDescartados(), d.enviadoEm());
		}
	}

	record DetalheContratoResponse(ContratoResponse contrato, List<DocumentoResponse> documentos) {
	}

	record ContratoRequest(@NotBlank @Size(max = 60) String numero, @NotNull UUID unidadeId,
			@Nullable @Size(max = 500) String objeto, @Nullable @Size(max = 200) String poderConcedente,
			@Nullable @Size(max = 200) String regulador, @Nullable LocalDate dataAssinatura,
			@Nullable LocalDate vigenciaFim) {
	}

	record ItemResponse(UUID id, UUID contratoId, UUID documentoId, TipoItem tipo, String titulo, String resumo,
			@Nullable String clausula, int pagina, String trecho, @Nullable BigDecimal valorReferencia,
			@Nullable String unidadeMedida, @Nullable LocalDate dataLimite, @Nullable String periodicidade,
			@Nullable UUID unidadeId, @Nullable String unidadeNome, @Nullable BigDecimal valorAlerta,
			@Nullable LocalDate vigenciaInicio, StatusItem status, String origem, @Nullable String modeloIa,
			@Nullable BigDecimal confianca, @Nullable JsonNode propostaIa, boolean semReferencia, Instant propostoEm,
			@Nullable String revisadoPor, @Nullable Instant revisadoEm, @Nullable String motivoRevisao) {
	}

	record ItemRequest(@NotNull TipoItem tipo, @NotBlank @Size(max = 200) String titulo,
			@NotBlank @Size(max = 2000) String resumo, @Nullable @Size(max = 40) String clausula, @Min(1) int pagina,
			@NotBlank @Size(max = 4000) String trecho,
			@Nullable @DecimalMin("-1000000000") @DecimalMax("1000000000") BigDecimal valorReferencia,
			@Nullable @Size(max = 40) String unidadeMedida, @Nullable LocalDate dataLimite,
			@Nullable @Size(max = 60) String periodicidade, @Nullable UUID unidadeId,
			@Nullable @DecimalMin("0") @DecimalMax("100") BigDecimal valorAlerta, @Nullable LocalDate vigenciaInicio) {

		DadosItem dados() {
			return new DadosItem(tipo, titulo, resumo, clausula, pagina, trecho, valorReferencia, unidadeMedida,
					dataLimite, periodicidade, unidadeId, valorAlerta, vigenciaInicio);
		}
	}

	record NovoItemRequest(@NotNull UUID documentoId, @NotNull @Valid ItemRequest item) {
	}

	record RejeicaoRequest(@NotBlank @Size(max = 500) String motivo) {
	}

	record Criado(UUID id) {
	}

	private final ContratoService contratos;

	private final RevisaoService revisao;

	private final JsonMapper json;

	ContratoController(ContratoService contratos, RevisaoService revisao, JsonMapper json) {
		this.contratos = contratos;
		this.revisao = revisao;
		this.json = json;
	}

	@GetMapping
	List<ContratoResponse> listar() {
		return contratos.listar().stream().map(ContratoResponse::de).toList();
	}

	@PostMapping
	@ExigePermissao(GERENCIAR)
	ResponseEntity<Criado> criar(@Valid @RequestBody ContratoRequest pedido) {
		var id = contratos.criar(new DadosContrato(pedido.numero(), pedido.unidadeId(), pedido.objeto(),
				pedido.poderConcedente(), pedido.regulador(), pedido.dataAssinatura(), pedido.vigenciaFim()));
		return ResponseEntity.created(URI.create("/api/v1/contratos/" + id)).body(new Criado(id));
	}

	@GetMapping("/{id}")
	DetalheContratoResponse detalhar(@PathVariable UUID id) {
		var detalhe = contratos.detalhar(id);
		return new DetalheContratoResponse(ContratoResponse.de(detalhe.contrato()),
				detalhe.documentos().stream().map(DocumentoResponse::de).toList());
	}

	/**
	 * A1: envia o PDF; a leitura e a extração seguem em segundo plano (acompanhar pelo
	 * documento).
	 */
	@PostMapping(path = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ExigePermissao(GERENCIAR)
	ResponseEntity<Criado> enviar(@PathVariable UUID id, @RequestPart("arquivo") MultipartFile arquivo,
			@RequestParam(defaultValue = "ORIGINAL") TipoDocumento tipo,
			@RequestParam Optional<@Size(max = 200) String> titulo) throws IOException {
		var nome = Optional.ofNullable(arquivo.getOriginalFilename()).orElse("contrato.pdf");
		var documento = contratos.enviarDocumento(id, tipo, titulo.filter(t -> !t.isBlank()).orElse(nome), nome,
				arquivo.getBytes());
		return ResponseEntity.created(URI.create("/api/v1/contratos/documentos/" + documento))
			.body(new Criado(documento));
	}

	@GetMapping("/documentos/{documento}")
	DocumentoResponse documento(@PathVariable UUID documento) {
		return DocumentoResponse.de(contratos.buscarDocumento(documento));
	}

	/** O PDF, para abrir na página citada (A4). */
	@GetMapping("/documentos/{documento}/arquivo")
	ResponseEntity<byte[]> arquivo(@PathVariable UUID documento) {
		var dados = contratos.buscarDocumento(documento);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.cacheControl(CacheControl.noStore().cachePrivate())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(dados.nomeArquivo()).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(contratos.lerArquivo(documento));
	}

	@PostMapping("/documentos/{documento}/processamento")
	@ExigePermissao(GERENCIAR)
	ResponseEntity<Void> reprocessar(@PathVariable UUID documento) {
		contratos.reprocessar(documento);
		return ResponseEntity.accepted().build();
	}

	@GetMapping("/{id}/itens")
	List<ItemResponse> itens(@PathVariable UUID id, @RequestParam Optional<StatusItem> status) {
		return revisao.listar(id, status.orElse(null)).stream().map(this::item).toList();
	}

	@PostMapping("/{id}/itens")
	@ExigePermissao(GERENCIAR)
	ResponseEntity<Criado> criarItem(@PathVariable UUID id, @Valid @RequestBody NovoItemRequest pedido) {
		var item = revisao.criarManual(id, pedido.documentoId(), pedido.item().dados());
		return ResponseEntity.created(URI.create("/api/v1/contratos/itens/" + item)).body(new Criado(item));
	}

	@PutMapping("/itens/{item}")
	@ExigePermissao(GERENCIAR)
	ResponseEntity<Void> editarItem(@PathVariable UUID item, @Valid @RequestBody ItemRequest pedido) {
		revisao.editar(item, pedido.dados());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/itens/{item}/aprovacao")
	@ExigePermissao(GERENCIAR)
	ResponseEntity<Void> aprovar(@PathVariable UUID item) {
		revisao.aprovar(item);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/itens/{item}/rejeicao")
	@ExigePermissao(GERENCIAR)
	ResponseEntity<Void> rejeitar(@PathVariable UUID item, @Valid @RequestBody RejeicaoRequest pedido) {
		revisao.rejeitar(item, pedido.motivo());
		return ResponseEntity.noContent().build();
	}

	private ItemResponse item(ItemContratual i) {
		var d = i.dados();
		return new ItemResponse(i.id(), i.contratoId(), i.documentoId(), d.tipo(), d.titulo(), d.resumo(), d.clausula(),
				d.pagina(), d.trecho(), d.valorReferencia(), d.unidadeMedida(), d.dataLimite(), d.periodicidade(),
				d.unidadeId(), i.unidadeNome(), d.valorAlerta(), d.vigenciaInicio(), i.status(), i.origem(),
				i.modeloIa(), i.confianca(), i.propostaIa() == null ? null : json.readTree(i.propostaIa()),
				d.semReferencia(), i.propostoEm(), i.revisadoPor(), i.revisadoEm(), i.motivoRevisao());
	}

}
