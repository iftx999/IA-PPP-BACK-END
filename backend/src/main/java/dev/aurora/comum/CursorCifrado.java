package dev.aurora.comum;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cursor opaco para paginação por chave (keyset): o valor da chave (ex.: a matrícula do
 * último item) é cifrado com AES-GCM, então não aparece na URL nem pode ser forjado.
 * Cursor inválido ou adulterado é tratado como ausente.
 */
@Component
public class CursorCifrado {

	private static final int TAMANHO_IV = 12;

	private static final int TAMANHO_TAG_BITS = 128;

	private final SecretKeySpec chave;

	private final SecureRandom aleatorio = new SecureRandom();

	CursorCifrado(@Value("${aurora.seguranca.chave-cursor}") String segredo) throws GeneralSecurityException {
		if (segredo.length() < 32) {
			throw new IllegalArgumentException("aurora.seguranca.chave-cursor precisa ter ao menos 32 caracteres");
		}
		var hash = MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8));
		this.chave = new SecretKeySpec(hash, "AES");
	}

	public String cifrar(String valor) {
		try {
			var iv = new byte[TAMANHO_IV];
			aleatorio.nextBytes(iv);
			var cifra = Cipher.getInstance("AES/GCM/NoPadding");
			cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
			var cifrado = cifra.doFinal(valor.getBytes(StandardCharsets.UTF_8));
			var tudo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
			return Base64.getUrlEncoder().withoutPadding().encodeToString(tudo);
		}
		catch (GeneralSecurityException erro) {
			throw new IllegalStateException("falha ao cifrar o cursor", erro);
		}
	}

	public Optional<String> decifrar(String cursor) {
		try {
			var tudo = Base64.getUrlDecoder().decode(cursor);
			if (tudo.length <= TAMANHO_IV) {
				return Optional.empty();
			}
			var cifra = Cipher.getInstance("AES/GCM/NoPadding");
			cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, tudo, 0, TAMANHO_IV));
			var claro = cifra.doFinal(tudo, TAMANHO_IV, tudo.length - TAMANHO_IV);
			return Optional.of(new String(claro, StandardCharsets.UTF_8));
		}
		catch (IllegalArgumentException | GeneralSecurityException erro) {
			return Optional.empty();
		}
	}

}
