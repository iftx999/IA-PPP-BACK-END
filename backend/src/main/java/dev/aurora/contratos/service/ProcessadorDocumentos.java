package dev.aurora.contratos.service;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.contratos.model.Pagina;
import dev.aurora.contratos.model.SituacaoDocumento;
import dev.aurora.contratos.repository.ContratoRepository;
import dev.aurora.contratos.repository.ItemRepository;
import dev.aurora.unidades.Unidades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pipeline do documento (doc 05, 5.3): texto por página → segmentos por cláusula → IA →
 * validação → itens propostos. Roda fora da requisição, no contexto de acesso de quem
 * enviou, com uma transação curta por etapa (a chamada à IA fica fora de transação).
 */
@Service
public class ProcessadorDocumentos {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessadorDocumentos.class);

	static final String SEM_TEXTO = "O PDF não tem texto selecionável (parece digitalizado). Envie a versão com "
			+ "texto ou cadastre os itens manualmente; o reconhecimento de texto (OCR) ainda não está disponível.";

	static final String IA_DESLIGADA = "A extração automática não está configurada neste ambiente. O texto foi lido; "
			+ "cadastre os itens manualmente.";

	private final ContratoRepository contratos;

	private final ItemRepository itens;

	private final Unidades unidades;

	private final LeitorPdf leitor;

	private final ExtratorContrato extrator;

	private final TransactionTemplate transacao;

	private final Executor executor;

	private final JsonMapper json;

	ProcessadorDocumentos(ContratoRepository contratos, ItemRepository itens, Unidades unidades, LeitorPdf leitor,
			ExtratorContrato extrator, TransactionTemplate transacao,
			@Qualifier("applicationTaskExecutor") Executor executor, JsonMapper json) {
		this.contratos = contratos;
		this.itens = itens;
		this.unidades = unidades;
		this.leitor = leitor;
		this.extrator = extrator;
		this.transacao = transacao;
		this.executor = executor;
		this.json = json;
	}

	/** Agenda o processamento no contexto de acesso atual (tenant, usuário, escopo). */
	public void agendar(UUID documento) {
		var contexto = ContextoAcesso.exigir();
		executor.execute(() -> {
			try {
				ContextoAcesso.executar(contexto, () -> {
					processar(documento);
					return null;
				});
			}
			catch (Exception erro) {
				LOG.error("Falha ao processar o documento {}", documento, erro);
			}
		});
	}

	void processar(UUID documentoId) {
		try {
			executarEtapas(documentoId);
		}
		catch (RuntimeException erro) {
			LOG.error("Falha inesperada ao processar o documento {}", documentoId, erro);
			emTransacao(() -> {
				contratos.definirSituacao(documentoId, SituacaoDocumento.FALHA,
						"Falha inesperada no processamento. Tente reprocessar; se persistir, acione o suporte.", null);
				return null;
			});
		}
	}

	private void executarEtapas(UUID documentoId) {
		var preparado = emTransacao(() -> {
			var documento = contratos.travarDocumento(documentoId).orElse(null);
			if (documento == null || documento.situacao() != SituacaoDocumento.RECEBIDO) {
				return null;
			}
			contratos.iniciarProcessamento(documentoId);
			itens.substituirPropostos(documentoId);
			return new Preparado(documento.contratoId(), contratos.lerArquivo(documentoId).orElseThrow());
		});
		if (preparado == null) {
			return;
		}

		List<Pagina> paginas;
		try {
			paginas = leitor.lerPaginas(preparado.pdf());
		}
		catch (LeitorPdf.PdfInvalidoException erro) {
			situacao(documentoId, SituacaoDocumento.FALHA, "Não foi possível ler o PDF: " + erro.getMessage(), null);
			return;
		}
		emTransacao(() -> {
			contratos.salvarPaginas(documentoId, paginas);
			return null;
		});
		if (paginas.stream().allMatch(p -> p.texto().isBlank())) {
			situacao(documentoId, SituacaoDocumento.SEM_TEXTO, SEM_TEXTO, null);
			return;
		}
		if (!extrator.disponivel()) {
			emTransacao(() -> {
				contratos.concluir(documentoId, 0, 0, null, IA_DESLIGADA);
				return null;
			});
			return;
		}
		situacao(documentoId, SituacaoDocumento.PROCESSANDO, null, null);

		var contrato = emTransacao(() -> contratos.buscar(preparado.contratoId()).orElseThrow());
		var doContrato = emTransacao(() -> unidades.subarvore(contrato.unidadeId()));
		var porNome = new HashMap<String, UUID>();
		for (var u : doContrato) {
			porNome.put(ValidadorPropostas.normalizar(u.nome()), u.id());
			porNome.put(ValidadorPropostas.normalizar(u.codigo()), u.id());
		}

		dev.aurora.contratos.model.ResultadoExtracao resultado;
		try {
			resultado = extrator.extrair(Segmentador.segmentar(paginas),
					doContrato.stream().map(dev.aurora.unidades.Unidade::nome).toList());
		}
		catch (ExtratorContrato.ExtracaoFalhouException erro) {
			situacao(documentoId, SituacaoDocumento.FALHA, erro.getMessage(), null);
			return;
		}

		var validacao = ValidadorPropostas.validar(resultado.propostas(), paginas, porNome);
		var aviso = resultado.observacoes().isEmpty() ? null : String.join(" ", resultado.observacoes());
		emTransacao(() -> {
			for (var item : validacao.validos()) {
				itens.inserir(UUID.randomUUID(), preparado.contratoId(), documentoId, item.dados(), "IA",
						resultado.modelo(), resultado.versaoPrompt(), item.confianca(),
						json.writeValueAsString(item.original()));
			}
			contratos.concluir(documentoId, validacao.validos().size(), validacao.descartados(), resultado.modelo(),
					aviso);
			return null;
		});
		LOG.info("Documento {} processado: {} itens propostos, {} descartados", documentoId, validacao.validos().size(),
				validacao.descartados());
	}

	private record Preparado(UUID contratoId, byte[] pdf) {
	}

	private void situacao(UUID documento, SituacaoDocumento situacao, @org.jspecify.annotations.Nullable String erro,
			@org.jspecify.annotations.Nullable String modelo) {
		emTransacao(() -> {
			contratos.definirSituacao(documento, situacao, erro, modelo);
			return null;
		});
	}

	private <T> T emTransacao(Supplier<T> acao) {
		return transacao.execute(status -> acao.get());
	}

}
