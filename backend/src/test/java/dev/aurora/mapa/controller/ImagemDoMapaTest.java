package dev.aurora.mapa.controller;

import java.util.Base64;

import dev.aurora.comum.RegraVioladaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagemDoMapaTest {

	@Test
	void conferePngPeloCabecalhoAntesDeDecodificar() {
		assertThat(ExportacaoMapaController.imagem(null)).isNull();
		assertThat(ExportacaoMapaController.imagem(" ")).isNull();
		assertThat(ExportacaoMapaController.imagem("data:image/png;base64," + png(800, 600))).hasSizeGreaterThan(24);
		assertThatThrownBy(() -> ExportacaoMapaController.imagem(png(4097, 10)))
			.isInstanceOf(RegraVioladaException.class)
			.hasMessageContaining("4096");
		assertThatThrownBy(() -> ExportacaoMapaController.imagem("%%%")).isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> ExportacaoMapaController.imagem(Base64.getEncoder().encodeToString(new byte[30])))
			.hasMessageContaining("PNG");
	}

	/** Só o cabeçalho (assinatura + IHDR com largura e altura). */
	private static String png(int largura, int altura) {
		var bytes = new byte[33];
		var assinatura = new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D',
				'R' };
		System.arraycopy(assinatura, 0, bytes, 0, assinatura.length);
		escrever(bytes, 16, largura);
		escrever(bytes, 20, altura);
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static void escrever(byte[] bytes, int posicao, int valor) {
		bytes[posicao] = (byte) (valor >>> 24);
		bytes[posicao + 1] = (byte) (valor >>> 16);
		bytes[posicao + 2] = (byte) (valor >>> 8);
		bytes[posicao + 3] = (byte) valor;
	}

}
