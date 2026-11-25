package dev.aurora.roteirizacao.integracao;

import java.util.ArrayList;
import java.util.List;

import dev.aurora.roteirizacao.model.Coordenada;

/** Decodifica o formato de traçado do Valhalla (polyline com 6 casas decimais). */
final class Polyline6 {

	private Polyline6() {
	}

	static List<Coordenada> decodificar(String codigo) {
		var pontos = new ArrayList<Coordenada>();
		int indice = 0;
		long lat = 0;
		long lon = 0;
		while (indice < codigo.length()) {
			long[] leitura = ler(codigo, indice);
			lat += leitura[0];
			leitura = ler(codigo, (int) leitura[1]);
			lon += leitura[0];
			indice = (int) leitura[1];
			pontos.add(new Coordenada(lat / 1e6, lon / 1e6));
		}
		return pontos;
	}

	/** Um número da sequência: {valor, próximo índice}. */
	private static long[] ler(String codigo, int inicio) {
		long resultado = 0;
		int deslocamento = 0;
		int indice = inicio;
		int b;
		do {
			if (indice >= codigo.length()) {
				throw new IllegalArgumentException("traçado truncado");
			}
			b = codigo.charAt(indice++) - 63;
			resultado |= (long) (b & 0x1f) << deslocamento;
			deslocamento += 5;
		}
		while (b >= 0x20);
		long valor = (resultado & 1) != 0 ? ~(resultado >> 1) : (resultado >> 1);
		return new long[] { valor, indice };
	}

}
