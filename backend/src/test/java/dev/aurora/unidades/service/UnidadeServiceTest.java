package dev.aurora.unidades.service;

import java.util.Optional;
import java.util.UUID;

import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.unidades.model.Unidade;
import dev.aurora.unidades.repository.UnidadeRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnidadeServiceTest {

	private final UnidadeRepository repositorio = mock(UnidadeRepository.class);

	private final UnidadeService servico = new UnidadeService(repositorio);

	@Test
	void retornaAUnidadeDoEscopo() {
		var id = UUID.randomUUID();
		var unidade = new Unidade(id, "MUNICIPIO", null, "CIDADE-A", "Cidade A", "3526902");
		when(repositorio.buscarNoEscopo(id)).thenReturn(Optional.of(unidade));

		assertThat(servico.buscar(id)).isEqualTo(unidade);
	}

	@Test
	void unidadeForaDoEscopoViraNaoEncontrada() {
		var id = UUID.randomUUID();
		when(repositorio.buscarNoEscopo(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.buscar(id)).isInstanceOf(RecursoNaoEncontradoException.class);
	}

}
