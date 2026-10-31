package dev.aurora.acesso.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import dev.aurora.acesso.model.ProvedorCorporativo;
import dev.aurora.acesso.model.ProvedorCorporativo.Protocolo;
import dev.aurora.acesso.repository.TenantRepository;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SSO corporativo do tenant (S10, ADR 0025): o administrador liga o diretório da empresa
 * (OIDC ou SAML) ao realm do tenant. Só pessoas já convidadas entram por ele; o escopo e
 * as permissões continuam vindo das concessões da Aurora.
 */
@Service
public class SsoCorporativoService {

	private static final int TAMANHO_NOME = 60;

	private static final int TAMANHO_CLIENTE = 255;

	private static final int TAMANHO_SEGREDO = 1024;

	private static final int TAMANHO_ENDERECO = 2048;

	private final TenantRepository tenants;

	private final ProvedorIdentidade identidade;

	private final Auditoria auditoria;

	private final boolean permitirEnderecosInternos;

	SsoCorporativoService(TenantRepository tenants, ProvedorIdentidade identidade, Auditoria auditoria,
			@Value("${aurora.seguranca.sso-permitir-enderecos-internos:false}") boolean permitirEnderecosInternos) {
		this.tenants = tenants;
		this.identidade = identidade;
		this.auditoria = auditoria;
		this.permitirEnderecosInternos = permitirEnderecosInternos;
	}

	/**
	 * @param provedor vazio se o tenant não tem SSO
	 * @param confiaSegundoFator a verificação em duas etapas do IdP vale no lugar do TOTP
	 * @param enderecoRetorno endereço de retorno (redirect URI / ACS) a registrar no IdP
	 * @param entidadeSaml identificador da Aurora como provedor de serviço SAML
	 * @param metadadosSaml metadados SAML da Aurora, para importar no IdP
	 */
	public record Configuracao(Optional<ProvedorCorporativo> provedor, boolean confiaSegundoFator,
			String enderecoRetorno, String entidadeSaml, String metadadosSaml) {
	}

	/**
	 * Pedido de alteração. {@code segredo} nulo mantém o atual (o segredo nunca é
	 * devolvido).
	 */
	public record Alteracao(Protocolo protocolo, String nome, String endereco, @Nullable String clienteId,
			@Nullable String segredo, boolean habilitado, boolean confiaSegundoFator) {
	}

	@Transactional(readOnly = true)
	public Configuracao obter() {
		var emissor = emissor(EscopoAdministrativo.exigirTenantInteiro());
		return configuracao(emissor, identidade.provedorCorporativo(emissor), tenants.ssoConfiaSegundoFator());
	}

	/**
	 * Cria ou altera o IdP. A chamada ao provedor de identidade fica dentro da transação:
	 * se falhar, a política também não muda.
	 */
	@Transactional
	public Configuracao salvar(Alteracao pedido) {
		var contexto = EscopoAdministrativo.exigirTenantInteiro();
		var emissor = emissor(contexto);
		var nome = obrigatorio(pedido.nome(), "Informe o nome exibido no botão de login.", TAMANHO_NOME);
		var endereco = validarEndereco(pedido.endereco());
		var atual = identidade.provedorCorporativo(emissor);
		String clienteId = null;
		String segredo = null;
		if (pedido.protocolo() == Protocolo.OIDC) {
			clienteId = obrigatorio(pedido.clienteId(), "Informe o ID do aplicativo (client ID) registrado no IdP.",
					TAMANHO_CLIENTE);
			var jaTemSegredo = atual.map(p -> p.protocolo() == Protocolo.OIDC).orElse(false);
			if (pedido.segredo() != null && !pedido.segredo().isBlank()) {
				segredo = obrigatorio(pedido.segredo(), "", TAMANHO_SEGREDO);
			}
			else if (!jaTemSegredo) {
				throw new RegraVioladaException("Informe o segredo do aplicativo (client secret) registrado no IdP.");
			}
		}
		var provedor = new ProvedorCorporativo(pedido.protocolo(), nome, endereco, clienteId, pedido.habilitado());
		identidade.salvarProvedorCorporativo(emissor, provedor, segredo);
		tenants.definirSsoConfiaSegundoFator(pedido.confiaSegundoFator());

		// Sem endereços nem segredo na trilha: só o que mudou de política.
		var detalhes = new LinkedHashMap<String, Object>();
		detalhes.put("protocolo", provedor.protocolo().name());
		detalhes.put("habilitado", provedor.habilitado());
		detalhes.put("confiaSegundoFator", pedido.confiaSegundoFator());
		detalhes.put("novo", atual.isEmpty());
		detalhes.put("segredoAlterado", segredo != null);
		auditoria.registrar(TipoEvento.SSO_CONFIGURADO, "tenant/" + contexto.tenantId(), detalhes);
		return configuracao(emissor, Optional.of(provedor), pedido.confiaSegundoFator());
	}

	/**
	 * Remove o IdP: as pessoas voltam a entrar com e-mail e senha (ou pedem nova senha).
	 */
	@Transactional
	public Configuracao remover() {
		var contexto = EscopoAdministrativo.exigirTenantInteiro();
		var emissor = emissor(contexto);
		if (identidade.provedorCorporativo(emissor).isPresent()) {
			identidade.removerProvedorCorporativo(emissor);
			auditoria.registrar(TipoEvento.SSO_REMOVIDO, "tenant/" + contexto.tenantId(), Map.of());
		}
		tenants.definirSsoConfiaSegundoFator(false);
		return configuracao(emissor, Optional.empty(), false);
	}

	private String emissor(ContextoAcesso contexto) {
		return tenants.buscarEmissor(contexto.tenantId())
			.orElseThrow(() -> new RegraVioladaException("O tenant não tem provedor de identidade configurado."));
	}

	private static Configuracao configuracao(String emissor, Optional<ProvedorCorporativo> provedor, boolean confia) {
		var retorno = emissor + "/broker/" + ProvedorCorporativo.APELIDO + "/endpoint";
		return new Configuracao(provedor, provedor.isPresent() && confia, retorno, emissor, retorno + "/descriptor");
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

	/**
	 * O Keycloak busca o endereço do lado do servidor. Em produção, só HTTPS e host
	 * público: endereço interno permitiria sondar a rede onde o Keycloak roda.
	 */
	String validarEndereco(@Nullable String valor) {
		var texto = obrigatorio(valor, "Informe o endereço de descoberta (OIDC) ou dos metadados (SAML) do IdP.",
				TAMANHO_ENDERECO);
		URI uri;
		try {
			uri = new URI(texto);
		}
		catch (URISyntaxException erro) {
			throw new RegraVioladaException("Endereço do IdP inválido.");
		}
		var esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (uri.getHost() == null || uri.getRawUserInfo() != null
				|| !(esquema.equals("https") || esquema.equals("http"))) {
			throw new RegraVioladaException("Endereço do IdP inválido.");
		}
		if (permitirEnderecosInternos) {
			return texto;
		}
		if (!esquema.equals("https")) {
			throw new RegraVioladaException("O endereço do IdP precisa usar HTTPS.");
		}
		InetAddress[] enderecos;
		try {
			enderecos = InetAddress.getAllByName(uri.getHost());
		}
		catch (UnknownHostException erro) {
			throw new RegraVioladaException("Não foi possível encontrar o servidor " + uri.getHost() + ".");
		}
		for (var ip : enderecos) {
			if (interno(ip)) {
				throw new RegraVioladaException(
						"O endereço do IdP precisa ser público (não pode apontar para a rede " + "interna).");
			}
		}
		return texto;
	}

	private static boolean interno(InetAddress ip) {
		var bytes = ip.getAddress();
		// fc00::/7 (IPv6 de uso local) e 100.64.0.0/10 (CGNAT) não têm método próprio.
		var ipv6Local = bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
		var cgnat = bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
		return ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress() || ip.isAnyLocalAddress()
				|| ip.isMulticastAddress() || ipv6Local || cgnat;
	}

}
