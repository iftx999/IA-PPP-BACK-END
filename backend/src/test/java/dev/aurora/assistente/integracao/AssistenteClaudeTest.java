package dev.aurora.assistente.integracao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import dev.aurora.ClaudeSimulado;
import dev.aurora.assistente.model.DefinicaoFerramenta;
import dev.aurora.assistente.model.Papel;
import dev.aurora.assistente.model.SaidaFerramenta;
import dev.aurora.assistente.model.TurnoAnterior;
import dev.aurora.assistente.service.AssistenteIndisponivelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistenteClaudeTest {

	private final ClaudeSimulado api = new ClaudeSimulado();

	private final AssistenteClaude assistente = new AssistenteClaude(
			AnthropicOkHttpClient.builder().apiKey("chave-de-teste").baseUrl(api.url()).maxRetries(0).build(),
			"claude-opus-5-5");

	private final List<DefinicaoFerramenta> ferramentas = List
		.of(new DefinicaoFerramenta("listar_competencias", "Meses com dados.", Map.of(), List.of()));

	@AfterEach
	void parar() {
		api.close();
	}

	@Test
	void conduzOLacoDeFerramentasESomaOUso() {
		api.responder(ClaudeSimulado.ferramenta("listar_competencias", "{}"),
				ClaudeSimulado.texto("Há dados de ago/2026 [1]."));
		var chamadas = new ArrayList<String>();

		var resposta = assistente.responder(
				List.of(new TurnoAnterior(Papel.USUARIO, "Oi"), new TurnoAnterior(Papel.ASSISTENTE, "Olá [3].")),
				"<pergunta>Quais meses?</pergunta>", ferramentas, (nome, entrada) -> {
					chamadas.add(nome);
					return new SaidaFerramenta("{\"fonte\":1,\"competencias\":[\"2026-08\"]}", false);
				});

		assertThat(resposta.texto()).isEqualTo("Há dados de ago/2026 [1].");
		assertThat(resposta.recusada()).isFalse();
		assertThat(resposta.uso().entrada()).isEqualTo(2000);
		assertThat(resposta.uso().cacheLido()).isEqualTo(6000);
		assertThat(chamadas).containsExactly("listar_competencias");

		var primeiro = api.pedidos().getFirst();
		assertThat(primeiro.get("model").asString()).isEqualTo("claude-opus-5-5");
		assertThat(primeiro.get("output_config").get("effort").asString()).isEqualTo("medium");
		assertThat(primeiro.get("fallbacks").asString()).isEqualTo("default");
		assertThat(primeiro.get("system").get(0).get("cache_control").get("type").asString()).isEqualTo("ephemeral");
		var ferramenta = primeiro.get("tools").get(0);
		assertThat(ferramenta.get("strict").asBoolean()).isTrue();
		assertThat(ferramenta.get("input_schema").get("additionalProperties").asBoolean()).isFalse();
		// Histórico só com texto; marcas de fontes antigas removidas.
		assertThat(primeiro.get("messages").get(1).toString()).contains("Olá").doesNotContain("[3]");

		var segundo = api.pedidos().get(1);
		var mensagens = segundo.get("messages");
		assertThat(mensagens.get(3).get("content").get(0).get("type").asString()).isEqualTo("tool_use");
		var resultado = mensagens.get(4).get("content").get(0);
		assertThat(resultado.get("type").asString()).isEqualTo("tool_result");
		assertThat(resultado.get("tool_use_id").asString()).isEqualTo("toolu_listar_competencias");
		assertThat(resultado.get("content").toString()).contains("2026-08");
	}

	@Test
	void recusaNaoLeConteudoEErrosViramIndisponibilidade() {
		api.responder(ClaudeSimulado.recusa());
		assertThat(assistente.responder(List.of(), "x", ferramentas, (n, e) -> null).recusada()).isTrue();

		api.responder("""
				{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""");
		api.status(529);
		assertThatThrownBy(() -> assistente.responder(List.of(), "x", ferramentas, (n, e) -> null))
			.isInstanceOf(AssistenteIndisponivelException.class)
			.hasMessageContaining("Tente de novo");
	}

	@Test
	void desisteDepoisDoMaximoDeRodadas() {
		var roteiro = new String[AssistenteClaude.MAXIMO_DE_RODADAS];
		java.util.Arrays.fill(roteiro, ClaudeSimulado.ferramenta("listar_competencias", "{}"));
		api.responder(roteiro);
		assertThatThrownBy(
				() -> assistente.responder(List.of(), "x", ferramentas, (n, e) -> new SaidaFerramenta("{}", false)))
			.isInstanceOf(AssistenteIndisponivelException.class)
			.hasMessageContaining("Reformule");
	}

}
