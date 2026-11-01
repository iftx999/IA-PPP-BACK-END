package dev.aurora.comum;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RespostaDeProblemaTest {

	@Test
	void mensagemComAspasNaoQuebraNemInjetaCampos() throws Exception {
		var resposta = new MockHttpServletResponse();
		RespostaDeProblema.escrever(resposta, HttpStatus.FORBIDDEN, "texto com \"aspas\", \"codigo\":\"X\"", "OTP");

		assertThat(resposta.getStatus()).isEqualTo(403);
		assertThat(resposta.getContentType()).startsWith("application/problem+json");
		assertThat(resposta.getContentAsString()).isEqualTo("""
				{"type":"about:blank","title":"Forbidden","status":403,\
				"detail":"texto com \\"aspas\\", \\"codigo\\":\\"X\\"","codigo":"OTP"}""");
	}

}
