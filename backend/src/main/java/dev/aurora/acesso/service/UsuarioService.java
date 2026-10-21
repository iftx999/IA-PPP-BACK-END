package dev.aurora.acesso.service;

import dev.aurora.acesso.ContextoAcesso;
import dev.aurora.acesso.model.UsuarioAutenticado;
import dev.aurora.acesso.repository.UsuarioRepository;
import dev.aurora.comum.RecursoNaoEncontradoException;

import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

	private final UsuarioRepository usuarios;

	UsuarioService(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	public UsuarioAutenticado usuarioAtual() {
		var contexto = ContextoAcesso.exigir();
		return usuarios.buscarAutenticado(contexto.usuarioId(), contexto.permissoes().stream().sorted().toList())
			.orElseThrow(RecursoNaoEncontradoException::new);
	}

}
