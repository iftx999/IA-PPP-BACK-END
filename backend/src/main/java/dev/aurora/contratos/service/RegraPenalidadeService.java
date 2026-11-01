package dev.aurora.contratos.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.contratos.FormulaPenalidade;
import dev.aurora.contratos.RegraDePenalidade;
import dev.aurora.contratos.RegrasDePenalidade;
import dev.aurora.contratos.model.DadosRegra;
import dev.aurora.contratos.model.ItemContratual;
import dev.aurora.contratos.model.StatusItem;
import dev.aurora.contratos.model.TipoItem;
import dev.aurora.contratos.repository.ContratoRepository;
import dev.aurora.contratos.repository.ItemRepository;
import dev.aurora.contratos.repository.RegraPenalidadeRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Penalidades calculáveis (ADR 0035): quem gerencia contratos transforma uma penalidade
 * aprovada numa regra com fórmula e valores; o simulador usa a regra para estimar a
 * multa. Definir e remover são auditados (sem a base em reais, que é dado financeiro do
 * cliente).
 */
@Service
public class RegraPenalidadeService implements RegrasDePenalidade {

	/** Limite de sanidade dos valores em reais (evita erro de digitação de ordens). */
	static final BigDecimal VALOR_MAXIMO = new BigDecimal("1000000000000");

	private final RegraPenalidadeRepository regras;

	private final ItemRepository itens;

	private final ContratoRepository contratos;

	private final Auditoria auditoria;

	RegraPenalidadeService(RegraPenalidadeRepository regras, ItemRepository itens, ContratoRepository contratos,
			Auditoria auditoria) {
		this.regras = regras;
		this.itens = itens;
		this.contratos = contratos;
		this.auditoria = auditoria;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RegraDePenalidade> aferidasNa(UUID unidadeId) {
		return regras.listarAferidasNa(unidadeId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<RegraDePenalidade> visiveis() {
		return regras.listarTodas();
	}

	@Transactional(readOnly = true)
	public List<RegraDePenalidade> doContrato(UUID contratoId) {
		contratos.buscar(contratoId).orElseThrow(RecursoNaoEncontradoException::new);
		return regras.listarDoContrato(contratoId);
	}

	@Transactional
	public RegraDePenalidade definir(UUID itemId, DadosRegra dados) {
		var item = itens.travar(itemId).orElseThrow(RecursoNaoEncontradoException::new);
		exigirPenalidadeAprovada(item);
		validar(dados);
		var normalizados = normalizar(dados);
		regras.salvar(itemId, normalizados, ContextoAcesso.exigir().usuarioId());
		var detalhes = detalhes(item);
		detalhes.put("formula", normalizados.formula().name());
		detalhes.put("periodicidade", normalizados.periodicidade().name());
		auditoria.registrar(TipoEvento.REGRA_DE_PENALIDADE_DEFINIDA, "item-contratual/" + itemId, detalhes);
		return regras.buscar(itemId).orElseThrow();
	}

	@Transactional
	public void remover(UUID itemId) {
		var item = itens.travar(itemId).orElseThrow(RecursoNaoEncontradoException::new);
		if (!regras.remover(itemId)) {
			throw new RecursoNaoEncontradoException();
		}
		auditoria.registrar(TipoEvento.REGRA_DE_PENALIDADE_REMOVIDA, "item-contratual/" + itemId, detalhes(item));
	}

	private static void exigirPenalidadeAprovada(ItemContratual item) {
		if (item.dados().tipo() != TipoItem.PENALIDADE) {
			throw new ConflitoException("Só itens do tipo penalidade podem ter regra de cálculo.");
		}
		if (item.status() != StatusItem.APROVADO) {
			throw new ConflitoException("Aprove a penalidade antes de definir como ela é calculada.");
		}
	}

	static void validar(DadosRegra d) {
		var percentual = d.formula() == FormulaPenalidade.PERCENTUAL_DA_BASE_POR_PONTO;
		if (d.valor().signum() <= 0) {
			throw new RegraVioladaException("O valor da penalidade deve ser maior que zero.");
		}
		if (percentual && d.valor().compareTo(BigDecimal.valueOf(100)) > 0) {
			throw new RegraVioladaException("O percentual por ponto deve ser de no máximo 100%.");
		}
		if (!percentual && d.valor().compareTo(VALOR_MAXIMO) > 0) {
			throw new RegraVioladaException("Valor em reais acima do permitido.");
		}
		if (percentual && (d.baseValor() == null || d.baseValor().signum() <= 0)) {
			throw new RegraVioladaException("Informe a base em reais sobre a qual o percentual incide.");
		}
		if (d.baseValor() != null && d.baseValor().compareTo(VALOR_MAXIMO) > 0) {
			throw new RegraVioladaException("Base em reais acima do permitido.");
		}
		if (d.teto() != null && (d.teto().signum() <= 0 || d.teto().compareTo(VALOR_MAXIMO) > 0)) {
			throw new RegraVioladaException("O teto deve ser maior que zero.");
		}
	}

	/** Fórmulas sem base descartam base e descrição enviadas por engano. */
	private static DadosRegra normalizar(DadosRegra d) {
		if (d.formula() == FormulaPenalidade.PERCENTUAL_DA_BASE_POR_PONTO) {
			var descricao = d.baseDescricao() == null || d.baseDescricao().isBlank() ? null : d.baseDescricao().strip();
			return new DadosRegra(d.formula(), d.valor(), d.baseValor(), descricao, d.contagem(), d.periodicidade(),
					d.teto());
		}
		return new DadosRegra(d.formula(), d.valor(), null, null, d.contagem(), d.periodicidade(), d.teto());
	}

	private static LinkedHashMap<String, @Nullable Object> detalhes(ItemContratual item) {
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("contrato", item.contratoId().toString());
		detalhes.put("clausula", item.dados().clausula());
		return detalhes;
	}

}
