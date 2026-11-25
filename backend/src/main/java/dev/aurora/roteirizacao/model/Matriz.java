package dev.aurora.roteirizacao.model;

/**
 * Tempos (s) e distâncias (m) de cada ponto para cada ponto, num perfil de veículo.
 *
 * @param estimada algum par foi estimado em linha reta (fora da malha ou serviço de rotas
 * indisponível)
 */
public record Matriz(int[][] segundos, int[][] metros, boolean estimada) {

	public int tamanho() {
		return segundos.length;
	}

	public int segundos(int de, int para) {
		return segundos[de][para];
	}

	public int metros(int de, int para) {
		return metros[de][para];
	}

}
