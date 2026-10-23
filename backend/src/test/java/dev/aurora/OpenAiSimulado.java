package dev.aurora;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API de Chat Completions da OpenAI simulada para testes (respostas em ordem, pedidos
 * guardados).
 */
public final class OpenAiSimulado implements AutoCloseable {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpServer servidor;

	private final Deque<String> respostas = new ArrayDeque<>();

	private final List<JsonNode> pedidos = new CopyOnWriteArrayList<>();

	private volatile int status = 200;

	public OpenAiSimulado() {
		try {
			servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException erro) {
			throw new UncheckedIOException(erro);
		}
		servidor.createContext("/chat/completions", troca -> {
			pedidos.add(JSON.readTree(troca.getRequestBody().readAllBytes()));
			String resposta;
			synchronized (respostas) {
				resposta = respostas.isEmpty() ? texto("(sem roteiro)") : respostas.removeFirst();
			}
			var corpo = resposta.getBytes(StandardCharsets.UTF_8);
			troca.getResponseHeaders().add("Content-Type", "application/json");
			troca.sendResponseHeaders(status, corpo.length);
			troca.getResponseBody().write(corpo);
			troca.close();
		});
		servidor.start();
	}

	public String url() {
		return "http://127.0.0.1:" + servidor.getAddress().getPort();
	}

	public OpenAiSimulado responder(String... roteiro) {
		synchronized (respostas) {
			respostas.clear();
			respostas.addAll(List.of(roteiro));
		}
		pedidos.clear();
		status = 200;
		return this;
	}

	public void status(int status) {
		this.status = status;
	}

	public List<JsonNode> pedidos() {
		return List.copyOf(pedidos);
	}

	public static String texto(String texto) {
		return completar("stop",
				"{\"role\":\"assistant\",\"content\":" + JSON.writeValueAsString(texto) + ",\"refusal\":null}");
	}

	public static String ferramenta(String nome, String argumentosJson) {
		return completar("tool_calls", """
				{"role":"assistant","content":null,"refusal":null,"tool_calls":[{"id":"call_%1$s","type":"function",
				 "function":{"name":"%1$s","arguments":%2$s}}]}""".formatted(nome,
				JSON.writeValueAsString(argumentosJson)));
	}

	public static String recusa() {
		return completar("stop",
				"{\"role\":\"assistant\",\"content\":null,\"refusal\":\"Não posso ajudar com isso.\"}");
	}

	private static String completar(String motivo, String mensagem) {
		return """
				{"id":"chatcmpl-teste","object":"chat.completion","created":1761200000,"model":"modelo-teste",
				 "choices":[{"index":0,"message":%s,"finish_reason":"%s","logprobs":null}],
				 "usage":{"prompt_tokens":1000,"completion_tokens":200,"total_tokens":1200,
				          "prompt_tokens_details":{"cached_tokens":400}}}""".formatted(mensagem, motivo);
	}

	@Override
	public void close() {
		servidor.stop(0);
	}

}
