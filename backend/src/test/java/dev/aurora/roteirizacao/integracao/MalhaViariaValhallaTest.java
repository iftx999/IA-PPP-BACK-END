package dev.aurora.roteirizacao.integracao;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dev.aurora.comum.Disjuntor;
import dev.aurora.roteirizacao.model.Calculo;
import dev.aurora.roteirizacao.model.Coordenada;
import dev.aurora.roteirizacao.model.PerfilRota;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MalhaViariaValhallaTest {

	private static final List<Coordenada> PONTOS = List.of(new Coordenada(-22.90, -47.06),
			new Coordenada(-22.90, -47.05), new Coordenada(-22.91, -47.05));

	/** Valhalla falso: 100 s e 1 km por par; o par 0→2 sem caminho; ou falha. */
	static final class ValhallaFalso extends ValhallaCliente {

		final AtomicInteger chamadas = new AtomicInteger();

		boolean fora;

		ValhallaFalso() {
			super(null);
		}

		@Override
		int[][][] matriz(List<Coordenada> origens, List<Coordenada> destinos, PerfilRota perfil) {
			chamadas.incrementAndGet();
			if (fora) {
				throw new IllegalStateException("conexão recusada");
			}
			var s = new int[origens.size()][destinos.size()];
			var m = new int[origens.size()][destinos.size()];
			for (int i = 0; i < origens.size(); i++) {
				for (int j = 0; j < destinos.size(); j++) {
					boolean semCaminho = origens.get(i).equals(PONTOS.get(0)) && destinos.get(j).equals(PONTOS.get(2));
					s[i][j] = semCaminho ? -1 : (i == j ? 0 : 100);
					m[i][j] = semCaminho ? -1 : (i == j ? 0 : 1000);
				}
			}
			return new int[][][] { s, m };
		}

	}

	private static MalhaViariaValhalla malha(ValhallaCliente cliente) {
		return new MalhaViariaValhalla(cliente, Disjuntor.isolado("teste", e -> false), 1000, 2,
				Executors.newVirtualThreadPerTaskExecutor());
	}

	@Test
	void usaOValhallaGuardaNoCacheEEstimaParSemCaminho() {
		var falso = new ValhallaFalso();
		var malha = malha(falso);

		var primeira = malha.matriz(PONTOS, PerfilRota.CARRO);

		assertThat(primeira.calculo()).isEqualTo(Calculo.VALHALLA);
		assertThat(primeira.matriz().segundos(0, 1)).isEqualTo(100);
		assertThat(primeira.matriz().metros(1, 0)).isEqualTo(1000);
		assertThat(primeira.matriz().segundos(0, 0)).isZero();
		// 0→2 sem caminho na malha: linha reta (~1,5 km × 1,35) e matriz marcada como
		// estimada.
		assertThat(primeira.matriz().metros(0, 2)).isBetween(1800, 2300);
		assertThat(primeira.matriz().estimada()).isTrue();
		assertThat(falso.chamadas).hasValue(1);

		// Segundo pedido: o par sem caminho não fica no cache, então pede de novo; os
		// demais vêm dele.
		malha.matriz(PONTOS.subList(0, 2), PerfilRota.CARRO);
		assertThat(falso.chamadas).hasValue(1);
		malha.matriz(PONTOS.subList(0, 2), PerfilRota.CAMINHAO);
		assertThat(falso.chamadas).hasValue(2);
	}

	@Test
	void foraDoArCaiParaLinhaReta() {
		var falso = new ValhallaFalso();
		falso.fora = true;

		var resultado = malha(falso).matriz(PONTOS, PerfilRota.MOTO);

		assertThat(resultado.calculo()).isEqualTo(Calculo.LINHA_RETA);
		assertThat(resultado.matriz().estimada()).isTrue();
		// ~1 km em linha reta × 1,35 a 30 km/h ≈ 2,7 min.
		assertThat(resultado.matriz().segundos(0, 1)).isBetween(130, 200);
	}

	@Test
	void semValhallaConfiguradoEhLinhaRetaSemTracado() {
		var malha = malha(null);

		assertThat(malha.matriz(PONTOS, PerfilRota.CARRO).calculo()).isEqualTo(Calculo.LINHA_RETA);
		assertThat(malha.tracado(PONTOS, PerfilRota.CARRO)).isEmpty();
	}

	@Test
	void decodificaOTracadoComSeisCasas() {
		// Exemplo clássico do formato polyline (5 casas) lido com 6: valores divididos
		// por 10.
		var pontos = Polyline6.decodificar("_p~iF~ps|U_ulLnnqC_mqNvxq`@");

		assertThat(pontos).hasSize(3);
		assertThat(pontos.get(0).latitude()).isCloseTo(3.85, org.assertj.core.data.Offset.offset(1e-9));
		assertThat(pontos.get(2).longitude()).isCloseTo(-12.6453, org.assertj.core.data.Offset.offset(1e-9));
	}

}
