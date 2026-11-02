package dev.aurora.acesso.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.acesso.ProvisionamentoDeCliente;
import dev.aurora.acesso.model.NovaConcessao;
import dev.aurora.acesso.repository.PerfilRepository;
import dev.aurora.acesso.repository.UsuarioRepository;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.unidades.Unidades;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Provisionamento de um cliente novo (S1, ADR 0027), equivalente ao
 * {@code provisionar-cliente.sh}: raiz, perfis do sistema e primeiro administrador.
 */
@Service
class ProvisionamentoService implements ProvisionamentoDeCliente {

	static final String ADMINISTRADOR = "Administrador do tenant";

	/**
	 * Perfis do sistema: o cliente não edita; cria os seus a partir do catálogo (RN-16).
	 */
	record PerfilDoSistema(String nome, String descricao, List<String> permissoes) {
	}

	static final List<PerfilDoSistema> PERFIS = List.of(
			new PerfilDoSistema(ADMINISTRADOR, "Usuários, perfis, unidades e integrações",
					List.of("assistente:usar", "campanha:gerenciar", "contrato:gerenciar", "contrato:ler",
							"dados_pessoais:ler", "indicador:gerenciar", "indicador:ler", "integracao:gerenciar",
							"mapa:marcar", "os:executar", "relatorio:exportar", "unidade:gerenciar", "unidade:ler",
							"usuario:gerenciar")),
			new PerfilDoSistema("Auditor", "Leitura da trilha de auditoria", List.of("auditoria:ler", "unidade:ler")),
			new PerfilDoSistema("Diretor", "Leitura de todos os indicadores no escopo",
					List.of("assistente:usar", "contrato:ler", "indicador:ler", "relatorio:exportar", "unidade:ler")),
			new PerfilDoSistema("Gestor comercial", "Indicadores e dados comerciais no escopo",
					List.of("assistente:usar", "campanha:gerenciar", "contrato:ler", "dados_pessoais:ler",
							"indicador:ler", "mapa:marcar", "unidade:ler")),
			// ADR 0036: a matrícula identifica o imóvel no campo.
			new PerfilDoSistema("Equipe de campo", "Ordens de serviço das equipes de que faz parte",
					List.of("dados_pessoais:ler", "os:executar", "unidade:ler")));

	private final PerfilRepository perfis;

	private final UsuarioRepository usuarios;

	private final Unidades unidades;

	private final ContextosDoSistema contextos;

	private final GestaoUsuarioService gestao;

	private final TransactionTemplate transacao;

	ProvisionamentoService(PerfilRepository perfis, UsuarioRepository usuarios, Unidades unidades,
			ContextosDoSistema contextos, GestaoUsuarioService gestao, TransactionTemplate transacao) {
		this.perfis = perfis;
		this.usuarios = usuarios;
		this.unidades = unidades;
		this.contextos = contextos;
		this.gestao = gestao;
		this.transacao = transacao;
	}

	@Override
	public boolean provisionar(UUID tenantId, String nomeCliente, String nomeAdministrador, String emailAdministrador) {
		var estrutura = executar(ContextoAcesso.somenteTenant(tenantId), () -> transacao.execute(status -> {
			var raiz = unidades.garantirRaiz(nomeCliente);
			for (var perfil : PERFIS) {
				if (perfis.buscarIdPorNome(perfil.nome()).isEmpty()) {
					var id = UUID.randomUUID();
					perfis.inserirDoSistema(id, perfil.nome(), perfil.descricao());
					perfis.substituirPermissoes(id, perfil.permissoes());
				}
			}
			var administrador = perfis.buscarIdPorNome(ADMINISTRADOR).orElseThrow();
			return Map.entry(raiz, administrador);
		}));
		// Contexto do sistema: o tenant já tem raiz, então ele abrange o tenant inteiro
		// (exigido pelo convite).
		var sistema = contextos.doTenant(tenantId)
			.orElseThrow(() -> new IllegalStateException("Cliente inativo ou sem raiz: " + tenantId));
		return executar(sistema, () -> {
			if (usuarios.buscarIdPorEmail(emailAdministrador).isPresent()) {
				return false;
			}
			gestao.convidar(new GestaoUsuarioService.DadosConvite(nomeAdministrador, emailAdministrador,
					List.of(new NovaConcessao(estrutura.getValue(), estrutura.getKey(), null))));
			return true;
		});
	}

	@Override
	public void reenviarConviteDoAdministrador(UUID tenantId, String emailAdministrador) {
		var sistema = contextos.doTenant(tenantId).orElseThrow(() -> new IllegalStateException("Cliente inativo"));
		executar(sistema, () -> {
			var id = usuarios.buscarIdPorEmail(emailAdministrador)
				.orElseThrow(dev.aurora.comum.RecursoNaoEncontradoException::new);
			gestao.reenviarConvite(id);
			return null;
		});
	}

	@Override
	public long contarUsuariosAtivos(UUID tenantId) {
		return executar(ContextoAcesso.somenteTenant(tenantId), usuarios::contarAtivos);
	}

	private static <T> T executar(ContextoAcesso contexto, java.util.concurrent.Callable<T> acao) {
		try {
			return ContextoAcesso.executar(contexto, acao);
		}
		catch (RuntimeException erro) {
			throw erro;
		}
		catch (Exception erro) {
			throw new IllegalStateException(erro);
		}
	}

}
