package dev.aurora.campanhas.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import dev.aurora.campanhas.model.AcaoDeCampo;
import dev.aurora.campanhas.model.ArquivoDeFoto;
import dev.aurora.campanhas.model.Posicao;
import dev.aurora.campanhas.model.SituacaoEvento;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.model.ValoresDoEncerramento;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.servicos.ParametroDaOrdem;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regras da execução no campo (ADR 0045): transições do ciclo pelas ações do app,
 * validação dos parâmetros no encerramento (RN-22) e das partes das fotos.
 */
class RegrasDeExecucaoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@ParameterizedTest
	@CsvSource(textBlock = """
			INICIAR,   PROGRAMADA,  EM_EXECUCAO
			INICIAR,   PLANEJADA,
			INICIAR,   EM_EXECUCAO,
			INICIAR,   PARALISADA,
			PARALISAR, EM_EXECUCAO, PARALISADA
			PARALISAR, PROGRAMADA,
			PARALISAR, PARALISADA,
			RETOMAR,   PARALISADA,  EM_EXECUCAO
			RETOMAR,   EM_EXECUCAO,
			RETOMAR,   PROGRAMADA,
			ENCERRAR,  EM_EXECUCAO, ENCERRADA
			ENCERRAR,  PROGRAMADA,
			ENCERRAR,  PARALISADA,
			ENCERRAR,  ENCERRADA,
			DEVOLVER,  PROGRAMADA,  DEVOLVIDA
			DEVOLVER,  EM_EXECUCAO, DEVOLVIDA
			DEVOLVER,  PARALISADA,  DEVOLVIDA
			DEVOLVER,  PLANEJADA,
			DEVOLVER,  DEVOLVIDA,
			DEVOLVER,  CANCELADA,
			""")
	void transicoesDoApp(AcaoDeCampo acao, StatusOrdem atual, @Nullable StatusOrdem esperado) {
		assertThat(acao.destino(atual).orElse(null)).isEqualTo(esperado);
	}

	@Test
	void soDevolverNaoETrabalhoDeCampo() {
		assertThat(AcaoDeCampo.DEVOLVER.trabalhoDeCampo()).isFalse();
		for (var acao : List.of(AcaoDeCampo.INICIAR, AcaoDeCampo.PARALISAR, AcaoDeCampo.RETOMAR,
				AcaoDeCampo.ENCERRAR)) {
			assertThat(acao.trabalhoDeCampo()).isTrue();
		}
		assertThat(AcaoDeCampo.PARALISAR.verbo()).isEqualTo("paralisar");
		assertThat(SituacaoEvento.AGUARDANDO.processado()).isFalse();
		assertThat(SituacaoEvento.REJEITADO.processado()).isTrue();
	}

	@Test
	void posicaoValida() {
		assertThat(new Posicao(-22.9, -47.06, 5.0).precisaoM()).isEqualTo(5.0);
		assertThat(new Posicao(90, 180, null).latitude()).isEqualTo(90);
		assertThatThrownBy(() -> new Posicao(90.1, 0, null)).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> new Posicao(0, -180.1, null)).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> new Posicao(Double.NaN, 0, null)).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> new Posicao(0, 0, -1.0)).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> new Posicao(0, 0, 100_001.0)).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> new Posicao(0, Double.POSITIVE_INFINITY, null))
			.isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> new Posicao(-90.1, 0, null)).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> new Posicao(0, 180.1, null)).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> new Posicao(0, 0, Double.NaN)).isInstanceOf(RegraVioladaException.class);
		assertThat(new Posicao(-90, -180, 0.0).precisaoM()).isZero();
		assertThat(new Posicao(0, 0, 100_000.0).precisaoM()).isEqualTo(100_000.0);
	}

	// ----- Parâmetros (RN-22) -----

	private static ParametroDaOrdem p(String chave, String tipo, boolean obrigatorio, @Nullable String min,
			@Nullable String max, List<String> opcoes, @Nullable String condicaoChave, @Nullable String condicaoValor) {
		return new ParametroDaOrdem(chave, "Rótulo " + chave, null, tipo, obrigatorio,
				min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), null, opcoes,
				condicaoChave, condicaoValor);
	}

	private static final List<ParametroDaOrdem> VISTORIA = List.of(
			p("viavel", "SIM_NAO", true, null, null, List.of(), null, null),
			p("motivo", "LISTA", true, null, null, List.of("Cota", "Distância"), "viavel", "false"),
			p("distancia", "DECIMAL", false, "0", "200", List.of(), null, null),
			p("tubos", "INTEIRO", false, "1", "10", List.of(), null, null),
			p("nota", "TEXTO", false, "3", null, List.of(), null, null),
			p("itens", "LISTA_MULTIPLA", false, null, null, List.of("A", "B", "C"), null, null),
			p("quando", "DATA_HORA", false, null, null, List.of(), null, null),
			p("foto", "FOTO", true, "1", "2", List.of(), null, null));

	private static JsonNode json(String texto) {
		return JSON.readTree(texto);
	}

	private static ValoresDoEncerramento validar(String valores) {
		return ValoresDoEncerramento.validar(VISTORIA, json(valores));
	}

	private static void recusa(String valores, String mensagem) {
		assertThatThrownBy(() -> validar(valores)).isInstanceOf(RegraVioladaException.class).hasMessage(mensagem);
	}

	@Test
	void valoresValidosSaoNormalizados() {
		var foto = UUID.randomUUID();
		var v = validar("""
				{"viavel":true,"motivo":"Cota","distancia":12.50,"tubos":3,"nota":"  ok!  ","itens":["B","A"],
				 "quando":"2026-11-18T10:00:00-03:00","foto":["%s","%s"]}""".formatted(foto, foto));
		// "motivo" só vale quando a ligação não é viável: descartado.
		assertThat(v.valores().has("motivo")).isFalse();
		assertThat(v.valores().get("viavel").booleanValue()).isTrue();
		assertThat(v.valores().get("distancia").decimalValue()).isEqualByComparingTo("12.5");
		assertThat(v.valores().get("nota").asString()).isEqualTo("ok!");
		assertThat(v.valores().get("itens").size()).isEqualTo(2);
		assertThat(v.valores().get("quando").asString()).isEqualTo("2026-11-18T13:00:00Z");
		assertThat(v.valores().get("foto").size()).isEqualTo(1);
		assertThat(v.fotos()).containsEntry(foto, "foto");
	}

	@Test
	void parametroCondicionadoObrigatorioQuandoACondicaoVale() {
		var foto = UUID.randomUUID();
		recusa("{\"viavel\":false,\"foto\":[\"%s\"]}".formatted(foto), "Preencha \"Rótulo motivo\".");
		var v = validar("{\"viavel\":false,\"motivo\":\"Distância\",\"foto\":[\"%s\"]}".formatted(foto));
		assertThat(v.valores().get("motivo").asString()).isEqualTo("Distância");
	}

	@Test
	void valoresInvalidos() {
		var f = "\"foto\":[\"" + UUID.randomUUID() + "\"]";
		recusa("{\"viavel\":\"sim\"," + f + "}", "\"Rótulo viavel\": responda sim ou não.");
		recusa("{" + f + "}", "Preencha \"Rótulo viavel\".");
		recusa("{\"viavel\":true}", "Preencha \"Rótulo foto\".");
		recusa("{\"viavel\":true,\"foto\":[]}", "Preencha \"Rótulo foto\".");
		recusa("{\"viavel\":true,\"foto\":[\"x\"]}", "\"Rótulo foto\": identificador de foto inválido.");
		recusa("{\"viavel\":true,\"foto\":\"%s\"}".formatted(UUID.randomUUID()),
				"\"Rótulo foto\": envie as fotos em uma lista.");
		recusa("{\"viavel\":true,\"foto\":[\"%s\",\"%s\",\"%s\"]}".formatted(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID()), "\"Rótulo foto\": de 1 a 2 fotos.");
		recusa("{\"viavel\":true," + f + ",\"distancia\":200.01}", "\"Rótulo distancia\": fora da faixa 0 a 200.");
		recusa("{\"viavel\":true," + f + ",\"distancia\":-1}", "\"Rótulo distancia\": fora da faixa 0 a 200.");
		recusa("{\"viavel\":true," + f + ",\"distancia\":\"12\"}", "\"Rótulo distancia\": informe um número.");
		recusa("{\"viavel\":true," + f + ",\"tubos\":2.5}", "\"Rótulo tubos\": informe um número inteiro.");
		recusa("{\"viavel\":true," + f + ",\"tubos\":0}", "\"Rótulo tubos\": fora da faixa 1 a 10.");
		recusa("{\"viavel\":true," + f + ",\"nota\":\"ab\"}", "\"Rótulo nota\": de 3 a 500 caracteres.");
		recusa("{\"viavel\":true," + f + ",\"nota\":\"" + "x".repeat(501) + "\"}",
				"\"Rótulo nota\": de 3 a 500 caracteres.");
		recusa("{\"viavel\":true," + f + ",\"nota\":5}", "\"Rótulo nota\": informe um texto.");
		recusa("{\"viavel\":true," + f + ",\"itens\":[\"A\",\"A\"]}", "\"Rótulo itens\": opção repetida.");
		recusa("{\"viavel\":true," + f + ",\"itens\":[\"D\"]}", "\"Rótulo itens\": escolha uma das opções.");
		recusa("{\"viavel\":true," + f + ",\"itens\":\"A\"}", "\"Rótulo itens\": escolha as opções em uma lista.");
		recusa("{\"viavel\":false,\"motivo\":\"Outro\"," + f + "}", "\"Rótulo motivo\": escolha uma das opções.");
		recusa("{\"viavel\":true," + f + ",\"quando\":\"2026-11-18 10:00\"}",
				"\"Rótulo quando\": informe data e hora com fuso (ISO 8601).");
		recusa("{\"viavel\":true," + f + ",\"extra\":1}", "Parâmetro \"extra\" não existe nesta versão do serviço.");
		assertThatThrownBy(() -> ValoresDoEncerramento.validar(VISTORIA, json("[1]")))
			.isInstanceOf(RegraVioladaException.class)
			.hasMessage("Os valores dos parâmetros vêm em um objeto (chave → valor).");
	}

	@Test
	void vaziosOpcionaisSaoIgnorados() {
		var f = "\"foto\":[\"" + UUID.randomUUID() + "\"]";
		var v = validar("{\"viavel\":true," + f + ",\"nota\":\"  \",\"itens\":[],\"distancia\":null}");
		assertThat(v.valores().has("nota")).isFalse();
		assertThat(v.valores().has("itens")).isFalse();
		assertThat(v.valores().has("distancia")).isFalse();
		// Serviço sem parâmetros: nada a informar.
		assertThat(ValoresDoEncerramento.validar(List.of(), null).valores().isEmpty()).isTrue();
	}

	@Test
	void mesmaFotoEmDoisParametros() {
		var id = UUID.randomUUID();
		var dois = List.of(p("antes", "FOTO", true, null, null, List.of(), null, null),
				p("depois", "FOTO", false, null, "3", List.of(), null, null));
		assertThatThrownBy(() -> ValoresDoEncerramento.validar(dois,
				json("{\"antes\":[\"%s\"],\"depois\":[\"%s\"]}".formatted(id, id))))
			.hasMessage("\"Rótulo depois\": a mesma foto aparece em mais de um parâmetro.");
		assertThatThrownBy(() -> ValoresDoEncerramento
			.validar(List.of(p("antes", "FOTO", true, null, null, List.of(), null, null)), json("{\"antes\":[]}")))
			.hasMessage("Preencha \"Rótulo antes\".");
		var semMaximo = ValoresDoEncerramento.validar(dois, json("{\"antes\":[\"%s\"]}".formatted(id)));
		assertThat(semMaximo.fotos()).hasSize(1);
	}

	@Test
	void limitesDosParametros() {
		var f = "\"foto\":[\"" + UUID.randomUUID() + "\"]";
		// Exatamente no mínimo e no máximo.
		var v = validar("{\"viavel\":true," + f + ",\"distancia\":0,\"tubos\":10,\"nota\":\"abc\"}");
		assertThat(v.valores().get("tubos").intValue()).isEqualTo(10);
		assertThat(validar("{\"viavel\":true," + f + ",\"distancia\":200,\"tubos\":1}").valores().has("tubos"))
			.isTrue();
		// Sem valores: o obrigatório falta; "null" vale como nenhum valor.
		assertThatThrownBy(() -> ValoresDoEncerramento.validar(VISTORIA, null))
			.hasMessage("Preencha \"Rótulo viavel\".");
		var opcionais = List.of(p("obs", "TEXTO", false, null, "5", List.of(), null, null),
				p("medida", "DECIMAL", false, null, "5", List.of(), null, null),
				p("altura", "DECIMAL", false, "1", null, List.of(), null, null),
				p("livre", "DECIMAL", false, null, null, List.of(), null, null),
				p("tipo", "LISTA", false, null, null, List.of("X", "Y"), null, null),
				p("detalhe", "TEXTO", true, null, null, List.of(), "tipo", "Y"),
				p("extras", "FOTO", false, null, null, List.of(), null, null),
				p("duas", "FOTO", false, "2", null, List.of(), null, null));
		assertThat(ValoresDoEncerramento.validar(opcionais, json("null")).valores().isEmpty()).isTrue();
		assertThat(
				ValoresDoEncerramento.validar(opcionais, json("{\"obs\":\"12345\",\"livre\":-99999}")).valores().size())
			.isEqualTo(2);
		assertThatThrownBy(() -> ValoresDoEncerramento.validar(opcionais, json("{\"obs\":\"123456\"}")))
			.hasMessage("\"Rótulo obs\": de 0 a 5 caracteres.");
		var comUnidade = List.of(new ParametroDaOrdem("medida", "Medida", null, "DECIMAL", false, null,
				new BigDecimal("5"), "m", List.of(), null, null));
		assertThatThrownBy(() -> ValoresDoEncerramento.validar(comUnidade, json("{\"medida\":6}")))
			.hasMessage("\"Medida\": fora da faixa -∞ a 5 m.");
		assertThatThrownBy(() -> ValoresDoEncerramento.validar(opcionais, json("{\"altura\":0.5}")))
			.hasMessage("\"Rótulo altura\": fora da faixa 1 a ∞.");
		// Condição sobre uma lista.
		assertThat(ValoresDoEncerramento.validar(opcionais, json("{\"tipo\":\"X\"}")).valores().has("tipo")).isTrue();
		assertThatThrownBy(() -> ValoresDoEncerramento.validar(opcionais, json("{\"tipo\":\"Y\"}")))
			.hasMessage("Preencha \"Rótulo detalhe\".");
		assertThatThrownBy(() -> ValoresDoEncerramento.validar(opcionais, json("{\"tipo\":1}")))
			.hasMessage("\"Rótulo tipo\": escolha uma das opções.");
		// Fotos: sem máximo; mínimo de duas.
		var tres = "[\"%s\",\"%s\",\"%s\"]".formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		assertThat(ValoresDoEncerramento.validar(opcionais, json("{\"extras\":" + tres + "}")).fotos()).hasSize(3);
		assertThatThrownBy(() -> ValoresDoEncerramento.validar(opcionais,
				json("{\"duas\":[\"%s\"]}".formatted(UUID.randomUUID()))))
			.hasMessage("\"Rótulo duas\": ao menos 2 foto(s).");
		assertThat(ValoresDoEncerramento
			.validar(opcionais, json("{\"duas\":[\"%s\",\"%s\"]}".formatted(UUID.randomUUID(), UUID.randomUUID())))
			.fotos()).hasSize(2);
		assertThatThrownBy(() -> ValoresDoEncerramento.validar(opcionais, json("{\"extras\":[1]}")))
			.hasMessage("\"Rótulo extras\": identificador de foto inválido.");
		assertThatThrownBy(() -> validar("{\"viavel\":true," + f + ",\"quando\":1}"))
			.hasMessage("\"Rótulo quando\": informe data e hora com fuso (ISO 8601).");
		var duas = "\"foto\":[\"%s\",\"%s\"]".formatted(UUID.randomUUID(), UUID.randomUUID());
		assertThat(validar("{\"viavel\":true," + duas + "}").fotos()).hasSize(2);
	}

	// ----- Fotos em partes -----

	@Test
	void intervaloDaParte() {
		var f = ArquivoDeFoto.intervalo("bytes 0-99/300", 100);
		assertThat(f.inicio()).isZero();
		assertThat(f.fim()).isEqualTo(99);
		assertThat(f.total()).isEqualTo(300);
		assertThat(ArquivoDeFoto.intervalo(" bytes 200-299/300 ", 100).fim()).isEqualTo(299);
		for (var ruim : new String[] { "bytes 0-99/*", "0-99/300", "bytes 100-99/300", "bytes 0-300/300" }) {
			assertThatThrownBy(() -> ArquivoDeFoto.intervalo(ruim, 100)).isInstanceOf(RegraVioladaException.class);
		}
		assertThatThrownBy(() -> ArquivoDeFoto.intervalo(null, 1)).isInstanceOf(RegraVioladaException.class);
		assertThat(ArquivoDeFoto.intervalo("bytes 0-0/1", 1).total()).isEqualTo(1);
		assertThat(ArquivoDeFoto.intervalo("bytes 5-5/10", 1).inicio()).isEqualTo(5);
		assertThatThrownBy(() -> ArquivoDeFoto.intervalo("bytes 100-99/300", 0))
			.hasMessage("Content-Range não confere com a parte enviada.");
		assertThatThrownBy(() -> ArquivoDeFoto.intervalo("bytes 200-300/300", 101))
			.hasMessage("Content-Range não confere com a parte enviada.");
		assertThatThrownBy(() -> ArquivoDeFoto.intervalo("bytes 0-98/300", 100))
			.hasMessage("Content-Range não confere com a parte enviada.");
		assertThatThrownBy(() -> ArquivoDeFoto.intervalo("bytes 0-0/0", 1)).hasMessage("A foto tem no máximo 5 MB.");
		assertThatThrownBy(() -> ArquivoDeFoto.intervalo("bytes 0-0/5242881", 1))
			.hasMessage("A foto tem no máximo 5 MB.");
		assertThat(ArquivoDeFoto.intervalo("bytes 0-0/5242880", 1).total()).isEqualTo(5_242_880);
		assertThat(ArquivoDeFoto.intervalo("bytes 0-1048575/5242880", 1_048_576).fim()).isEqualTo(1_048_575);
		assertThatThrownBy(() -> ArquivoDeFoto.intervalo("bytes 0-1048576/5242880", 1_048_577))
			.hasMessage("Envie partes de até 1024 KB.");
	}

	@Test
	void tipoDaFoto() {
		assertThat(ArquivoDeFoto.tipo("image/JPEG; charset=x")).isEqualTo("image/jpeg");
		assertThatThrownBy(() -> ArquivoDeFoto.tipo("image/gif")).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> ArquivoDeFoto.tipo(null)).isInstanceOf(RegraVioladaException.class);
		var jpeg = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0 };
		var png = new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0 };
		var webp = "RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
		assertThat(ArquivoDeFoto.tipoConfere("image/jpeg", jpeg)).isTrue();
		assertThat(ArquivoDeFoto.tipoConfere("image/png", png)).isTrue();
		assertThat(ArquivoDeFoto.tipoConfere("image/webp", webp)).isTrue();
		assertThat(ArquivoDeFoto.tipoConfere("image/jpeg", png)).isFalse();
		assertThat(ArquivoDeFoto.tipoConfere("image/png", jpeg)).isFalse();
		assertThat(ArquivoDeFoto.tipoConfere("image/webp", jpeg)).isFalse();
		assertThat(ArquivoDeFoto.tipoConfere("image/gif", jpeg)).isFalse();
		// Cada byte da assinatura conta; a assinatura exata (sem mais nada) basta.
		var exatos = new byte[][] { { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF },
				{ (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A },
				"RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII) };
		var tipos = new String[] { "image/jpeg", "image/png", "image/webp" };
		for (var i = 0; i < tipos.length; i++) {
			assertThat(ArquivoDeFoto.tipoConfere(tipos[i], exatos[i])).as(tipos[i]).isTrue();
			assertThat(ArquivoDeFoto.tipoConfere(tipos[i], java.util.Arrays.copyOf(exatos[i], exatos[i].length - 1)))
				.as(tipos[i] + " curto")
				.isFalse();
			for (var b = 0; b < exatos[i].length; b++) {
				if (tipos[i].equals("image/webp") && b >= 4 && b < 8) {
					continue;
				}
				var trocado = exatos[i].clone();
				trocado[b] ^= 0x01;
				assertThat(ArquivoDeFoto.tipoConfere(tipos[i], trocado)).as(tipos[i] + " byte " + b).isFalse();
			}
		}
		assertThat(ArquivoDeFoto.sha256("abc".getBytes(StandardCharsets.UTF_8)))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

}
