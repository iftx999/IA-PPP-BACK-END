package dev.aurora.acesso.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.aurora.acesso.model.Perfil;
import dev.aurora.acesso.model.Permissao;
import dev.aurora.acesso.repository.PerfilRepository;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Perfis e permissões (H6, RN-16). Perfis valem para o tenant inteiro. */
@Service
public class PerfilService {

	private static final String RECURSO = "perfil/";

	private final PerfilRepository perfis;

	private final Auditoria auditoria;

	PerfilService(PerfilRepository perfis, Auditoria auditoria) {
		this.perfis = perfis;
		this.auditoria = auditoria;
	}

	public record DadosPerfil(String nome, @Nullable String descricao, Set<String> permissoes) {
	}

	@Transactional(readOnly = true)
	public List<Permissao> catalogo() {
		return perfis.listarCatalogo();
	}

	@Transactional(readOnly = true)
	public List<Perfil> listar() {
		return perfis.listar();
	}

	@Transactional
	public UUID criar(DadosPerfil dados) {
		EscopoAdministrativo.exigirTenantInteiro();
		validarPermissoes(dados.permissoes());
		var id = UUID.randomUUID();
		try {
			perfis.inserir(id, dados.nome().strip(), dados.descricao());
		}
		catch (DuplicateKeyException erro) {
			throw new ConflitoException("Já existe um perfil chamado " + dados.nome().strip() + ".");
		}
		perfis.substituirPermissoes(id, new TreeSet<>(dados.permissoes()));
		auditoria.registrar(TipoEvento.PERFIL_CRIADO, RECURSO + id,
				Map.of("nome", dados.nome().strip(), "permissoes", List.copyOf(new TreeSet<>(dados.permissoes()))));
		return id;
	}

	@Transactional
	public void alterar(UUID id, DadosPerfil dados) {
		EscopoAdministrativo.exigirTenantInteiro();
		var atual = perfis.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (atual.sistema()) {
			throw new ConflitoException("Perfis do sistema não podem ser alterados. Duplique-o para personalizar.");
		}
		validarPermissoes(dados.permissoes());
		boolean administra = dados.permissoes().contains(Permissao.GERIR_USUARIOS);
		if (!administra && perfis.contarAdministradores(id, false, null, null) == 0) {
			throw new ConflitoException(
					"O tenant ficaria sem administrador: mantenha a permissão de gerenciar usuários.");
		}
		try {
			perfis.atualizar(id, dados.nome().strip(), dados.descricao());
		}
		catch (DuplicateKeyException erro) {
			throw new ConflitoException("Já existe um perfil chamado " + dados.nome().strip() + ".");
		}
		perfis.substituirPermissoes(id, new TreeSet<>(dados.permissoes()));
		auditoria.registrar(TipoEvento.PERFIL_ALTERADO, RECURSO + id,
				Map.of("antes", atual.permissoes(), "depois", List.copyOf(new TreeSet<>(dados.permissoes()))));
	}

	@Transactional
	public void remover(UUID id) {
		EscopoAdministrativo.exigirTenantInteiro();
		var atual = perfis.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (atual.sistema()) {
			throw new ConflitoException("Perfis do sistema não podem ser removidos.");
		}
		if (atual.usuarios() > 0) {
			throw new ConflitoException(
					"Perfil em uso por " + atual.usuarios() + (atual.usuarios() == 1 ? " usuário." : " usuários.")
							+ " Retire-o dos usuários antes de remover.");
		}
		perfis.remover(id);
		auditoria.registrar(TipoEvento.PERFIL_REMOVIDO, RECURSO + id, Map.of("nome", atual.nome()));
	}

	private void validarPermissoes(Set<String> pedidas) {
		var catalogo = perfis.listarCatalogo().stream().map(Permissao::codigo).collect(Collectors.toSet());
		var desconhecidas = new TreeSet<>(pedidas);
		desconhecidas.removeAll(catalogo);
		if (!desconhecidas.isEmpty()) {
			throw new RegraVioladaException("Permissões desconhecidas: " + String.join(", ", desconhecidas));
		}
	}

}
