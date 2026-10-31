package dev.aurora.contratos.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.contratos.PrazoContratual;
import dev.aurora.contratos.PrazosContratuais;
import dev.aurora.contratos.model.StatusItem;
import dev.aurora.contratos.model.TipoItem;
import dev.aurora.contratos.repository.ContratoRepository;
import dev.aurora.contratos.repository.ItemRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prazos contratuais (ADR 0024): o que o alerta de prazo acompanha e o cumprimento
 * informado por quem gere o contrato (auditado).
 */
@Service
public class PrazoService implements PrazosContratuais {

	private final ItemRepository itens;

	private final ContratoRepository contratos;

	private final Auditoria auditoria;

	PrazoService(ItemRepository itens, ContratoRepository contratos, Auditoria auditoria) {
		this.itens = itens;
		this.contratos = contratos;
		this.auditoria = auditoria;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PrazoContratual> pendentes(LocalDate hoje, LocalDate itensAte, LocalDate contratosAte) {
		var prazos = new ArrayList<>(itens.listarPrazosPendentes(itensAte));
		prazos.addAll(contratos.listarVigenciasTerminando(hoje, contratosAte));
		return prazos;
	}

	/**
	 * Marca ou desmarca o prazo como cumprido. Só itens PRAZO aprovados; repetir o mesmo
	 * estado é conflito.
	 */
	@Transactional
	public void definirCumprimento(UUID itemId, boolean cumprido) {
		var item = itens.travar(itemId).orElseThrow(RecursoNaoEncontradoException::new);
		if (item.dados().tipo() != TipoItem.PRAZO || item.status() != StatusItem.APROVADO) {
			throw new RegraVioladaException("Só prazos aprovados podem ser marcados como cumpridos.");
		}
		if (!itens.definirCumprimento(itemId, cumprido, ContextoAcesso.exigir().usuarioId())) {
			throw new ConflitoException(
					cumprido ? "O prazo já está cumprido." : "O prazo não está marcado como cumprido.");
		}
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("contrato", item.contratoId().toString());
		detalhes.put("clausula", item.dados().clausula());
		detalhes.put("dataLimite", item.dados().dataLimite() == null ? null : item.dados().dataLimite().toString());
		auditoria.registrar(cumprido ? TipoEvento.PRAZO_CONTRATUAL_CUMPRIDO : TipoEvento.PRAZO_CONTRATUAL_REABERTO,
				"item-contratual/" + itemId, detalhes);
	}

}
