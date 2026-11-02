package dev.aurora.campanhas.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.model.DadosEquipe;
import dev.aurora.campanhas.model.Equipe;
import dev.aurora.campanhas.model.Pessoa;
import dev.aurora.campanhas.model.TipoEquipe;
import dev.aurora.campanhas.repository.EquipeRepository;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Equipes de campo (ADR 0036): própria ou terceirizada, com a unidade onde atua e as
 * pessoas que recebem as OS. Cadastro e alteração são auditados.
 */
@Service
public class EquipeService {

	static final int MAXIMO_DE_MEMBROS = 50;

	private final EquipeRepository equipes;

	private final Unidades unidades;

	private final Auditoria auditoria;

	EquipeService(EquipeRepository equipes, Unidades unidades, Auditoria auditoria) {
		this.equipes = equipes;
		this.unidades = unidades;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<Equipe> listar() {
		var lista = equipes.listar();
		var membros = equipes.membros(lista.stream().map(Equipe::id).toList())
			.stream()
			.collect(Collectors.groupingBy(EquipeRepository.MembroDaEquipe::equipeId,
					Collectors.mapping(EquipeRepository.MembroDaEquipe::membro, Collectors.toList())));
		return lista.stream().map(e -> e.comMembros(membros.getOrDefault(e.id(), List.of()))).toList();
	}

	@Transactional(readOnly = true)
	public List<Pessoa> buscarPessoas(String busca) {
		var termo = busca.strip();
		return termo.length() < 2 ? List.of() : equipes.buscarPessoas(termo);
	}

	@Transactional
	public Equipe cadastrar(DadosEquipe dados) {
		var id = UUID.randomUUID();
		var normalizados = validar(dados, id);
		equipes.inserir(id, normalizados);
		equipes.substituirMembros(id, normalizados.membros());
		auditoria.registrar(TipoEvento.EQUIPE_CADASTRADA, "equipe/" + id, detalhes(normalizados));
		return buscar(id);
	}

	@Transactional
	public Equipe alterar(UUID id, DadosEquipe dados) {
		if (!equipes.travar(id)) {
			throw new RecursoNaoEncontradoException();
		}
		var atual = equipes.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		var normalizados = validar(dados, id);
		if (atual.ativa() && !normalizados.ativa() && atual.ordensEmCampo() > 0) {
			throw new ConflitoException("A equipe tem " + atual.ordensEmCampo()
					+ " OS em campo. Envie-as a outra equipe antes de desativá-la.");
		}
		equipes.atualizar(id, normalizados);
		equipes.substituirMembros(id, normalizados.membros());
		auditoria.registrar(TipoEvento.EQUIPE_ALTERADA, "equipe/" + id, detalhes(normalizados));
		return buscar(id);
	}

	private Equipe buscar(UUID id) {
		var equipe = equipes.buscar(id).orElseThrow();
		return equipe
			.comMembros(equipes.membros(List.of(id)).stream().map(EquipeRepository.MembroDaEquipe::membro).toList());
	}

	private DadosEquipe validar(DadosEquipe dados, UUID id) {
		unidades.buscarNoEscopo(dados.unidadeId())
			.orElseThrow(() -> new RegraVioladaException("Escolha uma unidade onde você gerencia campanhas."));
		var nome = dados.nome().strip().replaceAll("\\s+", " ");
		if (nome.length() < 2) {
			throw new RegraVioladaException("Informe o nome da equipe.");
		}
		if (equipes.nomeEmUso(nome, id)) {
			throw new ConflitoException("Já existe uma equipe com esse nome.");
		}
		@Nullable String empresa = dados.empresa() == null ? null : dados.empresa().strip();
		if (dados.tipo() == TipoEquipe.TERCEIRIZADA && (empresa == null || empresa.length() < 2)) {
			throw new RegraVioladaException("Informe a empresa da equipe terceirizada.");
		}
		if (dados.tipo() == TipoEquipe.PROPRIA) {
			empresa = null;
		}
		var membros = List.copyOf(new LinkedHashSet<>(dados.membros()));
		if (membros.size() > MAXIMO_DE_MEMBROS) {
			throw new RegraVioladaException("Uma equipe tem no máximo " + MAXIMO_DE_MEMBROS + " pessoas.");
		}
		if (equipes.contarUsuariosAtivos(membros) != membros.size()) {
			throw new RegraVioladaException("Só pessoas com acesso ativo podem entrar na equipe.");
		}
		return new DadosEquipe(dados.unidadeId(), nome, dados.tipo(), empresa, dados.ativa(), membros);
	}

	private static LinkedHashMap<String, @Nullable Object> detalhes(DadosEquipe dados) {
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("nome", dados.nome());
		detalhes.put("tipo", dados.tipo().name());
		detalhes.put("empresa", dados.empresa());
		detalhes.put("unidade", dados.unidadeId().toString());
		detalhes.put("ativa", dados.ativa());
		detalhes.put("membros", dados.membros().size());
		return detalhes;
	}

}
