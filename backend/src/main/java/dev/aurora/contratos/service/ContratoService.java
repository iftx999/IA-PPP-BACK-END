package dev.aurora.contratos.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.contratos.model.Contrato;
import dev.aurora.contratos.model.DadosContrato;
import dev.aurora.contratos.model.Documento;
import dev.aurora.contratos.model.SituacaoDocumento;
import dev.aurora.contratos.model.TipoDocumento;
import dev.aurora.contratos.repository.ContratoRepository;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Contratos e envio de documentos (A1). O processamento do PDF segue em segundo plano.
 */
@Service
public class ContratoService {

	private static final String RECURSO = "contrato/";

	private static final byte[] ASSINATURA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	/**
	 * Documento parado no meio do processamento há mais que isso pode ser reprocessado.
	 */
	static final Duration PROCESSAMENTO_PARADO = Duration.ofMinutes(15);

	private final ContratoRepository contratos;

	private final Unidades unidades;

	private final LeitorPdf leitor;

	private final ProcessadorDocumentos processador;

	private final Auditoria auditoria;

	private final Clock relogio;

	private final long tamanhoMaximo;

	ContratoService(ContratoRepository contratos, Unidades unidades, LeitorPdf leitor,
			ProcessadorDocumentos processador, Auditoria auditoria, Clock relogio,
			@Value("${aurora.contratos.tamanho-maximo-bytes}") long tamanhoMaximo) {
		this.contratos = contratos;
		this.unidades = unidades;
		this.leitor = leitor;
		this.processador = processador;
		this.auditoria = auditoria;
		this.relogio = relogio;
		this.tamanhoMaximo = tamanhoMaximo;
	}

	public record DetalheContrato(Contrato contrato, List<Documento> documentos) {
	}

	@Transactional(readOnly = true)
	public List<Contrato> listar() {
		return contratos.listar();
	}

	@Transactional(readOnly = true)
	public DetalheContrato detalhar(UUID id) {
		var contrato = contratos.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		return new DetalheContrato(contrato, contratos.listarDocumentos(id));
	}

	@Transactional
	public UUID criar(DadosContrato pedido) {
		var unidade = unidades.buscarNoEscopo(pedido.unidadeId())
			.orElseThrow(() -> new RegraVioladaException("Unidade não encontrada no seu escopo."));
		var dados = new DadosContrato(pedido.numero().strip(), unidade.id(), limpo(pedido.objeto()),
				limpo(pedido.poderConcedente()), limpo(pedido.regulador()), pedido.dataAssinatura(),
				pedido.vigenciaFim());
		if (dados.dataAssinatura() != null && dados.vigenciaFim() != null
				&& dados.vigenciaFim().isBefore(dados.dataAssinatura())) {
			throw new RegraVioladaException("O fim da vigência não pode ser antes da assinatura.");
		}
		var id = UUID.randomUUID();
		try {
			contratos.inserir(id, dados);
		}
		catch (DuplicateKeyException erro) {
			throw new ConflitoException("Já existe o contrato " + dados.numero() + ".");
		}
		auditoria.registrar(TipoEvento.CONTRATO_CRIADO, RECURSO + id,
				Map.of("numero", dados.numero(), "unidade", unidade.id().toString()));
		return id;
	}

	/**
	 * A1: valida o PDF (tipo, tamanho, integridade), guarda o arquivo com o SHA-256 e
	 * agenda a leitura e a extração, que começam depois que o envio é gravado.
	 */
	@Transactional
	public UUID enviarDocumento(UUID contratoId, TipoDocumento tipo, String titulo, String nomeArquivo,
			byte[] conteudo) {
		var contrato = contratos.buscar(contratoId).orElseThrow(RecursoNaoEncontradoException::new);
		if (conteudo.length == 0) {
			throw new RegraVioladaException("O arquivo está vazio.");
		}
		if (conteudo.length > tamanhoMaximo) {
			throw new RegraVioladaException("O arquivo passa do limite de " + (tamanhoMaximo / (1024 * 1024)) + " MB.");
		}
		if (!comecaCom(conteudo, ASSINATURA_PDF)) {
			throw new RegraVioladaException("O arquivo não é um PDF.");
		}
		int paginas;
		try {
			paginas = leitor.contarPaginas(conteudo);
		}
		catch (LeitorPdf.PdfInvalidoException erro) {
			throw new RegraVioladaException("Não foi possível abrir o PDF: " + erro.getMessage());
		}
		var sha256 = sha256(conteudo);
		var id = UUID.randomUUID();
		var nome = nomeArquivo.isBlank() ? "contrato.pdf" : nomeArquivo.strip();
		try {
			contratos.inserirDocumento(id, contrato.id(), tipo, titulo.strip(), nome, sha256, conteudo.length, paginas,
					ContextoAcesso.exigir().usuarioId(), conteudo);
		}
		catch (DuplicateKeyException erro) {
			throw new ConflitoException("Este arquivo já foi enviado para o contrato " + contrato.numero() + ".");
		}
		auditoria.registrar(TipoEvento.DOCUMENTO_CONTRATO_ENVIADO, "documento/" + id,
				Map.of("contrato", contrato.id().toString(), "tipo", tipo.name(), "paginas", paginas, "sha256", sha256,
						"bytes", conteudo.length));
		agendarDepoisDeGravar(id);
		return id;
	}

	/**
	 * Refaz a leitura e a extração (falha, PDF sem texto, IA ligada depois, ou
	 * processamento interrompido). Propostas não revisadas são substituídas; aprovadas
	 * ficam.
	 */
	@Transactional
	public void reprocessar(UUID documentoId) {
		var documento = contratos.travarDocumento(documentoId).orElseThrow(RecursoNaoEncontradoException::new);
		if (documento.situacao().emAndamento() && !parado(documento)) {
			throw new ConflitoException("O documento ainda está sendo processado.");
		}
		contratos.definirSituacao(documentoId, SituacaoDocumento.RECEBIDO, null, null);
		agendarDepoisDeGravar(documentoId);
	}

	@Transactional(readOnly = true)
	public Documento buscarDocumento(UUID id) {
		return contratos.buscarDocumento(id).orElseThrow(RecursoNaoEncontradoException::new);
	}

	@Transactional(readOnly = true)
	public byte[] lerArquivo(UUID documentoId) {
		buscarDocumento(documentoId);
		return contratos.lerArquivo(documentoId).orElseThrow(RecursoNaoEncontradoException::new);
	}

	private boolean parado(Documento documento) {
		var desde = documento.processamentoIniciadoEm() != null ? documento.processamentoIniciadoEm()
				: documento.enviadoEm();
		return desde.plus(PROCESSAMENTO_PARADO).isBefore(relogio.instant());
	}

	private void agendarDepoisDeGravar(UUID documento) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				processador.agendar(documento);
			}
		});
	}

	private static boolean comecaCom(byte[] conteudo, byte[] prefixo) {
		return conteudo.length >= prefixo.length && Arrays.equals(Arrays.copyOf(conteudo, prefixo.length), prefixo);
	}

	private static String sha256(byte[] conteudo) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
		}
		catch (NoSuchAlgorithmException erro) {
			throw new IllegalStateException(erro);
		}
	}

	private static @Nullable String limpo(@Nullable String texto) {
		return texto == null || texto.isBlank() ? null : texto.strip();
	}

}
