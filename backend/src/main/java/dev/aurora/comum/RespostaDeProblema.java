package dev.aurora.comum;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Problem Details (RFC 9457) escrito direto na resposta, para filtros que recusam antes
 * do Spring MVC. O JSON é gerado pelo Jackson: a mensagem nunca quebra o documento nem
 * injeta campos.
 */
public final class RespostaDeProblema {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private RespostaDeProblema() {
	}

	/**
	 * @param codigo código para o front tratar o caso (ex.: segundo fator), opcional
	 */
	public static void escrever(HttpServletResponse resposta, HttpStatus status, String detalhe,
			@Nullable String codigo) throws IOException {
		var corpo = new LinkedHashMap<String, Object>();
		corpo.put("type", "about:blank");
		corpo.put("title", status.getReasonPhrase());
		corpo.put("status", status.value());
		corpo.put("detail", detalhe);
		if (codigo != null) {
			corpo.put("codigo", codigo);
		}
		resposta.setStatus(status.value());
		resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
		resposta.getWriter().write(JSON.writeValueAsString(corpo));
	}

}
