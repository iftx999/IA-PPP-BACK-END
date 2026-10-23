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
 * API do Claude simulada para testes: devolve as respostas roteirizadas em ordem e guarda
 * os pedidos recebidos (para conferir ferramentas, cache, resultados enviados).
 */
public final class ClaudeSimulado implements AutoCloseable {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpServer servidor;

	private final Deque<String> respostas = new ArrayDeque<>();

	private final List<JsonNode> pedidos = new CopyOnWriteArrayList<>();

	private volatile int status = 200;

	public ClaudeSimulado() {
		try {
			servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException erro) {
			throw new UncheckedIOException(erro);
		}
		servidor.createContext("/v1/messages", troca -> {
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

	public ClaudeSimulado responder(String... roteiro) {
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

	/** Resposta final em texto. */
	public static String texto(String texto) {
		return mensagem("end_turn", "[{\"type\":\"text\",\"text\":" + JSON.writeValueAsString(texto) + "}]");
	}

	/** Pedido de ferramenta (a entrada em JSON). */
	public static String ferramenta(String nome, String entradaJson) {
		return mensagem("tool_use", "[{\"type\":\"tool_use\",\"id\":\"toolu_" + nome + "\",\"name\":\"" + nome
				+ "\",\"input\":" + entradaJson + "}]");
	}

	public static String recusa() {
		return mensagem("refusal", "[]");
	}

	private static String mensagem(String parada, String conteudo) {
		return """
				{"id":"msg_teste","type":"message","role":"assistant","model":"claude-opus-5-5","content":%s,
				 "stop_reason":"%s","stop_sequence":null,
				 "usage":{"input_tokens":1000,"output_tokens":200,"cache_read_input_tokens":3000,
				          "cache_creation_input_tokens":0}}""".formatted(conteudo, parada);
	}

	@Override
	public void close() {
		servidor.stop(0);
	}

}
