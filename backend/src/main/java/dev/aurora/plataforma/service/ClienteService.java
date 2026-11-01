package dev.aurora.plataforma.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import dev.aurora.acesso.ProvisionamentoDeCliente;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.comum.TriagemDeArquivos;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.contexto.ContextoPlataforma;
import dev.aurora.plataforma.model.Cliente;
import dev.aurora.plataforma.model.Limites;
import dev.aurora.plataforma.model.Logo;
import dev.aurora.plataforma.repository.ClienteRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Clientes da plataforma (S1, ADR 0027). Criar um cliente reserva o código, cria o realm
 * no provedor de identidade e provisiona raiz, perfis e o primeiro administrador. As
 * etapas são idempotentes: se uma falhar, "concluir provisionamento" retoma de onde
 * parou.
 *
 * <p>
 * Nenhum método abre transação longa: o cadastro roda no contexto de plataforma e o que
 * acontece dentro do cliente, no contexto dele (cada um com a sua conexão e as suas
 * regras de RLS).
 */
@Service
public class ClienteService {

	private static final Logger LOG = LoggerFactory.getLogger(ClienteService.class);

	static final Pattern CODIGO = Pattern.compile("^[a-z0-9](-?[a-z0-9])+$");

	/** Nomes de realm do próprio Keycloak e da Aurora. */
	static final Set<String> RESERVADOS = Set.of("master", "plataforma", "admin", "aurora-plataforma", "api", "publico",
			"corporativo");

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	static final int TAMANHO_MAXIMO_LOGO = 256 * 1024;

	private final ClienteRepository clientes;

	private final ProvedorDeRealms realms;

	private final ProvisionamentoDeCliente provisionamento;

	private final Auditoria auditoria;

	private final TransactionTemplate transacao;

	private final TriagemDeArquivos triagem;

	ClienteService(ClienteRepository clientes, ProvedorDeRealms realms, ProvisionamentoDeCliente provisionamento,
			Auditoria auditoria, TransactionTemplate transacao, TriagemDeArquivos triagem) {
		this.triagem = triagem;
		this.clientes = clientes;
		this.realms = realms;
		this.provisionamento = provisionamento;
		this.auditoria = auditoria;
		this.transacao = transacao;
	}

	/** Cliente com a contagem de usuários ativos (lida dentro de cada cliente). */
	public record Resumo(Cliente cliente, long usuariosAtivos) {
	}

	public record NovoCliente(String codigo, String nome, String contatoNome, String contatoEmail, Limites limites) {
	}

	public record Alteracao(String nome, boolean ativo, Limites limites) {
	}

	public List<Resumo> listar() {
		ContextoPlataforma.exigir();
		return clientes.listar().stream().map(c -> new Resumo(c, usuariosAtivos(c))).toList();
	}

	public Resumo buscar(UUID id) {
		ContextoPlataforma.exigir();
		var cliente = clientes.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		return new Resumo(cliente, usuariosAtivos(cliente));
	}

	/**
	 * Reserva o código e provisiona. Falha no provedor de identidade deixa o cliente
	 * criado e com o provisionamento pendente (a tela oferece concluir).
	 */
	public Resumo criar(NovoCliente pedido) {
		var plataforma = ContextoPlataforma.exigir();
		var codigo = obrigatorio(pedido.codigo(), "Informe o código do cliente.", 40).toLowerCase(Locale.ROOT);
		if (codigo.length() < 3 || !CODIGO.matcher(codigo).matches()) {
			throw new RegraVioladaException("O código deve ter de 3 a 40 letras minúsculas, números ou hífens (ex.: "
					+ "grupo-saneamento-x), sem hífen no início ou no fim.");
		}
		if (RESERVADOS.contains(codigo)) {
			throw new RegraVioladaException("O código " + codigo + " é reservado. Escolha outro.");
		}
		var nome = obrigatorio(pedido.nome(), "Informe o nome do cliente.", 120);
		var contatoNome = obrigatorio(pedido.contatoNome(), "Informe o nome do primeiro administrador.", 120);
		var contatoEmail = obrigatorio(pedido.contatoEmail(), "Informe o e-mail do primeiro administrador.", 254)
			.toLowerCase(Locale.ROOT);
		if (!EMAIL.matcher(contatoEmail).matches()) {
			throw new RegraVioladaException("E-mail do primeiro administrador inválido.");
		}
		var limites = validar(pedido.limites());
		var emissor = realms.emissor(codigo);
		if (clientes.existeCodigo(codigo) || clientes.clienteDoEmissor(emissor).isPresent() || realms.existe(codigo)) {
			throw new ConflitoException("O código " + codigo + " já está em uso. Escolha outro.");
		}

		var id = UUID.randomUUID();
		emPlataforma(() -> {
			clientes.inserir(id, codigo, nome, contatoNome, contatoEmail);
			clientes.atualizar(id, nome, true, limites.usuarios());
			clientes.definirLimitesDoAssistente(id, limites);
			// O emissor é gravado antes do realm existir: numa nova tentativa, o realm
			// com esse nome é deste
			// cliente.
			clientes.inserirEmissor(id, emissor);
			return null;
		});
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("codigo", codigo);
		detalhes.put("limiteUsuarios", limites.usuarios());
		detalhes.put("operador", plataforma.operador());
		noCliente(id, () -> {
			auditoria.registrar(TipoEvento.CLIENTE_CRIADO, "tenant/" + id, detalhes);
			return null;
		});
		LOG.info("Cliente {} criado ({}); provisionando", id, codigo);
		try {
			concluirProvisionamento(id);
		}
		catch (RuntimeException erro) {
			LOG.warn("Provisionamento do cliente {} ficou pendente: {}", id, erro.getMessage());
			throw erro;
		}
		return buscar(id);
	}

	/**
	 * Realm, raiz, perfis do sistema e convite do primeiro administrador. Idempotente.
	 */
	public Resumo concluirProvisionamento(UUID id) {
		ContextoPlataforma.exigir();
		var cliente = clientes.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		var emissor = realms.emissor(cliente.codigo());
		if (!emissor.equals(cliente.emissor())) {
			throw new ConflitoException("Este cliente foi criado fora da administração da plataforma (emissor "
					+ cliente.emissor() + "): conclua pelo script de provisionamento.");
		}
		realms.criar(cliente.codigo(), cliente.nome());
		if (cliente.contatoEmail() != null && cliente.contatoNome() != null) {
			provisionamento.provisionar(id, cliente.nome(), cliente.contatoNome(), cliente.contatoEmail());
		}
		emPlataforma(() -> {
			clientes.marcarProvisionado(id);
			return null;
		});
		return buscar(id);
	}

	public Resumo alterar(UUID id, Alteracao pedido) {
		var plataforma = ContextoPlataforma.exigir();
		var nome = obrigatorio(pedido.nome(), "Informe o nome do cliente.", 120);
		var limites = validar(pedido.limites());
		var atual = clientes.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (limites.usuarios() != null) {
			var ativos = usuariosAtivos(atual);
			if (ativos > limites.usuarios()) {
				throw new RegraVioladaException("O cliente já tem " + ativos + " usuários ativos; o limite não pode "
						+ "ficar abaixo disso. Peça ao cliente para desativar usuários antes.");
			}
		}
		emPlataforma(() -> {
			clientes.travar(id);
			clientes.atualizar(id, nome, pedido.ativo(), limites.usuarios());
			clientes.definirLimitesDoAssistente(id, limites);
			return null;
		});
		var mudancas = new LinkedHashMap<String, @Nullable Object>();
		if (!atual.nome().equals(nome)) {
			mudancas.put("nome", nome);
		}
		if (atual.ativo() != pedido.ativo()) {
			mudancas.put("ativo", pedido.ativo());
		}
		if (!Objects.equals(atual.limiteUsuarios(), limites.usuarios())) {
			mudancas.put("limiteUsuarios", limites.usuarios());
		}
		if (!Objects.equals(atual.assistenteTokensPorMes(), limites.assistenteTokensPorMes())
				|| !iguais(atual.assistenteUsdPorMes(), limites.assistenteUsdPorMes())) {
			mudancas.put("limitesDoAssistente", true);
		}
		if (!mudancas.isEmpty()) {
			mudancas.put("operador", plataforma.operador());
			noCliente(id, () -> {
				auditoria.registrar(TipoEvento.CLIENTE_ALTERADO, "tenant/" + id, mudancas);
				return null;
			});
		}
		return buscar(id);
	}

	/**
	 * Logo: PNG, JPEG ou WebP até 256 KB, conferido pelo conteúdo (não pela extensão).
	 */
	public Resumo definirLogo(UUID id, byte @Nullable [] conteudo) {
		var plataforma = ContextoPlataforma.exigir();
		clientes.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		Logo logo = null;
		if (conteudo != null) {
			if (conteudo.length == 0 || conteudo.length > TAMANHO_MAXIMO_LOGO) {
				throw new RegraVioladaException("A logo deve ter até 256 KB.");
			}
			var tipo = tipoDaImagem(conteudo);
			if (tipo == null) {
				throw new RegraVioladaException("Envie a logo em PNG, JPEG ou WebP.");
			}
			// ADR 0033: a logo fica pública na tela de entrada do cliente.
			var ameaca = triagem.ameaca(conteudo);
			if (ameaca.isPresent()) {
				LOG.warn("Logo do cliente {} recusada pelo antivírus: {}", id, ameaca.get());
				throw new RegraVioladaException("A imagem foi recusada pelo antivírus (" + ameaca.get() + ").");
			}
			logo = new Logo(conteudo, tipo);
		}
		var nova = logo;
		emPlataforma(() -> {
			clientes.definirLogo(id, nova);
			return null;
		});
		noCliente(id, () -> {
			auditoria.registrar(TipoEvento.CLIENTE_ALTERADO, "tenant/" + id,
					Map.of("logo", nova == null ? "removida" : "alterada", "operador", plataforma.operador()));
			return null;
		});
		return buscar(id);
	}

	public void reenviarConvite(UUID id) {
		ContextoPlataforma.exigir();
		var cliente = clientes.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (cliente.contatoEmail() == null) {
			throw new RegraVioladaException("Este cliente não tem o primeiro administrador registrado na plataforma.");
		}
		provisionamento.reenviarConviteDoAdministrador(id, cliente.contatoEmail());
	}

	static @Nullable String tipoDaImagem(byte[] b) {
		if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
			return "image/png";
		}
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
				&& b[10] == 'B' && b[11] == 'P') {
			return "image/webp";
		}
		return null;
	}

	private long usuariosAtivos(Cliente cliente) {
		return cliente.provisionadoEm() == null && cliente.emissor() == null ? 0
				: provisionamento.contarUsuariosAtivos(cliente.id());
	}

	private static Limites validar(@Nullable Limites limites) {
		if (limites == null) {
			return new Limites(null, null, null);
		}
		if (limites.usuarios() != null && limites.usuarios() < 1) {
			throw new RegraVioladaException("O limite de usuários deve ser de pelo menos 1.");
		}
		if (limites.assistenteTokensPorMes() != null && limites.assistenteTokensPorMes() < 1) {
			throw new RegraVioladaException("O limite de tokens do assistente deve ser positivo.");
		}
		if (limites.assistenteUsdPorMes() != null && limites.assistenteUsdPorMes().signum() <= 0) {
			throw new RegraVioladaException("O limite de custo do assistente deve ser positivo.");
		}
		return limites;
	}

	private static boolean iguais(java.math.@Nullable BigDecimal a, java.math.@Nullable BigDecimal b) {
		return a == null ? b == null : b != null && a.compareTo(b) == 0;
	}

	private static String obrigatorio(@Nullable String valor, String mensagem, int tamanho) {
		if (valor == null || valor.isBlank()) {
			throw new RegraVioladaException(mensagem);
		}
		var limpo = valor.strip();
		if (limpo.length() > tamanho) {
			throw new RegraVioladaException("Valor com mais de " + tamanho + " caracteres.");
		}
		return limpo;
	}

	/** Transação curta no contexto de plataforma. */
	private void emPlataforma(Callable<@Nullable Object> acao) {
		transacao.execute(status -> {
			try {
				return acao.call();
			}
			catch (RuntimeException erro) {
				throw erro;
			}
			catch (Exception erro) {
				throw new IllegalStateException(erro);
			}
		});
	}

	/**
	 * Transação curta dentro do cliente (só com o tenant: ex.: registrar na trilha dele).
	 */
	private void noCliente(UUID tenant, Callable<@Nullable Object> acao) {
		try {
			ContextoAcesso.executar(ContextoAcesso.somenteTenant(tenant), () -> {
				emPlataforma(acao);
				return null;
			});
		}
		catch (RuntimeException erro) {
			throw erro;
		}
		catch (Exception erro) {
			throw new IllegalStateException(erro);
		}
	}

}
