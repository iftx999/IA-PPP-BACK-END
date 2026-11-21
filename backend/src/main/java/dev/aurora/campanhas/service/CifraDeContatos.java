package dev.aurora.campanhas.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Telefone dos imóveis cifrado no banco (ADR 0052): AES-256-GCM com a chave
 * {@code aurora.seguranca.chave-contatos}, que fica fora do banco. Decifrado só no envio
 * do aviso de chegada.
 */
@Component
public class CifraDeContatos {

	private static final int TAMANHO_IV = 12;

	private final SecretKeySpec chave;

	private final SecureRandom aleatorio = new SecureRandom();

	CifraDeContatos(@Value("${aurora.seguranca.chave-contatos}") String segredo) throws GeneralSecurityException {
		if (segredo.length() < 32) {
			throw new IllegalArgumentException("aurora.seguranca.chave-contatos precisa ter ao menos 32 caracteres");
		}
		this.chave = new SecretKeySpec(
				MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8)), "AES");
	}

	public String cifrar(String telefone) {
		try {
			var iv = new byte[TAMANHO_IV];
			aleatorio.nextBytes(iv);
			var cifra = Cipher.getInstance("AES/GCM/NoPadding");
			cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, iv));
			var cifrado = cifra.doFinal(telefone.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder()
				.encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
		}
		catch (GeneralSecurityException erro) {
			throw new IllegalStateException("falha ao cifrar o contato", erro);
		}
	}

	/**
	 * @throws IllegalStateException se o valor foi alterado ou a chave mudou
	 */
	public String decifrar(String cifrado) {
		try {
			var tudo = Base64.getDecoder().decode(cifrado);
			var cifra = Cipher.getInstance("AES/GCM/NoPadding");
			cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, tudo, 0, TAMANHO_IV));
			return new String(cifra.doFinal(tudo, TAMANHO_IV, tudo.length - TAMANHO_IV), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException erro) {
			throw new IllegalStateException("contato ilegível (chave trocada?)", erro);
		}
	}

}
