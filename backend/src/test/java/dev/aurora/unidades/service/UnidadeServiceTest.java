package dev.aurora.unidades.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.unidades.Unidade;
import dev.aurora.unidades.model.DadosUnidade;
import dev.aurora.unidades.model.TipoUnidade;
import dev.aurora.unidades.model.UnidadeAdministrada;
import dev.aurora.unidades.repository.UnidadeRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnidadeServiceTest {

	private final UnidadeRepository repositorio = mock(UnidadeRepository.class);

	private final UnidadeService servico = new UnidadeService(repositorio, mock(Auditoria.class));

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

	@Test
	void normalizaCodigoEExigeIbgeSoNoMunicipio() {
		var dados = UnidadeService.normalizar(TipoUnidade.MUNICIPIO,
				new DadosUnidade(" cidade-d ", " Cidade D ", "3550308", null, null));
		assertThat(dados.codigo()).isEqualTo("CIDADE-D");
		assertThat(dados.nome()).isEqualTo("Cidade D");

		assertThatThrownBy(
				() -> UnidadeService.normalizar(TipoUnidade.MUNICIPIO, new DadosUnidade("X", "X", null, null, null)))
			.isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(
				() -> UnidadeService.normalizar(TipoUnidade.ETE, new DadosUnidade("X", "X", "3550308", null, null)))
			.isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(
				() -> UnidadeService.normalizar(TipoUnidade.ETE, new DadosUnidade("COM ESPACO", "X", null, null, null)))
			.isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> UnidadeService.normalizar(TipoUnidade.ETE,
				new DadosUnidade("X", "X", null, BigDecimal.ONE, null)))
			.isInstanceOf(RegraVioladaException.class);
	}

	@Test
	void hierarquiaPermitida() {
		assertThat(TipoUnidade.SETOR_COMERCIAL.aceitaPai(TipoUnidade.MUNICIPIO)).isTrue();
		assertThat(TipoUnidade.SETOR_COMERCIAL.aceitaPai(TipoUnidade.CONCESSAO)).isFalse();
		assertThat(TipoUnidade.MUNICIPIO.aceitaPai(TipoUnidade.CONCESSAO)).isTrue();
		assertThat(TipoUnidade.GRUPO.tiposPai()).isEmpty();
	}

	@Test
	void naoInativaComFilhasAtivasNemComAcessos() {
		var pai = UUID.randomUUID();
		var comFilhas = administrada(TipoUnidade.MUNICIPIO, pai, true, 2, 0);
		when(repositorio.buscarParaGestao(comFilhas.id())).thenReturn(Optional.of(comFilhas));
		assertThatThrownBy(() -> servico.definirAtiva(comFilhas.id(), false)).isInstanceOf(ConflitoException.class)
			.hasMessageContaining("2 unidades ativas");

		var comAcesso = administrada(TipoUnidade.SETOR_COMERCIAL, pai, true, 0, 1);
		when(repositorio.buscarParaGestao(comAcesso.id())).thenReturn(Optional.of(comAcesso));
		assertThatThrownBy(() -> servico.definirAtiva(comAcesso.id(), false)).isInstanceOf(ConflitoException.class)
			.hasMessageContaining("1 concessão de acesso");
		verify(repositorio, never()).definirAtiva(any(), anyBoolean());
	}

	@Test
	void raizNaoSeMoveNemInativa() {
		var raiz = administrada(TipoUnidade.GRUPO, null, true, 1, 1);
		when(repositorio.buscarParaGestao(raiz.id())).thenReturn(Optional.of(raiz));
		assertThatThrownBy(() -> servico.mover(raiz.id(), UUID.randomUUID())).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> servico.definirAtiva(raiz.id(), false)).isInstanceOf(RegraVioladaException.class);
	}

	private static UnidadeAdministrada administrada(TipoUnidade tipo, UUID pai, boolean ativa, long filhas,
			long acessos) {
		return new UnidadeAdministrada(UUID.randomUUID(), tipo, pai, "COD", "Unidade", null, null, null, ativa,
				pai == null ? 1 : 3, filhas, acessos);
	}

}
