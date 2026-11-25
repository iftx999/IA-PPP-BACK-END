package dev.aurora.campanhas.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.servicos.ParametroDaOrdem;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * RN-22: valores dos parâmetros da versão do serviço no encerramento executado. Valida
 * tipo, obrigatoriedade, faixa (valor, caracteres ou quantidade de fotos) e opções; o
 * parâmetro com condição só vale quando o outro tem o valor indicado (os demais são
 * descartados). Fotos são ids dos anexos enviados depois (10.9.3).
 *
 * @param valores chave → valor, só dos parâmetros que valem
 * @param fotos id do anexo → chave do parâmetro de foto
 */
public record ValoresDoEncerramento(ObjectNode valores, Map<UUID, String> fotos) {

	static final int MAXIMO_DE_TEXTO = 500;

	public ValoresDoEncerramento {
		fotos = Map.copyOf(fotos);
	}

	public static ValoresDoEncerramento validar(List<ParametroDaOrdem> parametros, @Nullable JsonNode enviados) {
		if (enviados != null && !enviados.isNull() && !enviados.isObject()) {
			throw new RegraVioladaException("Os valores dos parâmetros vêm em um objeto (chave → valor).");
		}
		var porChave = new HashMap<String, ParametroDaOrdem>();
		parametros.forEach(p -> porChave.put(p.chave(), p));
		if (enviados != null) {
			for (var chave : enviados.propertyNames()) {
				if (!porChave.containsKey(chave)) {
					throw new RegraVioladaException("Parâmetro \"" + chave + "\" não existe nesta versão do serviço.");
				}
			}
		}
		var saida = JsonNodeFactory.instance.objectNode();
		var comparaveis = new HashMap<String, String>();
		var fotos = new LinkedHashMap<UUID, String>();
		for (var p : parametros) {
			if (!vale(p, comparaveis)) {
				continue;
			}
			var valor = enviados == null ? null : enviados.get(p.chave());
			if (vazio(valor)) {
				if (p.obrigatorio()) {
					throw new RegraVioladaException("Preencha \"" + p.rotulo() + "\".");
				}
				continue;
			}
			var limpo = validarValor(p, valor, fotos);
			saida.set(p.chave(), limpo);
			if ("SIM_NAO".equals(p.tipo()) || "LISTA".equals(p.tipo())) {
				comparaveis.put(p.chave(), limpo.asString());
			}
		}
		return new ValoresDoEncerramento(saida, fotos);
	}

	/** Sem condição, ou o parâmetro da condição (anterior) tem o valor indicado. */
	private static boolean vale(ParametroDaOrdem p, Map<String, String> comparaveis) {
		return p.condicaoChave() == null
				|| String.valueOf(p.condicaoValor()).equals(comparaveis.get(p.condicaoChave()));
	}

	private static boolean vazio(@Nullable JsonNode valor) {
		return valor == null || valor.isNull() || (valor.isString() && valor.stringValue().isBlank())
				|| (valor.isArray() && valor.isEmpty());
	}

	private static JsonNode validarValor(ParametroDaOrdem p, JsonNode valor, Map<UUID, String> fotos) {
		var f = JsonNodeFactory.instance;
		return switch (p.tipo()) {
			case "INTEIRO", "DECIMAL" -> f.numberNode(numero(p, valor));
			case "TEXTO" -> f.stringNode(texto(p, valor));
			case "LISTA" -> f.stringNode(opcao(p, valor));
			case "LISTA_MULTIPLA" -> {
				var lista = f.arrayNode();
				opcoes(p, valor).forEach(lista::add);
				yield lista;
			}
			case "SIM_NAO" -> {
				if (!valor.isBoolean()) {
					throw invalido(p, "responda sim ou não");
				}
				yield f.booleanNode(valor.booleanValue());
			}
			case "DATA_HORA" -> f.stringNode(dataHora(p, valor));
			case "FOTO" -> {
				var lista = f.arrayNode();
				for (var id : fotos(p, valor)) {
					if (fotos.putIfAbsent(id, p.chave()) != null) {
						throw invalido(p, "a mesma foto aparece em mais de um parâmetro");
					}
					lista.add(id.toString());
				}
				yield lista;
			}
			default -> throw new IllegalStateException("Tipo de parâmetro desconhecido: " + p.tipo());
		};
	}

	private static BigDecimal numero(ParametroDaOrdem p, JsonNode valor) {
		if (!valor.isNumber()) {
			throw invalido(p, "informe um número");
		}
		var numero = valor.decimalValue();
		if ("INTEIRO".equals(p.tipo()) && numero.stripTrailingZeros().scale() > 0) {
			throw invalido(p, "informe um número inteiro");
		}
		if ((p.minimo() != null && numero.compareTo(p.minimo()) < 0)
				|| (p.maximo() != null && numero.compareTo(p.maximo()) > 0)) {
			throw invalido(p, "fora da faixa " + faixa(p));
		}
		return numero;
	}

	private static String texto(ParametroDaOrdem p, JsonNode valor) {
		if (!valor.isString()) {
			throw invalido(p, "informe um texto");
		}
		var texto = valor.stringValue().strip();
		var maximo = p.maximo() == null ? MAXIMO_DE_TEXTO : p.maximo().intValue();
		var minimo = p.minimo() == null ? 0 : p.minimo().intValue();
		if (texto.length() < minimo || texto.length() > maximo) {
			throw invalido(p, "de " + minimo + " a " + maximo + " caracteres");
		}
		return texto;
	}

	private static String opcao(ParametroDaOrdem p, JsonNode valor) {
		if (!valor.isString() || !p.opcoes().contains(valor.stringValue())) {
			throw invalido(p, "escolha uma das opções");
		}
		return valor.stringValue();
	}

	private static List<String> opcoes(ParametroDaOrdem p, JsonNode valor) {
		if (!valor.isArray()) {
			throw invalido(p, "escolha as opções em uma lista");
		}
		var escolhidas = new ArrayList<String>();
		for (var item : valor.values()) {
			var opcao = opcao(p, item);
			if (escolhidas.contains(opcao)) {
				throw invalido(p, "opção repetida");
			}
			escolhidas.add(opcao);
		}
		return escolhidas;
	}

	private static String dataHora(ParametroDaOrdem p, JsonNode valor) {
		try {
			return OffsetDateTime.parse(valor.isString() ? valor.stringValue() : "").toInstant().toString();
		}
		catch (DateTimeParseException erro) {
			throw invalido(p, "informe data e hora com fuso (ISO 8601)");
		}
	}

	private static List<UUID> fotos(ParametroDaOrdem p, JsonNode valor) {
		if (!valor.isArray()) {
			throw invalido(p, "envie as fotos em uma lista");
		}
		var ids = new ArrayList<UUID>();
		var vistos = new HashSet<UUID>();
		for (var item : valor.values()) {
			UUID id;
			try {
				id = UUID.fromString(item.isString() ? item.stringValue() : "");
			}
			catch (IllegalArgumentException erro) {
				throw invalido(p, "identificador de foto inválido");
			}
			if (vistos.add(id)) {
				ids.add(id);
			}
		}
		// Vazio já foi tratado: obrigatório tem ao menos uma.
		var minimo = p.minimo() == null ? 0 : p.minimo().intValue();
		var maximo = p.maximo() == null ? Integer.MAX_VALUE : p.maximo().intValue();
		if (ids.size() < minimo || ids.size() > maximo) {
			throw invalido(p, maximo == Integer.MAX_VALUE ? "ao menos " + minimo + " foto(s)"
					: "de " + minimo + " a " + maximo + " fotos");
		}
		return ids;
	}

	private static String faixa(ParametroDaOrdem p) {
		var min = p.minimo() == null ? "-∞" : p.minimo().toPlainString();
		var max = p.maximo() == null ? "∞" : p.maximo().toPlainString();
		return min + " a " + max + (p.unidade() == null ? "" : " " + p.unidade());
	}

	private static RegraVioladaException invalido(ParametroDaOrdem p, String detalhe) {
		return new RegraVioladaException("\"" + p.rotulo() + "\": " + detalhe + ".");
	}

}
