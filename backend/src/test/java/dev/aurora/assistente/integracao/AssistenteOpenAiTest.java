package dev.aurora.assistente.integracao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import dev.aurora.OpenAiSimulado;
import dev.aurora.assistente.model.DefinicaoFerramenta;
import dev.aurora.assistente.model.SaidaFerramenta;
import dev.aurora.assistente.service.AssistenteIndisponivelException;
import dev.aurora.comum.PropriedadesIa;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistenteOpenAiTest {

	private final OpenAiSimulado api = new OpenAiSimulado();

	private final AssistenteOpenAi assistente = new AssistenteOpenAi(
			OpenAIOkHttpClient.builder().apiKey("chave-de-teste").baseUrl(api.url()).maxRetries(0).build(),
			new PropriedadesIa.OpenAi("chave-de-teste", "modelo-teste", null, "medium", new BigDecimal("2"),
					new BigDecimal("8"), new BigDecimal("0.5")),
			JsonMapper.builder().build());

	private final List<DefinicaoFerramenta> ferramentas = List.of(new DefinicaoFerramenta("listar_unidades",
			"Unidades.", Map.of("nome_contem", Map.of("type", List.of("string", "null"))), List.of("nome_contem")));

	@AfterEach
	void parar() {
		api.close();
	}

	@Test
	void conduzOLacoComFerramentasEstritasECalculaOCusto() {
		api.responder(OpenAiSimulado.ferramenta("listar_unidades", "{\"nome_contem\":\"Cidade\"}"),
				OpenAiSimulado.texto("Há 2 unidades [1]."));

		var resposta = assistente.responder(List.of(), "<pergunta>Quais cidades?</pergunta>", ferramentas,
				(nome, entrada) -> {
					assertThat(entrada).containsEntry("nome_contem", "Cidade");
					return new SaidaFerramenta("{\"fonte\":1,\"quantidade\":2}", false);
				});

		assertThat(resposta.texto()).isEqualTo("Há 2 unidades [1].");
		assertThat(resposta.modelo()).isEqualTo("modelo-teste");
		// 2 × (600 entrada, 200 saída, 400 do cache)
		assertThat(resposta.uso().entrada()).isEqualTo(1200);
		assertThat(resposta.uso().cacheLido()).isEqualTo(800);
		assertThat(resposta.custoUsd()).isEqualByComparingTo("0.0060");

		var primeiro = api.pedidos().getFirst();
		assertThat(primeiro.get("model").asString()).isEqualTo("modelo-teste");
		assertThat(primeiro.get("reasoning_effort").asString()).isEqualTo("medium");
		assertThat(primeiro.get("messages").get(0).get("role").asString()).isEqualTo("developer");
		assertThat(primeiro.get("messages").get(0).get("content").asString()).contains("assistente da Aurora");
		var funcao = primeiro.get("tools").get(0).get("function");
		assertThat(funcao.get("strict").asBoolean()).isTrue();
		assertThat(funcao.get("parameters").get("additionalProperties").asBoolean()).isFalse();

		var mensagens = api.pedidos().get(1).get("messages");
		assertThat(mensagens.get(2).get("tool_calls").get(0).get("id").asString()).isEqualTo("call_listar_unidades");
		assertThat(mensagens.get(3).get("role").asString()).isEqualTo("tool");
		assertThat(mensagens.get(3).get("tool_call_id").asString()).isEqualTo("call_listar_unidades");
	}

	@Test
	void recusaEErroDaApi() {
		api.responder(OpenAiSimulado.recusa());
		assertThat(assistente.responder(List.of(), "x", ferramentas, (n, e) -> null).recusada()).isTrue();

		api.responder("{\"error\":{\"message\":\"Incorrect API key\",\"type\":\"invalid_request_error\"}}");
		api.status(401);
		assertThatThrownBy(() -> assistente.responder(List.of(), "x", ferramentas, (n, e) -> null))
			.isInstanceOf(AssistenteIndisponivelException.class)
			.hasMessageContaining("chave");
	}

}
