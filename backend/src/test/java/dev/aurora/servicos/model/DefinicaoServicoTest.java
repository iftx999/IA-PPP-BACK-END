package dev.aurora.servicos.model;

import java.math.BigDecimal;
import java.util.List;

import dev.aurora.comum.RegraVioladaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RN-20 e J2: validação e normalização da definição do serviço e dos parâmetros. */
class DefinicaoServicoTest {

	private static final Prazos PRAZOS = new Prazos(24, 2, 5, 10);

	@Test
	void normalizaTextosNumerosEFotos() {
		var d = definicao(List.of(
				new Parametro(" Viavel ", "  Ligação   viável ", " ", TipoParametro.SIM_NAO, true, null, null, null,
						List.of(), null),
				new Parametro("foto", "Foto", null, TipoParametro.FOTO, true, null, null, null, List.of(), null),
				new Parametro("distancia", "Distância", null, TipoParametro.DECIMAL, false, new BigDecimal("0.00"),
						new BigDecimal("200.0"), "m", List.of(), new Condicao("viavel", "true"))))
			.normalizada();

		assertThat(d.tempoPadraoMin()).isEqualTo(new BigDecimal("20"));
		var viavel = d.parametros().get(0);
		assertThat(viavel.chave()).isEqualTo("viavel");
		assertThat(viavel.rotulo()).isEqualTo("Ligação viável");
		assertThat(viavel.ajuda()).isNull();
		var foto = d.parametros().get(1);
		assertThat(foto.minimo()).isEqualTo(BigDecimal.ONE);
		assertThat(foto.maximo()).isEqualTo(new BigDecimal("5"));
		var distancia = d.parametros().get(2);
		assertThat(distancia.minimo()).isEqualTo(BigDecimal.ZERO);
		assertThat(distancia.maximo()).isEqualTo(new BigDecimal("200"));
		assertThat(distancia.maximo().toString()).isEqualTo("200");
		// Normalizar de novo não muda nada: é a base da comparação de versões.
		assertThat(d.normalizada()).isEqualTo(d);
	}

	@Test
	void condicaoSoEmParametroAnteriorSimNaoOuLista() {
		var depois = List.of(new Parametro("medida", "Medida", null, TipoParametro.DECIMAL, false, null, null, null,
				List.of(), new Condicao("tipo", "A")), lista("tipo", "A", "B"));
		assertThatThrownBy(() -> definicao(depois).normalizada()).isInstanceOf(RegraVioladaException.class)
			.hasMessageContaining("venha antes");

		var valorInexistente = List.of(lista("tipo", "A", "B"), new Parametro("medida", "Medida", null,
				TipoParametro.DECIMAL, false, null, null, null, List.of(), new Condicao("tipo", "C")));
		assertThatThrownBy(() -> definicao(valorInexistente).normalizada()).hasMessageContaining("não existe");

		var valido = List.of(lista("tipo", "A", "B"), new Parametro("medida", "Medida", null, TipoParametro.DECIMAL,
				false, null, null, null, List.of(), new Condicao("tipo", "B")));
		assertThat(definicao(valido).normalizada().parametros().get(1).condicao()).isEqualTo(new Condicao("tipo", "B"));
	}

	@Test
	void regrasDosParametros() {
		assertThatThrownBy(() -> definicao(List.of(lista("tipo", "A"))).normalizada())
			.hasMessageContaining("de 2 a 30 opções");
		assertThatThrownBy(() -> definicao(List.of(lista("tipo", "A", "a"))).normalizada())
			.hasMessageContaining("opção repetida");
		assertThatThrownBy(() -> definicao(List.of(lista("tipo", "A", "B"), lista("TIPO", "C", "D"))).normalizada())
			.hasMessageContaining("repetida: \"tipo\"");
		assertThatThrownBy(() -> definicao(List
			.of(new Parametro("1x", "Errado", null, TipoParametro.TEXTO, false, null, null, null, List.of(), null)))
			.normalizada()).hasMessageContaining("chave");
		assertThatThrownBy(() -> definicao(List.of(new Parametro("q", "Quantidade", null, TipoParametro.INTEIRO, true,
				new BigDecimal("1.5"), null, null, List.of(), null)))
			.normalizada()).hasMessageContaining("inteiros");
		assertThatThrownBy(() -> definicao(List.of(new Parametro("q", "Quantidade", null, TipoParametro.INTEIRO, true,
				BigDecimal.TEN, BigDecimal.ONE, null, List.of(), null)))
			.normalizada()).hasMessageContaining("maior que o máximo");
		assertThatThrownBy(() -> definicao(List.of(new Parametro("s", "Sim", null, TipoParametro.SIM_NAO, true,
				BigDecimal.ONE, null, null, List.of(), null)))
			.normalizada()).hasMessageContaining("mínimo e máximo só");
		assertThatThrownBy(() -> definicao(List.of(new Parametro("f", "Fotos", null, TipoParametro.FOTO, true,
				BigDecimal.ZERO, null, null, List.of(), null)))
			.normalizada()).hasMessageContaining("ao menos 1");
		assertThatThrownBy(() -> definicao(List.of(new Parametro("f", "Fotos", null, TipoParametro.FOTO, false, null,
				BigDecimal.valueOf(11), null, List.of(), null)))
			.normalizada()).hasMessageContaining("de 0 a 10 fotos");
		assertThatThrownBy(() -> definicao(List
			.of(new Parametro("t", "Texto", null, TipoParametro.TEXTO, false, null, null, "m", List.of(), null)))
			.normalizada()).hasMessageContaining("unidade só em número");
	}

	@Test
	void regrasDoServico() {
		assertThatThrownBy(() -> new DefinicaoServico("Limpeza", null, UnidadeMedida.UN, AlvoServico.TRECHO,
				BigDecimal.TEN, true, PRAZOS, null, null, false, List.of())
			.normalizada()).hasMessageContaining("Tempo por unidade");
		assertThatThrownBy(() -> new DefinicaoServico("Limpeza", null, UnidadeMedida.M, AlvoServico.TRECHO,
				BigDecimal.TEN, true, PRAZOS, null, null, true, List.of())
			.normalizada()).hasMessageContaining("conferido pela carga");
		assertThatThrownBy(() -> new DefinicaoServico("Limpeza", null, UnidadeMedida.M, AlvoServico.TRECHO,
				new BigDecimal("1441"), false, PRAZOS, null, null, false, List.of())
			.normalizada()).hasMessageContaining("Tempo padrão");
		assertThatThrownBy(() -> new DefinicaoServico("Limpeza", null, UnidadeMedida.M, AlvoServico.TRECHO,
				BigDecimal.TEN, false, new Prazos(24, 5, 2, 10), null, null, false, List.of())
			.normalizada()).hasMessageContaining("prioridade alta");
		assertThatThrownBy(() -> new DefinicaoServico("Limpeza", null, UnidadeMedida.M, AlvoServico.TRECHO,
				BigDecimal.TEN, false, new Prazos(73, 1, 2, 10), null, null, false, List.of())
			.normalizada()).hasMessageContaining("72 horas");
		assertThatThrownBy(() -> new DefinicaoServico("Li", null, UnidadeMedida.M, AlvoServico.TRECHO, BigDecimal.TEN,
				false, PRAZOS, null, null, false, List.of())
			.normalizada()).hasMessageContaining("Nome do serviço");
	}

	@Test
	void codigoVemDoNome() {
		assertThat(Codigo.normalizar(" Limpeza de PV ")).isEqualTo("LIMPEZA_DE_PV");
		assertThat(Codigo.normalizar("Reposição — pavimento")).isEqualTo("REPOSICAO_PAVIMENTO");
		assertThatThrownBy(() -> Codigo.normalizar("1 serviço")).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> Codigo.normalizar("—")).isInstanceOf(RegraVioladaException.class);
	}

	@Test
	void outroSempreExigeObservacao() {
		var motivo = new DadosMotivo(TipoMotivo.DEVOLUCAO, "outro", "Outro", false, null, true).normalizados();
		assertThat(motivo.codigo()).isEqualTo("OUTRO");
		assertThat(motivo.exigeObservacao()).isTrue();
	}

	private static Parametro lista(String chave, String... opcoes) {
		return new Parametro(chave, "Tipo", null, TipoParametro.LISTA, true, null, null, null, List.of(opcoes), null);
	}

	private static DefinicaoServico definicao(List<Parametro> parametros) {
		return new DefinicaoServico("Vistoria de viabilidade", null, UnidadeMedida.UN, AlvoServico.IMOVEL,
				new BigDecimal("20.00"), false, PRAZOS, null, null, false, parametros);
	}

}
