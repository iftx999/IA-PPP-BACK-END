package dev.aurora.contratos.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.contratos.model.Contrato;
import dev.aurora.contratos.model.DadosItem;
import dev.aurora.contratos.model.ItemContratual;
import dev.aurora.contratos.model.Proposta;
import dev.aurora.contratos.model.StatusItem;
import dev.aurora.contratos.model.TipoDocumento;
import dev.aurora.contratos.model.TipoItem;
import dev.aurora.contratos.repository.ContratoRepository;
import dev.aurora.contratos.repository.ItemRepository;
import dev.aurora.metas.MetaDoContrato;
import dev.aurora.metas.Metas;
import dev.aurora.metas.ResultadoAditivo;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revisão humana dos itens (A3, RN-10): editar, aprovar ou rejeitar. Nada vira meta antes
 * da aprovação; item sem cláusula não é aprovado; aprovação e rejeição vão para a
 * auditoria.
 */
@Service
public class RevisaoService {

	private static final BigDecimal CEM = BigDecimal.valueOf(100);

	private final ItemRepository itens;

	private final ContratoRepository contratos;

	private final Unidades unidades;

	private final Metas metas;

	private final Auditoria auditoria;

	private final JsonMapper json;

	RevisaoService(ItemRepository itens, ContratoRepository contratos, Unidades unidades, Metas metas,
			Auditoria auditoria, JsonMapper json) {
		this.json = json;
		this.itens = itens;
		this.contratos = contratos;
		this.unidades = unidades;
		this.metas = metas;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<ItemContratual> listar(UUID contratoId, @Nullable StatusItem status) {
		contratos.buscar(contratoId).orElseThrow(RecursoNaoEncontradoException::new);
		return itens.listar(contratoId, status);
	}

	/**
	 * Item cadastrado à mão (IA desligada ou item que ela não achou); também passa por
	 * aprovação.
	 */
	@Transactional
	public UUID criarManual(UUID contratoId, UUID documentoId, DadosItem dados) {
		var contrato = contratos.buscar(contratoId).orElseThrow(RecursoNaoEncontradoException::new);
		var documento = contratos.buscarDocumento(documentoId)
			.filter(d -> d.contratoId().equals(contratoId))
			.orElseThrow(() -> new RegraVioladaException("Documento não pertence a este contrato."));
		var limpos = validar(contrato, dados, documento.paginas());
		var id = UUID.randomUUID();
		itens.inserir(id, contratoId, documentoId, limpos, "MANUAL", null, null, null, null);
		return id;
	}

	/** Edita um item pendente; a proposta original da IA continua guardada. */
	@Transactional
	public void editar(UUID itemId, DadosItem dados) {
		var item = pendente(itemId);
		var contrato = contratos.buscar(item.contratoId()).orElseThrow(RecursoNaoEncontradoException::new);
		var paginas = contratos.buscarDocumento(item.documentoId()).orElseThrow().paginas();
		itens.atualizar(itemId, validar(contrato, dados, paginas));
	}

	@Transactional
	public void aprovar(UUID itemId) {
		var item = pendente(itemId);
		var dados = item.dados();
		var contrato = contratos.buscar(item.contratoId()).orElseThrow(RecursoNaoEncontradoException::new);
		if (dados.semReferencia()) {
			throw new RegraVioladaException("Item sem referência: informe a cláusula antes de aprovar.");
		}
		UUID meta = null;
		ResultadoAditivo aditivo = null;
		if (dados.tipo() == TipoItem.META) {
			var documento = contratos.buscarDocumento(item.documentoId()).orElseThrow();
			if (documento.tipo() == TipoDocumento.ADITIVO) {
				aditivo = metas.aditarDoContrato(metaDe(contrato, item));
				meta = aditivo.metaId();
			}
			else {
				meta = metas.definirDoContrato(metaDe(contrato, item));
			}
		}
		itens.revisar(itemId, StatusItem.APROVADO, ContextoAcesso.exigir().usuarioId(), null);
		var detalhes = detalhes(item);
		if (aditivo != null) {
			// O item antigo deixa de valer (a meta dele foi encerrada ou substituída).
			aditivo.itensSubstituidos().forEach(antigo -> itens.marcarSubstituido(antigo, itemId));
			detalhes.put("aditivo", true);
			detalhes.put("metasEncerradas", aditivo.encerradas());
			detalhes.put("metasSubstituidas", aditivo.substituidas());
		}
		detalhes.put("editado", editado(item));
		if (meta != null) {
			detalhes.put("meta", meta.toString());
		}
		auditoria.registrar(TipoEvento.ITEM_CONTRATUAL_APROVADO, "item-contratual/" + itemId, detalhes);
	}

	@Transactional
	public void rejeitar(UUID itemId, String motivo) {
		var item = pendente(itemId);
		var limpo = motivo.strip();
		if (limpo.length() < 3) {
			throw new RegraVioladaException("Informe o motivo da rejeição.");
		}
		itens.revisar(itemId, StatusItem.REJEITADO, ContextoAcesso.exigir().usuarioId(), limpo);
		var detalhes = detalhes(item);
		detalhes.put("motivo", limpo);
		auditoria.registrar(TipoEvento.ITEM_CONTRATUAL_REJEITADO, "item-contratual/" + itemId, detalhes);
	}

	private ItemContratual pendente(UUID itemId) {
		var item = itens.travar(itemId).orElseThrow(RecursoNaoEncontradoException::new);
		if (item.status() != StatusItem.PROPOSTO) {
			throw new ConflitoException("O item já foi revisado.");
		}
		return item;
	}

	/**
	 * Meta do contrato: unidade dentro do contrato, meta e alerta em %, início de
	 * vigência.
	 */
	private MetaDoContrato metaDe(Contrato contrato, ItemContratual item) {
		var d = item.dados();
		var faltando = new ArrayList<String>();
		if (d.unidadeId() == null) {
			faltando.add("unidade");
		}
		if (d.valorReferencia() == null) {
			faltando.add("meta (%)");
		}
		if (d.valorAlerta() == null) {
			faltando.add("limite de alerta (%)");
		}
		if (d.vigenciaInicio() == null) {
			faltando.add("início da vigência");
		}
		if (!faltando.isEmpty()) {
			throw new RegraVioladaException("Para aprovar uma meta, informe: " + String.join(", ", faltando) + ".");
		}
		var origem = "Contrato " + contrato.numero() + ", cláusula " + d.clausula() + ", p. " + d.pagina();
		return new MetaDoContrato(d.unidadeId(), d.valorReferencia(), d.valorAlerta(), d.vigenciaInicio(),
				d.dataLimite(), item.id(), origem);
	}

	/** Regras comuns a item manual e edição. */
	private DadosItem validar(Contrato contrato, DadosItem d, @Nullable Integer paginasDoDocumento) {
		if (paginasDoDocumento != null && d.pagina() > paginasDoDocumento) {
			throw new RegraVioladaException("O documento tem " + paginasDoDocumento + " páginas.");
		}
		if (d.unidadeId() != null
				&& unidades.subarvore(contrato.unidadeId()).stream().noneMatch(u -> u.id().equals(d.unidadeId()))) {
			throw new RegraVioladaException("A unidade precisa ser coberta pelo contrato e estar no seu escopo.");
		}
		if (d.tipo() == TipoItem.META) {
			if (foraDeFaixa(d.valorReferencia()) || foraDeFaixa(d.valorAlerta())) {
				throw new RegraVioladaException("Meta e limite de alerta são percentuais de 0 a 100.");
			}
			if (d.valorReferencia() != null && d.valorAlerta() != null
					&& d.valorAlerta().compareTo(d.valorReferencia()) > 0) {
				throw new RegraVioladaException("O limite de alerta não pode ser maior que a meta.");
			}
			if (d.vigenciaInicio() != null && d.dataLimite() != null && d.dataLimite().isBefore(d.vigenciaInicio())) {
				throw new RegraVioladaException("O fim da vigência não pode ser antes do início.");
			}
		}
		var clausula = d.clausula() == null || d.clausula().isBlank() ? null : d.clausula().strip();
		return new DadosItem(d.tipo(), d.titulo().strip(), d.resumo().strip(), clausula, d.pagina(), d.trecho().strip(),
				d.valorReferencia(), d.unidadeMedida(), d.dataLimite(), d.periodicidade(), d.unidadeId(),
				d.valorAlerta(), d.vigenciaInicio());
	}

	private static boolean foraDeFaixa(@Nullable BigDecimal valor) {
		return valor != null && (valor.signum() < 0 || valor.compareTo(CEM) > 0);
	}

	/**
	 * O item aprovado difere do que a IA propôs (para a auditoria); a unidade não entra,
	 * pois a IA a cita por nome.
	 */
	boolean editado(ItemContratual item) {
		if (item.propostaIa() == null) {
			return false;
		}
		var p = json.readValue(item.propostaIa(), Proposta.class);
		var d = item.dados();
		return !p.tipo().equalsIgnoreCase(d.tipo().name()) || !p.titulo().strip().equals(d.titulo())
				|| !Objects.equals(vazioComoNulo(p.clausula()), d.clausula()) || p.pagina() != d.pagina()
				|| !p.trecho().strip().equals(d.trecho()) || !mesmoNumero(p.valorReferencia(), d.valorReferencia())
				|| !mesmoNumero(p.valorAlerta(), d.valorAlerta()) || !Objects.equals(p.dataLimite(), d.dataLimite())
				|| !Objects.equals(p.vigenciaInicio(), d.vigenciaInicio());
	}

	private static boolean mesmoNumero(@Nullable BigDecimal a, @Nullable BigDecimal b) {
		return a == null ? b == null : b != null && a.compareTo(b) == 0;
	}

	private static @Nullable String vazioComoNulo(@Nullable String texto) {
		return texto == null || texto.isBlank() ? null : texto.strip();
	}

	private static LinkedHashMap<String, @Nullable Object> detalhes(ItemContratual item) {
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("contrato", item.contratoId().toString());
		detalhes.put("documento", item.documentoId().toString());
		detalhes.put("tipo", item.dados().tipo().name());
		detalhes.put("clausula", item.dados().clausula());
		detalhes.put("pagina", item.dados().pagina());
		detalhes.put("origem", item.origem());
		if (item.dados().unidadeId() != null) {
			detalhes.put("unidade", item.dados().unidadeId().toString());
		}
		return detalhes;
	}

}
