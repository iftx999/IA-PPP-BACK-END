package dev.aurora.servicos.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Limites exatos das regras do catálogo (RN-20, J2): cada limite aceito e o primeiro
 * valor recusado.
 */
class FronteirasDoCatalogoTest {

	private static final Prazos PRAZOS = new Prazos(24, 2, 5, 10);

	@Test
	void prazos() {
		new Prazos(1, 1, 1, 1).validar();
		new Prazos(72, 365, 365, 365).validar();
		for (var invalido : List.of(new Prazos(0, 1, 1, 1), new Prazos(73, 1, 1, 1), new Prazos(1, 0, 1, 1),
				new Prazos(1, 1, 1, 366), new Prazos(1, 366, 366, 366), new Prazos(1, 3, 2, 5),
				new Prazos(1, 1, 5, 4))) {
			assertThatThrownBy(invalido::validar).isInstanceOf(RegraVioladaException.class);
		}
	}

	@Test
	void tempoPadrao() {
		assertThat(servico("0.01", false, UnidadeMedida.UN).normalizada().tempoPadraoMin())
			.isEqualTo(new BigDecimal("0.01"));
		assertThat(servico("1440", false, UnidadeMedida.UN).normalizada().tempoPadraoMin())
			.isEqualTo(new BigDecimal("1440"));
		assertThat(servico("3", true, UnidadeMedida.M).normalizada().tempoPorUnidade()).isTrue();
		for (var invalido : List.of("0", "-1", "1440.01", "1.005")) {
			assertThatThrownBy(() -> servico(invalido, false, UnidadeMedida.UN).normalizada())
				.hasMessageContaining("Tempo padrão");
		}
	}

	@Test
	void nomeDescricaoEAlvo() {
		assertThat(comNome("abc").normalizada().nome()).isEqualTo("abc");
		assertThat(comNome("x".repeat(120)).normalizada().nome()).hasSize(120);
		assertThatThrownBy(() -> comNome("ab").normalizada()).hasMessageContaining("Nome do serviço");
		assertThatThrownBy(() -> comNome("x".repeat(121)).normalizada()).hasMessageContaining("Nome do serviço");

		var descricao = new DefinicaoServico("Serviço", "d".repeat(500), UnidadeMedida.UN, AlvoServico.IMOVEL,
				BigDecimal.TEN, false, PRAZOS, null, null, true, List.of());
		assertThat(descricao.normalizada().descricao()).hasSize(500);
		assertThat(descricao.normalizada().conferidoPelaCarga()).isTrue();
		assertThatThrownBy(() -> new DefinicaoServico("Serviço", "d".repeat(501), UnidadeMedida.UN, AlvoServico.IMOVEL,
				BigDecimal.TEN, false, PRAZOS, null, null, false, List.of())
			.normalizada()).hasMessageContaining("Descrição");
		assertThat(new DefinicaoServico("Serviço", "   ", UnidadeMedida.UN, AlvoServico.IMOVEL, BigDecimal.TEN, false,
				PRAZOS, null, null, false, List.of())
			.normalizada()
			.descricao()).isNull();
		assertThatThrownBy(() -> new DefinicaoServico("Serviço", null, UnidadeMedida.M2, AlvoServico.AMBOS,
				BigDecimal.TEN, false, PRAZOS, null, null, true, List.of())
			.normalizada()).hasMessageContaining("conferido pela carga");
	}

	@Test
	void quantidadeDeParametros() {
		var quarenta = IntStream.rangeClosed(1, 40).mapToObj(i -> simNao("p" + i)).toList();
		assertThat(comParametros(quarenta).normalizada().parametros()).hasSize(40);
		var quarentaEUm = new ArrayList<>(quarenta);
		quarentaEUm.add(simNao("p41"));
		assertThatThrownBy(() -> comParametros(quarentaEUm).normalizada()).hasMessageContaining("no máximo 40");
	}

	@Test
	void faixaDoTexto() {
		var texto = um(parametro(TipoParametro.TEXTO, false, BigDecimal.ZERO, new BigDecimal("500")));
		assertThat(texto.minimo()).isEqualTo(BigDecimal.ZERO);
		assertThat(texto.maximo()).isEqualTo(new BigDecimal("500"));
		assertThat(um(parametro(TipoParametro.TEXTO, false, null, null)).maximo()).isEqualTo(new BigDecimal("500"));
		assertThatThrownBy(() -> um(parametro(TipoParametro.TEXTO, false, new BigDecimal("-1"), null)))
			.hasMessageContaining("texto de 0 a 500");
		assertThatThrownBy(() -> um(parametro(TipoParametro.TEXTO, false, null, new BigDecimal("501"))))
			.hasMessageContaining("texto de 0 a 500");
	}

	@Test
	void faixaDaFoto() {
		var opcional = um(parametro(TipoParametro.FOTO, false, null, null));
		assertThat(opcional.minimo()).isEqualTo(BigDecimal.ZERO);
		assertThat(opcional.maximo()).isEqualTo(new BigDecimal("5"));
		var muitas = um(parametro(TipoParametro.FOTO, true, new BigDecimal("7"), null));
		assertThat(muitas.maximo()).isEqualTo(new BigDecimal("7"));
		assertThat(um(parametro(TipoParametro.FOTO, true, BigDecimal.ONE, BigDecimal.TEN)).maximo())
			.isEqualTo(BigDecimal.TEN);
		assertThatThrownBy(() -> um(parametro(TipoParametro.FOTO, false, new BigDecimal("-1"), BigDecimal.ONE)))
			.hasMessageContaining("de 0 a 10 fotos");
		assertThatThrownBy(() -> um(parametro(TipoParametro.FOTO, true, BigDecimal.ONE, new BigDecimal("11"))))
			.hasMessageContaining("de 0 a 10 fotos");
		assertThatThrownBy(() -> um(parametro(TipoParametro.FOTO, true, new BigDecimal("3"), new BigDecimal("2"))))
			.hasMessageContaining("maior que o máximo");
	}

	@Test
	void faixaDosNumeros() {
		var igual = um(parametro(TipoParametro.DECIMAL, true, new BigDecimal("2.50"), new BigDecimal("2.5")));
		assertThat(igual.minimo()).isEqualTo(igual.maximo());
		assertThat(um(parametro(TipoParametro.INTEIRO, true, new BigDecimal("2.0"), null)).minimo())
			.isEqualTo(new BigDecimal("2"));
		assertThatThrownBy(() -> um(parametro(TipoParametro.INTEIRO, true, null, new BigDecimal("2.5"))))
			.hasMessageContaining("inteiros");
		assertThatThrownBy(() -> um(parametro(TipoParametro.FOTO, true, new BigDecimal("1.5"), null)))
			.hasMessageContaining("inteiros");
		assertThatThrownBy(() -> um(parametro(TipoParametro.DATA_HORA, false, null, BigDecimal.ONE)))
			.hasMessageContaining("mínimo e máximo só");
		assertThat(um(
				new Parametro("q", "Quantidade", null, TipoParametro.INTEIRO, true, null, null, "un", List.of(), null))
			.unidade()).isEqualTo("un");
		assertThat(Parametro.limpo(new BigDecimal("1E+2")).toPlainString()).isEqualTo("100");
		assertThat(Parametro.limpo(new BigDecimal("1E+2")).scale()).isZero();
		assertThat(Parametro.limpo(new BigDecimal("0.10")).scale()).isEqualTo(1);
		assertThat(Parametro.limpo(null)).isNull();
	}

	@Test
	void opcoes() {
		var trinta = IntStream.rangeClosed(1, 30).mapToObj(i -> "Opção " + i).toList();
		assertThat(um(lista(TipoParametro.LISTA_MULTIPLA, trinta)).opcoes()).hasSize(30);
		var trintaEUma = new ArrayList<>(trinta);
		trintaEUma.add("Opção 31");
		assertThatThrownBy(() -> um(lista(TipoParametro.LISTA, trintaEUma))).hasMessageContaining("de 2 a 30");
		assertThat(um(lista(TipoParametro.LISTA, List.of("x".repeat(60), "B"))).opcoes().getFirst()).hasSize(60);
		assertThatThrownBy(() -> um(lista(TipoParametro.LISTA, List.of("x".repeat(61), "B"))))
			.hasMessageContaining("Opção");
		assertThatThrownBy(() -> um(new Parametro("t", "Texto", null, TipoParametro.TEXTO, false, null, null, null,
				List.of("A", "B"), null)))
			.hasMessageContaining("opções só em lista");
		assertThat(um(parametro(TipoParametro.SIM_NAO, true, null, null)).opcoes()).isEmpty();
	}

	@Test
	void rotuloEAjuda() {
		assertThat(um(new Parametro("a", "ab", "h".repeat(200), TipoParametro.SIM_NAO, true, null, null, null,
				List.of(), null))
			.ajuda()).hasSize(200);
		assertThatThrownBy(() -> um(new Parametro("a", "ab", "h".repeat(201), TipoParametro.SIM_NAO, true, null, null,
				null, List.of(), null)))
			.hasMessageContaining("Ajuda");
		assertThatThrownBy(
				() -> um(new Parametro("a", "a", null, TipoParametro.SIM_NAO, true, null, null, null, List.of(), null)))
			.hasMessageContaining("Rótulo");
		assertThat(um(new Parametro("a", "x".repeat(80), null, TipoParametro.SIM_NAO, true, null, null, null, List.of(),
				null))
			.rotulo()).hasSize(80);
	}

	@Test
	void condicoes() {
		var simNao = simNao("feito");
		assertThat(segundo(simNao, condicional("FEITO", " true ")).condicao()).isEqualTo(new Condicao("feito", "true"));
		assertThat(segundo(simNao, condicional("feito", "false")).condicao()).isEqualTo(new Condicao("feito", "false"));
		assertThatThrownBy(() -> segundo(simNao, condicional("feito", "sim"))).hasMessageContaining("não existe");
		assertThatThrownBy(() -> segundo(simNao, condicional("outro", "true"))).hasMessageContaining("venha antes");
		var texto = parametro(TipoParametro.TEXTO, false, null, null);
		assertThatThrownBy(() -> segundo(texto, condicional("campo", "x"))).hasMessageContaining("venha antes");
		var multipla = lista(TipoParametro.LISTA_MULTIPLA, List.of("A", "B"));
		assertThatThrownBy(() -> segundo(multipla, condicional("campo", "A"))).hasMessageContaining("venha antes");
	}

	@Test
	void tiposDeParametro() {
		assertThat(TipoParametro.LISTA.temOpcoes()).isTrue();
		assertThat(TipoParametro.LISTA_MULTIPLA.temOpcoes()).isTrue();
		assertThat(TipoParametro.SIM_NAO.temOpcoes()).isFalse();
		assertThat(TipoParametro.FOTO.temFaixa()).isTrue();
		assertThat(TipoParametro.TEXTO.temFaixa()).isTrue();
		assertThat(TipoParametro.DATA_HORA.temFaixa()).isFalse();
		assertThat(TipoParametro.SIM_NAO.condiciona()).isTrue();
		assertThat(TipoParametro.LISTA.condiciona()).isTrue();
		assertThat(TipoParametro.LISTA_MULTIPLA.condiciona()).isFalse();
		assertThat(TipoParametro.DECIMAL.numerico()).isTrue();
		assertThat(TipoParametro.FOTO.numerico()).isFalse();
	}

	@Test
	void itensDeApoioEMotivos() {
		assertThat(new DadosGrupo("Ligações", "  Ligações  ", true).normalizados())
			.isEqualTo(new DadosGrupo("LIGACOES", "Ligações", true));
		assertThat(new DadosHabilidade("solda pead", "Solda", false).normalizados())
			.isEqualTo(new DadosHabilidade("SOLDA_PEAD", "Solda", false));
		assertThat(new DadosTipoVeiculo("moto", "Moto", PerfilRota.MOTO, true).normalizados())
			.isEqualTo(new DadosTipoVeiculo("MOTO", "Moto", PerfilRota.MOTO, true));
		assertThat(new DadosGrupo("AB", "ab", true).normalizados().nome()).isEqualTo("ab");
		assertThatThrownBy(() -> new DadosGrupo("AB", "a", true).normalizados()).hasMessageContaining("Nome do grupo");
		assertThatThrownBy(() -> new DadosGrupo("AB", "x".repeat(81), true).normalizados())
			.hasMessageContaining("Nome do grupo");
		assertThatThrownBy(() -> new DadosGrupo("AB", null, true).normalizados()).hasMessageContaining("Nome do grupo");

		assertThat(new DadosMotivo(TipoMotivo.PARALISACAO, "Chuva", "Chuva", false, null, true).normalizados()
			.exigeObservacao()).isFalse();
		assertThat(new DadosMotivo(TipoMotivo.PARALISACAO, "Chuva", "Chuva", true, null, true).normalizados()
			.exigeObservacao()).isTrue();
		assertThat(new DadosMotivo(TipoMotivo.CANCELAMENTO, "Outro", "Outro", false, null, true).normalizados()
			.exigeObservacao()).isTrue();
	}

	private static DefinicaoServico servico(String tempo, boolean porUnidade, UnidadeMedida unidade) {
		return new DefinicaoServico("Serviço", null, unidade, AlvoServico.TRECHO, new BigDecimal(tempo), porUnidade,
				PRAZOS, null, null, false, List.of());
	}

	private static DefinicaoServico comNome(String nome) {
		return new DefinicaoServico(nome, null, UnidadeMedida.UN, AlvoServico.IMOVEL, BigDecimal.TEN, false, PRAZOS,
				null, null, false, List.of());
	}

	private static DefinicaoServico comParametros(List<Parametro> parametros) {
		return new DefinicaoServico("Serviço", null, UnidadeMedida.UN, AlvoServico.IMOVEL, BigDecimal.TEN, false,
				PRAZOS, null, null, false, parametros);
	}

	private static Parametro um(Parametro p) {
		return comParametros(List.of(p)).normalizada().parametros().getFirst();
	}

	private static Parametro segundo(Parametro primeiro, Parametro segundo) {
		return comParametros(List.of(primeiro, segundo)).normalizada().parametros().get(1);
	}

	private static Parametro simNao(String chave) {
		return new Parametro(chave, "Sim ou não", null, TipoParametro.SIM_NAO, true, null, null, null, List.of(), null);
	}

	private static Parametro condicional(String chave, String valor) {
		return new Parametro("detalhe", "Detalhe", null, TipoParametro.TEXTO, false, null, null, null, List.of(),
				new Condicao(chave, valor));
	}

	private static Parametro parametro(TipoParametro tipo, boolean obrigatorio, @Nullable BigDecimal minimo,
			@Nullable BigDecimal maximo) {
		return new Parametro("campo", "Campo", null, tipo, obrigatorio, minimo, maximo, null, List.of(), null);
	}

	private static Parametro lista(TipoParametro tipo, List<String> opcoes) {
		return new Parametro("campo", "Campo", null, tipo, true, null, null, null, opcoes, null);
	}

}
