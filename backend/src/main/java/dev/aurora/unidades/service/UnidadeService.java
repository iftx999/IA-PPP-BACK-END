package dev.aurora.unidades.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.unidades.Unidades;
import dev.aurora.unidades.Unidade;
import dev.aurora.unidades.repository.UnidadeRepository;

import org.springframework.stereotype.Service;

@Service
public class UnidadeService implements Unidades {

	private final UnidadeRepository unidades;

	UnidadeService(UnidadeRepository unidades) {
		this.unidades = unidades;
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

}
