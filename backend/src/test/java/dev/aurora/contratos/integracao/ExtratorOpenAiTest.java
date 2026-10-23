package dev.aurora.contratos.integracao;

import java.util.List;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import dev.aurora.OpenAiSimulado;
import dev.aurora.comum.PropriedadesIa;
import dev.aurora.contratos.model.Trecho;
import dev.aurora.contratos.service.ExtratorContrato.ExtracaoFalhouException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtratorOpenAiTest {

	private final OpenAiSimulado api = new OpenAiSimulado();

	private final ExtratorOpenAi extrator = new ExtratorOpenAi(
			OpenAIOkHttpClient.builder().apiKey("chave-de-teste").baseUrl(api.url()).maxRetries(0).build(),
			new PropriedadesIa.OpenAi("chave-de-teste", "modelo-teste", null, null, null, null, null));

	@AfterEach
	void parar() {
		api.close();
	}

	@Test
	void usaSaidaEstruturadaComAsMesmasInstrucoes() throws Exception {
		api.responder(OpenAiSimulado.texto("""
				{"itens":[{"tipo":"META","titulo":"Adesão mínima","resumo":"80% até 2026","clausula":"7.2","pagina":2,
				 "trecho":"A adesão mínima será de 80%","valorReferencia":"80","unidadeMedida":"%",
				 "dataLimite":"2026-12-31","periodicidade":"","unidadeAlvo":"Concessão A","valorAlerta":"",
				 "vigenciaInicio":"","confianca":0.9}],"observacoes":[]}"""));

		var resultado = extrator.extrair(List.of(new Trecho("7.2", 2, "A adesão mínima será de 80%")),
				List.of("Concessão A"));

		assertThat(resultado.propostas()).hasSize(1);
		assertThat(resultado.propostas().getFirst().valorReferencia()).isEqualByComparingTo("80");
		assertThat(resultado.modelo()).isEqualTo("modelo-teste");
		var pedido = api.pedidos().getFirst();
		assertThat(pedido.get("response_format").get("type").asString()).isEqualTo("json_schema");
		assertThat(pedido.get("response_format").get("json_schema").get("strict").asBoolean()).isTrue();
		assertThat(pedido.get("messages").get(0).get("content").asString()).isEqualTo(ExtratorClaude.INSTRUCOES);
		assertThat(pedido.has("reasoning_effort")).isFalse();
	}

	@Test
	void recusaViraFalhaDeExtracao() {
		api.responder(OpenAiSimulado.recusa());
		assertThatThrownBy(() -> extrator.extrair(List.of(new Trecho(null, 1, "texto")), List.of()))
			.isInstanceOf(ExtracaoFalhouException.class)
			.hasMessageContaining("recusou");
	}

}
