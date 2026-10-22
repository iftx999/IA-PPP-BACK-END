package dev.aurora.contratos.integracao;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.sun.net.httpserver.HttpServer;
import dev.aurora.contratos.model.Trecho;
import dev.aurora.contratos.service.ExtratorContrato.ExtracaoFalhouException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercita o SDK de verdade contra um servidor local que imita a Messages API. */
class ExtratorClaudeTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private HttpServer servidor;

	private final List<JsonNode> pedidos = new ArrayList<>();

	private final List<String> cabecalhosBeta = new ArrayList<>();

	private int status = 200;

	private String resposta;

	private ExtratorClaude extrator;

	@BeforeEach
	void subir() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/v1/messages", troca -> {
			pedidos.add(JSON.readTree(troca.getRequestBody().readAllBytes()));
			cabecalhosBeta.add(troca.getRequestHeaders().getFirst("anthropic-beta"));
			assertThat(troca.getRequestHeaders().getFirst("x-api-key")).isEqualTo("chave-de-teste");
			var corpo = resposta.getBytes(StandardCharsets.UTF_8);
			troca.getResponseHeaders().add("Content-Type", "application/json");
			troca.sendResponseHeaders(status, corpo.length);
			troca.getResponseBody().write(corpo);
			troca.close();
		});
		servidor.start();
		var cliente = AnthropicOkHttpClient.builder()
			.apiKey("chave-de-teste")
			.baseUrl("http://127.0.0.1:" + servidor.getAddress().getPort())
			.maxRetries(0)
			.build();
		extrator = new ExtratorClaude(cliente, "claude-opus-5-5");
	}

	@AfterEach
	void parar() {
		servidor.stop(0);
	}

	@Test
	void enviaEsquemaEsforcoEFallbackELeOResultado() throws Exception {
		resposta = mensagem("end_turn", """
				{"itens":[{"tipo":"META","titulo":"Adesão mínima","resumo":"80% até 2026","clausula":"7.2","pagina":2,
				 "trecho":"A adesão mínima será de 80%","valorReferencia":"80","unidadeMedida":"%",
				 "dataLimite":"2026-12-31","periodicidade":"","unidadeAlvo":"Concessão A","valorAlerta":"75,5",
				 "vigenciaInicio":"","confianca":0.9}],"observacoes":[]}""");

		var resultado = extrator.extrair(List.of(new Trecho("7.2", 2, "A adesão mínima será de 80%")),
				List.of("Concessão A", "Cidade A"));

		var proposta = resultado.propostas().getFirst();
		assertThat(proposta.valorReferencia()).isEqualByComparingTo("80");
		assertThat(proposta.valorAlerta()).isEqualByComparingTo("75.5");
		assertThat(proposta.vigenciaInicio()).isNull();
		assertThat(proposta.periodicidade()).isNull();
		assertThat(resultado.modelo()).isEqualTo("claude-opus-5-5");
		assertThat(resultado.versaoPrompt()).isEqualTo(ExtratorClaude.VERSAO_PROMPT);

		var pedido = pedidos.getFirst();
		assertThat(pedido.get("model").asString()).isEqualTo("claude-opus-5-5");
		assertThat(pedido.get("output_config").get("effort").asString()).isEqualTo("high");
		assertThat(pedido.get("output_config").get("format").get("type").asString()).isEqualTo("json_schema");
		assertThat(pedido.get("fallbacks").asString()).isEqualTo("default");
		assertThat(pedido.get("system").get(0).get("cache_control").get("type").asString()).isEqualTo("ephemeral");
		assertThat(pedido.get("messages").get(0).toString()).contains("[p. 2 | cláusula 7.2]").contains("<unidades>");
		assertThat(cabecalhosBeta.getFirst()).contains("server-side-fallback-2026-07-01");
	}

	@Test
	void recusaEErroDaApiViramFalhaDeExtracao() {
		resposta = mensagem("refusal", null);
		assertThatThrownBy(() -> extrator.extrair(List.of(new Trecho(null, 1, "texto")), List.of()))
			.isInstanceOf(ExtracaoFalhouException.class)
			.hasMessageContaining("recusou");

		status = 529;
		resposta = """
				{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""";
		assertThatThrownBy(() -> extrator.extrair(List.of(new Trecho(null, 1, "texto")), List.of()))
			.isInstanceOf(ExtracaoFalhouException.class)
			.hasMessageContaining("Tente reprocessar");
	}

	@Test
	void divideOTextoEmLotes() {
		var grande = "x".repeat(ExtratorClaude.CARACTERES_POR_LOTE - 10);
		var lotes = ExtratorClaude
			.lotes(List.of(new Trecho("1", 1, grande), new Trecho("2", 2, "y".repeat(100)), new Trecho("3", 3, "z")));
		assertThat(lotes).hasSize(2);
		assertThat(lotes.get(1)).extracting(Trecho::clausula).containsExactly("2", "3");
	}

	private static String mensagem(String parada, String textoJson) {
		var conteudo = textoJson == null ? "[]"
				: "[{\"type\":\"text\",\"text\":" + JSON.writeValueAsString(textoJson) + "}]";
		return """
				{"id":"msg_teste","type":"message","role":"assistant","model":"claude-opus-5-5","content":%s,
				 "stop_reason":"%s","stop_sequence":null,
				 "usage":{"input_tokens":120,"output_tokens":60,"cache_read_input_tokens":0,
				          "cache_creation_input_tokens":0}}""".formatted(conteudo, parada);
	}

}
