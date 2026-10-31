package dev.aurora.acesso.service;

import java.util.List;
import java.util.UUID;

import dev.aurora.acesso.Destinatarios;
import dev.aurora.acesso.repository.UsuarioRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DestinatariosService implements Destinatarios {

	private final UsuarioRepository usuarios;

	DestinatariosService(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Destinatario> comPermissao(String permissao, @Nullable UUID unidade) {
		return usuarios.listarComPermissao(permissao, unidade);
	}

}
