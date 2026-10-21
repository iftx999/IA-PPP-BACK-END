package dev.aurora.auditoria.service;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Formato 2 da cadeia: HMAC-SHA256(chave, hash_anterior || JSON canônico do evento). O
 * JSON tem as chaves em ordem alfabética (inclusive dentro de {@code detalhes}) e
 * {@code ocorridoEm} em ISO-8601 UTC com microssegundos, a mesma precisão do PostgreSQL.
 */
public class AssinaturaEvento {

	public static final int FORMATO = 2;

	private static final String ALGORITMO = "HmacSHA256";

	private final SecretKeySpec chave;

	private final JsonMapper json;

	public AssinaturaEvento(byte[] chave, JsonMapper json) {
		if (chave.length < 32) {
			throw new IllegalArgumentException("a chave da auditoria precisa ter ao menos 32 bytes");
		}
		this.chave = new SecretKeySpec(chave, ALGORITMO);
		this.json = json;
	}

	public byte[] assinar(byte[] hashAnterior, UUID tenantId, long sequencia, Instant ocorridoEm,
			@Nullable UUID usuarioId, String tipo, @Nullable String recurso, Map<String, ?> detalhes,
			@Nullable String ipOrigem) {
		var evento = new LinkedHashMap<String, Object>();
		evento.put("detalhes", ordenar(detalhes));
		evento.put("ipOrigem", ipOrigem);
		evento.put("ocorridoEm", ocorridoEm.toString());
		evento.put("recurso", recurso);
		evento.put("sequencia", sequencia);
		evento.put("tenantId", tenantId.toString());
		evento.put("tipo", tipo);
		evento.put("usuarioId", usuarioId == null ? null : usuarioId.toString());
		try {
			var mac = Mac.getInstance(ALGORITMO);
			mac.init(chave);
			mac.update(hashAnterior);
			return mac.doFinal(json.writeValueAsBytes(evento));
		}
		catch (GeneralSecurityException erro) {
			throw new IllegalStateException("falha ao assinar o evento de auditoria", erro);
		}
	}

	@SuppressWarnings("unchecked")
	private static Object ordenar(@Nullable Object valor) {
		if (valor instanceof Map<?, ?> mapa) {
			var ordenado = new TreeMap<String, Object>();
			((Map<Object, Object>) mapa).forEach((k, v) -> ordenado.put(String.valueOf(k), ordenar(v)));
			return ordenado;
		}
		return valor;
	}

}
