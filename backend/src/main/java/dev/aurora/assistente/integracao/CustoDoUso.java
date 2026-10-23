package dev.aurora.assistente.integracao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

import dev.aurora.assistente.model.Uso;

/**
 * Custo em dólares de uma pergunta no Claude, pela tabela de preços por milhão de tokens
 * (out/2026; conferir a cada troca de modelo). Gravação em cache custa 1,25 × a entrada
 * (cache de 5 minutos). O fallback do servidor pode responder com outro modelo: modelo
 * fora da tabela fica "sem custo" no relatório de uso, em vez de um valor errado.
 */
final class CustoDoUso {

	private record Preco(String entrada, String saida, String cacheLido) {
	}

	private static final Map<String, Preco> PRECOS = Map.of("claude-opus-5-5", new Preco("4", "20", "0.20"),
			"claude-opus-5", new Preco("5", "25", "0.50"), "claude-opus-4-8", new Preco("5", "25", "0.50"),
			"claude-sonnet-5-5", new Preco("2", "10", "0.20"), "claude-fable-5-1", new Preco("10", "50", "0.25"));

	private static final BigDecimal MILHAO = BigDecimal.valueOf(1_000_000);

	private CustoDoUso() {
	}

	static Optional<BigDecimal> calcular(String modelo, Uso uso) {
		var preco = PRECOS.get(modelo);
		if (preco == null) {
			return Optional.empty();
		}
		var entrada = new BigDecimal(preco.entrada());
		var custo = entrada.multiply(BigDecimal.valueOf(uso.entrada()))
			.add(new BigDecimal(preco.saida()).multiply(BigDecimal.valueOf(uso.saida())))
			.add(new BigDecimal(preco.cacheLido()).multiply(BigDecimal.valueOf(uso.cacheLido())))
			.add(entrada.multiply(new BigDecimal("1.25")).multiply(BigDecimal.valueOf(uso.cacheEscrito())));
		return Optional.of(custo.divide(MILHAO, 6, RoundingMode.HALF_UP));
	}

}
