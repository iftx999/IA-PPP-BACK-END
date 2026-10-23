package dev.aurora.mapa.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.mapa.model.Area;
import dev.aurora.mapa.model.Marcacao;
import dev.aurora.mapa.model.NovaMarcacao;
import dev.aurora.mapa.repository.MarcacaoRepository;
import org.junit.jupiter.api.Test;

import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarcacaoServiceTest {

	private final MarcacaoRepository repositorio = mock(MarcacaoRepository.class);

	private final Auditoria auditoria = mock(Auditoria.class);

	private final MarcacaoService servico = new MarcacaoService(repositorio, auditoria);

	private final UUID usuario = UUID.randomUUID();

	private final ContextoAcesso contexto = new ContextoAcesso(UUID.randomUUID(), usuario, Set.of("mapa:marcar"),
			List.of("u1"));

	@Test
	void pontoForaDasUnidadesNaoGrava() {
		when(repositorio.buscarUnidadeNoPonto(-46, -22)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> ContextoAcesso.executar(contexto,
				() -> servico.criar(new NovaMarcacao(-46, -22, "Visita", null))))
			.isInstanceOf(RegraVioladaException.class);
		verify(repositorio, never()).inserir(any(), any(), any(), any());
		verifyNoInteractions(auditoria);
	}

	@Test
	void textoChegaLimpoEObservacaoEmBrancoViraNula() throws Exception {
		var unidade = UUID.randomUUID();
		when(repositorio.buscarUnidadeNoPonto(-47.5, -22.6)).thenReturn(Optional.of(unidade));
		when(repositorio.buscar(any())).thenAnswer(chamada -> Optional.of(marcacao(chamada.getArgument(0), usuario)));

		ContextoAcesso.executar(contexto, () -> servico.criar(new NovaMarcacao(-47.5, -22.6, "  Visita ", "   ")));

		verify(repositorio).inserir(any(), eq(unidade), eq(new NovaMarcacao(-47.5, -22.6, "Visita", null)),
				eq(usuario));
	}

	@Test
	void soQuemCriouRemove() throws Exception {
		var id = UUID.randomUUID();
		when(repositorio.buscar(id)).thenReturn(Optional.of(marcacao(id, UUID.randomUUID())));
		assertThatThrownBy(() -> ContextoAcesso.executar(contexto, () -> {
			servico.remover(id);
			return null;
		})).isInstanceOf(AccessDeniedException.class);
		verify(repositorio, never()).remover(any());

		when(repositorio.buscar(id)).thenReturn(Optional.of(marcacao(id, usuario)));
		ContextoAcesso.executar(contexto, () -> {
			servico.remover(id);
			return null;
		});
		verify(repositorio).remover(id);
	}

	@Test
	void listaLimitada() {
		servico.listar(new Area(-48, -23, -47, -22));
		verify(repositorio).listar(any(), eq(MarcacaoService.LIMITE_NA_AREA));
		assertThat(MarcacaoService.LIMITE_NA_AREA).isEqualTo(500);
	}

	private static Marcacao marcacao(UUID id, UUID autor) {
		return new Marcacao(id, UUID.randomUUID(), "Setor", -47.5, -22.6, "Visita", null, autor, "Autor",
				Instant.now());
	}

}
