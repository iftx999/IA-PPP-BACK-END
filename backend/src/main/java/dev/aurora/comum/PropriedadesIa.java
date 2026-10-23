package dev.aurora.comum;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provedor de IA da extração de contratos e do assistente (ADR 0014, 0018, 0019). Chaves
 * vêm do cofre de segredos (variáveis de ambiente), nunca do repositório.
 *
 * @param provedor {@code claude} (padrão) ou {@code openai}
 * @param chave chave da API da Anthropic; vazia desliga a IA quando o provedor é Claude
 * @param modelo modelo do Claude
 * @param urlBase endereço da API do Claude (vazio = padrão; outro valor só para testes ou
 * proxy)
 */
@ConfigurationProperties("aurora.ia")
public record PropriedadesIa(@Nullable String provedor, @Nullable String chave, @Nullable String modelo,
		@Nullable String urlBase, @Nullable OpenAi openai) {

	/**
	 * @param modelo obrigatório com o provedor OpenAI (sem padrão: a escolha é da
	 * empresa)
	 * @param esforco esforço de raciocínio (low, medium, high) para modelos que o
	 * aceitam; vazio = não enviar
	 * @param precoEntrada dólares por milhão de tokens de entrada (sem preço, o uso é
	 * contado sem custo)
	 */
	public record OpenAi(@Nullable String chave, @Nullable String modelo, @Nullable String urlBase,
			@Nullable String esforco, @Nullable BigDecimal precoEntrada, @Nullable BigDecimal precoSaida,
			@Nullable BigDecimal precoCacheLido) {
	}

	public boolean usaOpenAi() {
		return "openai".equalsIgnoreCase(provedor == null ? "" : provedor.strip());
	}

	public OpenAi openAi() {
		return openai == null ? new OpenAi(null, null, null, null, null, null, null) : openai;
	}

	/**
	 * Configuração incompleta do provedor escolhido deixa a IA desligada (com aviso no
	 * log).
	 */
	public boolean ligada() {
		if (usaOpenAi()) {
			return preenchido(openAi().chave()) && preenchido(openAi().modelo());
		}
		return preenchido(chave);
	}

	public static boolean preenchido(@Nullable String valor) {
		return valor != null && !valor.isBlank();
	}

}
