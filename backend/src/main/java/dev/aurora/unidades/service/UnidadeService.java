package dev.aurora.unidades.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.unidades.Unidade;
import dev.aurora.unidades.Unidades;
import dev.aurora.unidades.model.DadosUnidade;
import dev.aurora.unidades.model.TipoUnidade;
import dev.aurora.unidades.model.UnidadeAdministrada;
import dev.aurora.unidades.repository.UnidadeRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unidades do escopo (H1): consulta para os demais módulos e cadastro da hierarquia. Toda
 * operação vale só dentro do escopo de quem a faz, inclusive o pai de destino.
 */
@Service
public class UnidadeService implements Unidades {

	private static final String RECURSO = "unidade/";

	private static final Pattern CODIGO = Pattern.compile("[A-Z0-9][A-Z0-9_.-]{0,39}");

	private static final Pattern IBGE = Pattern.compile("\\d{7}");

	private final UnidadeRepository unidades;

	private final Auditoria auditoria;

	UnidadeService(UnidadeRepository unidades, Auditoria auditoria) {
		this.unidades = unidades;
		this.auditoria = auditoria;
	}

	public List<Unidade> listar() {
		return unidades.listarNoEscopo();
	}

	/** Unidade de outro tenant ou fora do escopo é tratada como inexistente. */
	public Unidade buscar(UUID id) {
		return unidades.buscarNoEscopo(id).orElseThrow(RecursoNaoEncontradoException::new);
	}

	@Override
	public Optional<Unidade> buscarNoEscopo(UUID id) {
		return unidades.buscarNoEscopo(id);
	}

	@Override
	public Optional<Unidade> principalDoEscopo() {
		return unidades.buscarPrincipalDoEscopo();
	}

	@Override
	public Optional<String> caminhoNoEscopo(UUID id) {
		return unidades.buscarCaminhoNoEscopo(id);
	}

	/** Árvore do escopo para a administração, com as inativas. */
	@Transactional(readOnly = true)
	public List<UnidadeAdministrada> arvore() {
		return unidades.listarParaGestao();
	}

	@Transactional
	public UUID criar(TipoUnidade tipo, UUID paiId, DadosUnidade pedido) {
		if (tipo.tiposPai().isEmpty()) {
			throw new RegraVioladaException("O grupo é a raiz do tenant e é criado no cadastro do tenant.");
		}
		var pai = unidades.buscarParaGestao(paiId)
			.orElseThrow(() -> new RegraVioladaException("Unidade de cima não encontrada no seu escopo."));
		exigirPaiValido(tipo, pai);
		var dados = normalizar(tipo, pedido);
		var id = UUID.randomUUID();
		try {
			unidades.inserir(id, tipo, paiId, dados);
		}
		catch (DuplicateKeyException erro) {
			throw codigoRepetido(dados);
		}
		auditoria.registrar(TipoEvento.UNIDADE_CRIADA, RECURSO + id,
				Map.of("tipo", tipo.name(), "codigo", dados.codigo(), "pai", paiId.toString()));
		return id;
	}

	@Transactional
	public void alterar(UUID id, DadosUnidade pedido) {
		var atual = buscarParaGestao(id);
		var dados = normalizar(atual.tipo(), pedido);
		try {
			unidades.atualizar(id, dados);
		}
		catch (DuplicateKeyException erro) {
			throw codigoRepetido(dados);
		}
		var detalhes = new LinkedHashMap<String, Object>();
		detalhes.put("codigo", dados.codigo());
		if (!atual.nome().equals(dados.nome())) {
			detalhes.put("nomeAnterior", atual.nome());
			detalhes.put("nome", dados.nome());
		}
		auditoria.registrar(TipoEvento.UNIDADE_ALTERADA, RECURSO + id, detalhes);
	}

	/**
	 * Muda a unidade de lugar na árvore, levando as descendentes. Metas herdadas e
	 * escopos de acesso passam a seguir o novo pai.
	 */
	@Transactional
	public void mover(UUID id, UUID novoPaiId) {
		unidades.travarSubarvore(id);
		var unidade = buscarParaGestao(id);
		if (unidade.raiz()) {
			throw new RegraVioladaException("O grupo é a raiz do tenant e não pode ser movido.");
		}
		if (novoPaiId.equals(unidade.unidadePaiId())) {
			return;
		}
		if (!unidade.ativo()) {
			throw new ConflitoException("Reative a unidade antes de movê-la.");
		}
		var novoPai = unidades.buscarParaGestao(novoPaiId)
			.orElseThrow(() -> new RegraVioladaException("Unidade de destino não encontrada no seu escopo."));
		if (unidades.estaNaSubarvore(novoPaiId, id)) {
			throw new RegraVioladaException("Não é possível mover uma unidade para dentro dela mesma.");
		}
		exigirPaiValido(unidade.tipo(), novoPai);
		int reescritas = unidades.mover(id, novoPaiId);
		auditoria.registrar(TipoEvento.UNIDADE_MOVIDA, RECURSO + id, Map.of("de",
				String.valueOf(unidade.unidadePaiId()), "para", novoPaiId.toString(), "unidades", reescritas));
	}

	/**
	 * Inativar preserva histórico (medições, metas, imóveis); a unidade some das telas e
	 * dos escopos.
	 */
	@Transactional
	public void definirAtiva(UUID id, boolean ativa) {
		unidades.travarSubarvore(id);
		var unidade = buscarParaGestao(id);
		if (unidade.ativo() == ativa) {
			return;
		}
		if (unidade.raiz()) {
			throw new RegraVioladaException("O grupo é a raiz do tenant e não pode ser inativado.");
		}
		if (ativa) {
			var pai = buscarParaGestao(unidade.unidadePaiId());
			if (!pai.ativo()) {
				throw new ConflitoException("Reative primeiro a unidade de cima (" + pai.nome() + ").");
			}
		}
		else {
			if (unidade.filhasAtivas() > 0) {
				throw new ConflitoException("Há " + plural(unidade.filhasAtivas(), "unidade ativa", "unidades ativas")
						+ " abaixo desta. Inative-as ou mova-as antes.");
			}
			if (unidade.acessos() > 0) {
				throw new ConflitoException("A unidade é escopo de "
						+ plural(unidade.acessos(), "concessão de acesso", "concessões de acesso")
						+ ". Retire-as em Usuários antes de inativar.");
			}
		}
		unidades.definirAtiva(id, ativa);
		auditoria.registrar(ativa ? TipoEvento.UNIDADE_REATIVADA : TipoEvento.UNIDADE_INATIVADA, RECURSO + id,
				Map.of("codigo", unidade.codigo()));
	}

	private UnidadeAdministrada buscarParaGestao(UUID id) {
		return unidades.buscarParaGestao(id).orElseThrow(RecursoNaoEncontradoException::new);
	}

	private static void exigirPaiValido(TipoUnidade tipo, UnidadeAdministrada pai) {
		if (!pai.ativo()) {
			throw new RegraVioladaException("A unidade de cima (" + pai.nome() + ") está inativa.");
		}
		if (!tipo.aceitaPai(pai.tipo())) {
			throw new RegraVioladaException(
					tipo.descricao() + " não pode ficar abaixo de " + pai.tipo().descricao() + ".");
		}
	}

	/** Código em maiúsculas e sem espaços nas pontas; IBGE só para município. */
	static DadosUnidade normalizar(TipoUnidade tipo, DadosUnidade dados) {
		var codigo = dados.codigo().strip().toUpperCase(Locale.ROOT);
		if (!CODIGO.matcher(codigo).matches()) {
			throw new RegraVioladaException(
					"Código inválido: use até 40 letras, números, ponto, hífen ou sublinhado, sem espaços.");
		}
		var ibge = dados.codigoIbge() == null || dados.codigoIbge().isBlank() ? null : dados.codigoIbge().strip();
		if (tipo.exigeCodigoIbge() && (ibge == null || !IBGE.matcher(ibge).matches())) {
			throw new RegraVioladaException("Município exige o código IBGE com 7 dígitos.");
		}
		if (!tipo.exigeCodigoIbge() && ibge != null) {
			throw new RegraVioladaException("Só município tem código IBGE.");
		}
		if ((dados.latitude() == null) != (dados.longitude() == null)) {
			throw new RegraVioladaException("Informe latitude e longitude juntas, ou nenhuma das duas.");
		}
		return new DadosUnidade(codigo, dados.nome().strip(), ibge, dados.latitude(), dados.longitude());
	}

	private static ConflitoException codigoRepetido(DadosUnidade dados) {
		return new ConflitoException("Já existe uma unidade com o código " + dados.codigo() + ".");
	}

	private static String plural(long quantidade, String singular, String plural) {
		return quantidade + " " + (quantidade == 1 ? singular : plural);
	}

}
