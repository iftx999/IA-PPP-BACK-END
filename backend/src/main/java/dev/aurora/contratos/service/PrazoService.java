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
import dev.aurora.contratos.Recorrencia;
import dev.aurora.contratos.model.AgendaDePrazo;
import dev.aurora.contratos.model.ItemContratual;
import dev.aurora.contratos.model.StatusItem;
import dev.aurora.contratos.repository.ContratoRepository;
import dev.aurora.contratos.repository.ItemRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prazos contratuais (ADR 0024, ADR 0026): o que o alerta de prazo acompanha, a agenda de
 * vencimentos e o cumprimento informado por quem gere o contrato (auditados).
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
	 * Marca como cumprido o vencimento atual (o mais antigo em aberto); o acompanhamento
	 * passa ao seguinte, se o item se repete.
	 */
	@Transactional
	public AgendaDePrazo cumprir(UUID itemId) {
		var item = comAgenda(itemId);
		var vencimento = item.agenda()
			.proximoVencimento()
			.orElseThrow(() -> new ConflitoException("Não há vencimento em aberto neste item."));
		itens.registrarCumprimento(itemId, vencimento, ContextoAcesso.exigir().usuarioId());
		auditar(TipoEvento.PRAZO_CONTRATUAL_CUMPRIDO, item, vencimento);
		return comAgenda(itemId).agenda();
	}

	/** Desfaz o último cumprimento informado (ex.: marcado por engano). */
	@Transactional
	public AgendaDePrazo reabrir(UUID itemId) {
		var item = comAgenda(itemId);
		var ultimo = item.agenda()
			.ultimoCumprimento()
			.orElseThrow(() -> new ConflitoException("Nenhum vencimento deste item está marcado como cumprido."));
		itens.removerCumprimento(itemId, ultimo.vencimento());
		auditar(TipoEvento.PRAZO_CONTRATUAL_REABERTO, item, ultimo.vencimento());
		return comAgenda(itemId).agenda();
	}

	/**
	 * Define quando o prazo ou a obrigação vence e se repete (ADR 0026). Não altera o
	 * conteúdo aprovado: {@code primeiroVencimento} nulo volta a usar a data limite do
	 * item.
	 */
	@Transactional
	public AgendaDePrazo definirAgenda(UUID itemId, @Nullable LocalDate primeiroVencimento,
			@Nullable Recorrencia recorrencia) {
		var item = aprovadoComVencimento(itemId);
		if (primeiroVencimento == null && item.dados().dataLimite() == null) {
			throw new RegraVioladaException("Informe a data do primeiro vencimento.");
		}
		var primeiro = primeiroVencimento == null ? item.dados().dataLimite() : primeiroVencimento;
		var contrato = contratos.buscar(item.contratoId()).orElseThrow(RecursoNaoEncontradoException::new);
		if (recorrencia != null && contrato.vigenciaFim() != null && primeiro.isAfter(contrato.vigenciaFim())) {
			throw new RegraVioladaException("O primeiro vencimento é depois do fim da vigência do contrato.");
		}
		itens.definirAgenda(itemId, primeiroVencimento, recorrencia);
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("contrato", item.contratoId().toString());
		detalhes.put("clausula", item.dados().clausula());
		detalhes.put("primeiroVencimento", primeiro.toString());
		detalhes.put("recorrencia", recorrencia == null ? null : recorrencia.name());
		auditoria.registrar(TipoEvento.AGENDA_DE_PRAZO_DEFINIDA, "item-contratual/" + itemId, detalhes);
		return comAgenda(itemId).agenda();
	}

	private ItemContratual aprovadoComVencimento(UUID itemId) {
		var item = itens.travar(itemId).orElseThrow(RecursoNaoEncontradoException::new);
		if (!ItemContratual.temVencimento(item.dados().tipo()) || item.status() != StatusItem.APROVADO) {
			throw new RegraVioladaException("Só prazos e obrigações aprovados têm vencimento a cumprir.");
		}
		return item;
	}

	private ComAgenda comAgenda(UUID itemId) {
		var item = aprovadoComVencimento(itemId);
		if (item.agenda() == null) {
			throw new RegraVioladaException("Defina primeiro quando o item vence.");
		}
		return new ComAgenda(item, item.agenda());
	}

	private record ComAgenda(ItemContratual item, AgendaDePrazo agenda) {

		UUID contratoId() {
			return item.contratoId();
		}

		@Nullable String clausula() {
			return item.dados().clausula();
		}

	}

	private void auditar(TipoEvento tipo, ComAgenda item, LocalDate vencimento) {
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("contrato", item.contratoId().toString());
		detalhes.put("clausula", item.clausula());
		detalhes.put("vencimento", vencimento.toString());
		auditoria.registrar(tipo, "item-contratual/" + item.item().id(), detalhes);
	}

}
