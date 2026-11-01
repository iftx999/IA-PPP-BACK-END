package dev.aurora.plataforma.service;

import java.util.Optional;
import java.util.concurrent.Callable;

import dev.aurora.contexto.ContextoPlataforma;
import dev.aurora.plataforma.model.Logo;
import dev.aurora.plataforma.repository.ClienteRepository;

import org.springframework.stereotype.Service;

/**
 * O que a tela de entrada precisa antes do login (ADR 0027): a partir do código da
 * empresa, o nome, o emissor onde entrar e a logo. Só clientes ativos e provisionados; o
 * resto do cadastro (contato, limites) não sai daqui.
 */
@Service
public class ConsultaPublicaService {

	/**
	 * Sem pessoa autenticada: a leitura usa o contexto de plataforma só para o cadastro.
	 */
	private static final ContextoPlataforma ANONIMO = new ContextoPlataforma("anonimo");

	private final ClienteRepository clientes;

	ConsultaPublicaService(ClienteRepository clientes) {
		this.clientes = clientes;
	}

	public record ClientePublico(String codigo, String nome, String emissor, boolean temLogo) {
	}

	public Optional<ClientePublico> porCodigo(String codigo) {
		if (!ClienteService.CODIGO.matcher(codigo).matches()) {
			return Optional.empty();
		}
		return comoAnonimo(() -> clientes.buscarPorCodigo(codigo)
			.filter(c -> c.ativo() && c.provisionadoEm() != null && c.emissor() != null)
			.map(c -> new ClientePublico(c.codigo(), c.nome(), c.emissor(), c.temLogo())));
	}

	public Optional<Logo> logo(String codigo) {
		if (!ClienteService.CODIGO.matcher(codigo).matches()) {
			return Optional.empty();
		}
		return comoAnonimo(() -> clientes.buscarLogo(codigo));
	}

	private static <T> T comoAnonimo(Callable<T> acao) {
		try {
			return ContextoPlataforma.executar(ANONIMO, acao);
		}
		catch (RuntimeException erro) {
			throw erro;
		}
		catch (Exception erro) {
			throw new IllegalStateException(erro);
		}
	}

}
