package dev.aurora.roteirizacao.integracao;

import dev.aurora.roteirizacao.model.Coordenada;
import dev.aurora.roteirizacao.model.PerfilRota;

/**
 * Estimativa sem malha viária: distância em linha reta × fator de sinuosidade urbana, a
 * uma velocidade média por perfil. Usada quando o Valhalla está desligado, fora do ar ou
 * não acha caminho entre dois pontos.
 */
final class LinhaReta {

	/** Ruas não são retas: o caminho real fica ~35 % maior em malha urbana. */
	static final double SINUOSIDADE = 1.35;

	private LinhaReta() {
	}

	static int metros(Coordenada de, Coordenada para) {
		return (int) Math.round(de.distanciaM(para) * SINUOSIDADE);
	}

	static int segundos(Coordenada de, Coordenada para, PerfilRota perfil) {
		return (int) Math.round(metros(de, para) / (kmh(perfil) / 3.6));
	}

	/** Velocidade média urbana, com paradas e semáforos. */
	static double kmh(PerfilRota perfil) {
		return switch (perfil) {
			case MOTO -> 30;
			case CARRO -> 27;
			case CAMINHAO -> 20;
		};
	}

}
