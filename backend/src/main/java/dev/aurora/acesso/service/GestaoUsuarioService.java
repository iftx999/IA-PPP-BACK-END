package dev.aurora.acesso.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import dev.aurora.acesso.model.ConcessaoDoUsuario;
import dev.aurora.acesso.model.NovaConcessao;
import dev.aurora.acesso.model.SituacaoUsuario;
import dev.aurora.acesso.model.Usuario;
import dev.aurora.acesso.repository.PerfilRepository;
import dev.aurora.acesso.repository.TenantRepository;
import dev.aurora.acesso.repository.UsuarioRepository;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.unidades.Unidades;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usuários do tenant (H2, H7, H8, RN-16): convite, concessões perfil × unidade, ativação
 * e redefinição de senha. A conta (login e senha) vive no provedor de identidade; aqui
 * ficam o vínculo, o escopo e o ciclo de vida.
 */
@Service
public class GestaoUsuarioService {

	private static final String RECURSO = "usuario/";

	private static final String SEM_ADMINISTRADOR = "O tenant ficaria sem administrador: mantenha ao menos um "
			+ "usuário ativo com gestão de usuários na unidade principal.";

	private final UsuarioRepository usuarios;

	private final PerfilRepository perfis;

	private final TenantRepository tenants;

	private final Unidades unidades;

	private final ProvedorIdentidade identidade;

	private final Auditoria auditoria;

	private final Clock relogio;

	GestaoUsuarioService(UsuarioRepository usuarios, PerfilRepository perfis, TenantRepository tenants,
			Unidades unidades, ProvedorIdentidade identidade, Auditoria auditoria, Clock relogio) {
		this.usuarios = usuarios;
		this.perfis = perfis;
		this.tenants = tenants;
		this.unidades = unidades;
		this.identidade = identidade;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	public record DadosConvite(String nome, String email, List<NovaConcessao> concessoes) {
	}

	@Transactional(readOnly = true)
	public List<Usuario> listar() {
		EscopoAdministrativo.exigirTenantInteiro();
		var lista = usuarios.listar();
		var concessoes = usuarios.listarConcessoes(lista.stream().map(Usuario::id).toList());
		return lista.stream().map(u -> u.comConcessoes(concessoes.getOrDefault(u.id(), List.of()))).toList();
	}

	@Transactional(readOnly = true)
	public Usuario buscar(UUID id) {
		EscopoAdministrativo.exigirTenantInteiro();
		return comConcessoes(usuarios.buscar(id).orElseThrow(RecursoNaoEncontradoException::new));
	}

	/**
	 * Cria a conta no provedor, registra o usuário com as concessões e envia o convite.
	 * Se o envio falhar, nada é gravado aqui; a conta criada no provedor é reaproveitada
	 * na próxima tentativa.
	 */
	@Transactional
	public UUID convidar(DadosConvite dados) {
		var contexto = EscopoAdministrativo.exigirTenantInteiro();
		var email = dados.email().strip().toLowerCase(Locale.ROOT);
		var nome = dados.nome().strip();
		var concessoes = validar(dados.concessoes());
		if (concessoes.isEmpty()) {
			throw new RegraVioladaException("Informe ao menos um perfil e uma unidade para o convite.");
		}
		if (usuarios.existeEmail(email)) {
			throw new ConflitoException("Já existe um usuário com o e-mail " + email + ".");
		}

		var emissor = emissorDoTenant(contexto.tenantId());
		var sub = identidade.criarConta(emissor, email, nome);
		var id = UUID.randomUUID();
		try {
			usuarios.inserirConvidado(id, sub, email, nome);
		}
		catch (DuplicateKeyException erro) {
			throw new ConflitoException("A conta de " + email + " já está vinculada a outro usuário.");
		}
		usuarios.substituirConcessoes(id, concessoes, contexto.usuarioId());
		auditoria.registrar(TipoEvento.USUARIO_CONVIDADO, RECURSO + id, Map.of("concessoes", descrever(concessoes)));
		identidade.enviarConvite(emissor, sub);
		return id;
	}

	@Transactional
	public void reenviarConvite(UUID id) {
		var contexto = EscopoAdministrativo.exigirTenantInteiro();
		var usuario = usuarios.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (usuario.situacao() != SituacaoUsuario.CONVITE_PENDENTE) {
			throw new ConflitoException("Só é possível reenviar o convite de quem ainda não acessou.");
		}
		var emissor = emissorDoTenant(contexto.tenantId());
		var sub = usuario.subExterno();
		if (sub == null) {
			sub = identidade.criarConta(emissor, usuario.email(), usuario.nome());
			usuarios.vincularSub(id, sub);
		}
		usuarios.marcarConvidado(id);
		auditoria.registrar(TipoEvento.CONVITE_REENVIADO, RECURSO + id, Map.of());
		identidade.enviarConvite(emissor, sub);
	}

	/** Substitui as concessões do usuário (vale na próxima requisição dele). */
	@Transactional
	public void alterarConcessoes(UUID id, List<NovaConcessao> pedidas) {
		var contexto = EscopoAdministrativo.exigirTenantInteiro();
		var usuario = comConcessoes(usuarios.travar(id).orElseThrow(RecursoNaoEncontradoException::new));
		var concessoes = validar(pedidas);
		boolean continuaAdministrador = usuario.ativo() && concessoes.stream().anyMatch(usuarios::concedeAdministracao);
		if (!continuaAdministrador && perfis.contarAdministradores(null, false, id, null) == 0) {
			throw new ConflitoException(SEM_ADMINISTRADOR);
		}
		usuarios.substituirConcessoes(id, concessoes, contexto.usuarioId());
		auditoria.registrar(TipoEvento.ACESSO_ALTERADO, RECURSO + id,
				Map.of("antes", descreverAtuais(usuario.concessoes()), "depois", descrever(concessoes)));
	}

	/**
	 * Desativar bloqueia o login e encerra as sessões, preservando histórico e auditoria
	 * (S7).
	 */
	@Transactional
	public void definirAtivo(UUID id, boolean ativo) {
		var contexto = EscopoAdministrativo.exigirTenantInteiro();
		var usuario = usuarios.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (usuario.ativo() == ativo) {
			return;
		}
		if (!ativo) {
			if (id.equals(contexto.usuarioId())) {
				throw new ConflitoException("Você não pode desativar o próprio usuário.");
			}
			if (perfis.contarAdministradores(null, false, id, null) == 0) {
				throw new ConflitoException(SEM_ADMINISTRADOR);
			}
		}
		usuarios.definirAtivo(id, ativo);
		auditoria.registrar(ativo ? TipoEvento.USUARIO_REATIVADO : TipoEvento.USUARIO_DESATIVADO, RECURSO + id,
				Map.of());
		if (usuario.subExterno() != null) {
			identidade.definirAtiva(emissorDoTenant(contexto.tenantId()), usuario.subExterno(), ativo);
		}
	}

	@Transactional
	public void enviarRedefinicaoSenha(UUID id) {
		var contexto = EscopoAdministrativo.exigirTenantInteiro();
		var usuario = usuarios.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (usuario.situacao() != SituacaoUsuario.ATIVO || usuario.subExterno() == null) {
			throw new ConflitoException(usuario.situacao() == SituacaoUsuario.CONVITE_PENDENTE
					? "O convite ainda não foi aceito: reenvie o convite." : "O usuário está desativado.");
		}
		auditoria.registrar(TipoEvento.REDEFINICAO_SENHA_ENVIADA, RECURSO + id, Map.of());
		identidade.enviarRedefinicaoSenha(emissorDoTenant(contexto.tenantId()), usuario.subExterno());
	}

	/**
	 * Perfil e unidade precisam existir no tenant (unidade ativa); repetições são
	 * descartadas.
	 */
	private List<NovaConcessao> validar(Collection<NovaConcessao> pedidas) {
		var agora = OffsetDateTime.now(relogio);
		var unicas = new LinkedHashSet<NovaConcessao>();
		var vistas = new LinkedHashSet<String>();
		for (var concessao : pedidas) {
			if (!perfis.buscar(concessao.perfilId()).isPresent()) {
				throw new RegraVioladaException("Perfil não encontrado: " + concessao.perfilId());
			}
			if (unidades.buscarNoEscopo(concessao.unidadeId()).isEmpty()) {
				throw new RegraVioladaException("Unidade não encontrada ou inativa: " + concessao.unidadeId());
			}
			if (concessao.validoAte() != null && !concessao.validoAte().isAfter(agora)) {
				throw new RegraVioladaException("A validade da concessão precisa ser futura.");
			}
			if (vistas.add(concessao.perfilId() + "@" + concessao.unidadeId())) {
				unicas.add(concessao);
			}
		}
		return List.copyOf(unicas);
	}

	private Usuario comConcessoes(Usuario usuario) {
		return usuario
			.comConcessoes(usuarios.listarConcessoes(List.of(usuario.id())).getOrDefault(usuario.id(), List.of()));
	}

	private String emissorDoTenant(UUID tenantId) {
		return tenants.buscarEmissor(tenantId)
			.orElseThrow(() -> new IllegalStateException("tenant sem emissor de identidade: " + tenantId));
	}

	/** Só identificadores na trilha: nada de nome ou e-mail (LGPD). */
	private static List<String> descrever(Collection<NovaConcessao> concessoes) {
		return concessoes.stream()
			.map(c -> c.perfilId() + "@" + c.unidadeId() + (c.validoAte() == null ? "" : " ate " + c.validoAte()))
			.toList();
	}

	private static List<String> descreverAtuais(Collection<ConcessaoDoUsuario> concessoes) {
		return descrever(
				concessoes.stream().map(c -> new NovaConcessao(c.perfilId(), c.unidadeId(), c.validoAte())).toList());
	}

}
