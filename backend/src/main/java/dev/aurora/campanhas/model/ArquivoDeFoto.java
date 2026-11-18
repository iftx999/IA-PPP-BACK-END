package dev.aurora.campanhas.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

/**
 * Regras do arquivo de foto enviado em partes pelo app (10.9.3): intervalo de cada parte,
 * formatos aceitos e a assinatura real do conteúdo.
 */
public final class ArquivoDeFoto {

	public static final int TAMANHO_MAXIMO = 5 * 1024 * 1024;

	public static final int PARTE_MAXIMA = 1024 * 1024;

	static final Set<String> TIPOS = Set.of("image/jpeg", "image/png", "image/webp");

	private static final Pattern INTERVALO = Pattern.compile("^bytes (\\d{1,9})-(\\d{1,9})/(\\d{1,9})$");

	private ArquivoDeFoto() {
	}

	/** Intervalo da parte; o fim é inclusivo. */
	public record Faixa(int inicio, int fim, int total) {
	}

	public static Faixa intervalo(@Nullable String cabecalho, int tamanhoDaParte) {
		var m = INTERVALO.matcher(cabecalho == null ? "" : cabecalho.strip());
		if (!m.matches()) {
			throw new RegraVioladaException("Informe Content-Range: bytes inicio-fim/total.");
		}
		var faixa = new Faixa(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
		if (faixa.total() < 1 || faixa.total() > TAMANHO_MAXIMO) {
			throw new RegraVioladaException("A foto tem no máximo " + TAMANHO_MAXIMO / (1024 * 1024) + " MB.");
		}
		if (faixa.inicio() > faixa.fim() || faixa.fim() >= faixa.total()
				|| faixa.fim() - faixa.inicio() + 1 != tamanhoDaParte) {
			throw new RegraVioladaException("Content-Range não confere com a parte enviada.");
		}
		if (tamanhoDaParte > PARTE_MAXIMA) {
			throw new RegraVioladaException("Envie partes de até " + PARTE_MAXIMA / 1024 + " KB.");
		}
		return faixa;
	}

	public static String tipo(@Nullable String tipoMidia) {
		var tipo = tipoMidia == null ? "" : tipoMidia.split(";")[0].strip().toLowerCase(Locale.ROOT);
		if (!TIPOS.contains(tipo)) {
			throw new RegraVioladaException("Envie a foto em JPEG, PNG ou WebP.");
		}
		return tipo;
	}

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private static final byte[] RIFF = { 'R', 'I', 'F', 'F' };

	private static final byte[] WEBP = { 'W', 'E', 'B', 'P' };

	/** Assinatura do formato no começo do arquivo. */
	public static boolean tipoConfere(String tipo, byte[] c) {
		return switch (tipo) {
			case "image/jpeg" -> tem(c, 0, JPEG);
			case "image/png" -> tem(c, 0, PNG);
			case "image/webp" -> tem(c, 0, RIFF) && tem(c, 8, WEBP);
			default -> false;
		};
	}

	/** O conteúdo tem a assinatura a partir da posição. */
	static boolean tem(byte[] c, int desde, byte[] assinatura) {
		var fim = desde + assinatura.length;
		return c.length >= fim && Arrays.equals(c, desde, fim, assinatura, 0, assinatura.length);
	}

	public static String sha256(byte[] conteudo) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
		}
		catch (NoSuchAlgorithmException erro) {
			throw new IllegalStateException(erro);
		}
	}

}
