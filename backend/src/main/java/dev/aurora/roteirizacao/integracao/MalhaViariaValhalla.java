package dev.aurora.roteirizacao.integracao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.aurora.comum.Disjuntor;
import dev.aurora.comum.ServicoExternoIndisponivelException;
import dev.aurora.roteirizacao.model.Calculo;
import dev.aurora.roteirizacao.model.Coordenada;
import dev.aurora.roteirizacao.model.Matriz;
import dev.aurora.roteirizacao.model.PerfilRota;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Malha viária pelo Valhalla, com cache dos pares já calculados (a reotimização
 * reaproveita quase tudo) e reserva em linha reta: sem Valhalla configurado, com o
 * disjuntor aberto ou num bloco que falhou, o resultado sai como
 * {@link Calculo#LINHA_RETA}. Par sem caminho na malha (ponto fora dela) é estimado em
 * linha reta e marca a matriz como estimada.
 *
 * <p>
 * Matrizes grandes são pedidas em blocos de {@value ValhallaCliente#LADO_DO_BLOCO} ×
 * {@value ValhallaCliente#LADO_DO_BLOCO}, alguns em paralelo.
 */
public class MalhaViariaValhalla implements MalhaViaria, AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(MalhaViariaValhalla.class);

	private final @Nullable ValhallaCliente cliente;

	private final Disjuntor disjuntor;

	private final Cache cache;

	private final ExecutorService executor;

	private final Semaphore vagas;

	MalhaViariaValhalla(@Nullable ValhallaCliente cliente, Disjuntor disjuntor, int paresNoCache, int concorrencia,
			ExecutorService executor) {
		this.cliente = cliente;
		this.disjuntor = disjuntor;
		this.cache = new Cache(paresNoCache);
		this.executor = executor;
		this.vagas = new Semaphore(concorrencia);
	}

	@Override
	public Resultado matriz(List<Coordenada> pontos, PerfilRota perfil) {
		int n = pontos.size();
		var segundos = new int[n][n];
		var metros = new int[n][n];
		var cliente = this.cliente;
		if (cliente == null) {
			preencherEmLinhaReta(pontos, perfil, segundos, metros, 0, n, 0, n);
			return new Resultado(new Matriz(segundos, metros, true), Calculo.LINHA_RETA);
		}
		var blocos = new ArrayList<Future<?>>();
		var falhou = new AtomicBoolean();
		var estimada = new AtomicBoolean();
		int lado = ValhallaCliente.LADO_DO_BLOCO;
		for (int i0 = 0; i0 < n; i0 += lado) {
			for (int j0 = 0; j0 < n; j0 += lado) {
				int i1 = Math.min(n, i0 + lado);
				int j1 = Math.min(n, j0 + lado);
				if (completarDoCache(pontos, perfil, segundos, metros, i0, i1, j0, j1)) {
					continue;
				}
				int a0 = i0;
				int b0 = j0;
				blocos.add(executor.submit(() -> calcularBloco(cliente, pontos, perfil, segundos, metros, a0, i1, b0,
						j1, falhou, estimada)));
			}
		}
		for (var bloco : blocos) {
			try {
				bloco.get();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("cálculo da matriz interrompido", e);
			}
			catch (ExecutionException e) {
				throw new IllegalStateException("falha no cálculo da matriz", e.getCause());
			}
		}
		for (int i = 0; i < n; i++) {
			segundos[i][i] = 0;
			metros[i][i] = 0;
		}
		return new Resultado(new Matriz(segundos, metros, falhou.get() || estimada.get()),
				falhou.get() ? Calculo.LINHA_RETA : Calculo.VALHALLA);
	}

	@Override
	public List<List<Coordenada>> tracado(List<Coordenada> paradas, PerfilRota perfil) {
		var cliente = this.cliente;
		if (cliente == null || paradas.size() < 2) {
			return List.of();
		}
		var linhas = new ArrayList<List<Coordenada>>();
		int passo = ValhallaCliente.PARADAS_POR_ROTA - 1;
		try {
			for (int inicio = 0; inicio < paradas.size() - 1; inicio += passo) {
				var parte = paradas.subList(inicio,
						Math.min(paradas.size(), inicio + ValhallaCliente.PARADAS_POR_ROTA));
				linhas.addAll(disjuntor.executar(() -> cliente.rota(parte, perfil), Indisponivel::new));
			}
			return linhas;
		}
		catch (RuntimeException erro) {
			LOG.warn("Traçado da rota indisponível no Valhalla: {}", erro.getMessage());
			return List.of();
		}
	}

	@Override
	public void close() {
		executor.shutdownNow();
	}

	private void calcularBloco(ValhallaCliente cliente, List<Coordenada> pontos, PerfilRota perfil, int[][] segundos,
			int[][] metros, int i0, int i1, int j0, int j1, AtomicBoolean falhou, AtomicBoolean estimada) {
		var origens = pontos.subList(i0, i1);
		var destinos = pontos.subList(j0, j1);
		int[][][] bloco;
		try {
			vagas.acquire();
			try {
				bloco = disjuntor.executar(() -> cliente.matriz(origens, destinos, perfil), Indisponivel::new);
			}
			finally {
				vagas.release();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
		catch (RuntimeException erro) {
			if (!falhou.getAndSet(true)) {
				LOG.warn("Valhalla indisponível, deslocamentos em linha reta: {}", erro.getMessage());
			}
			preencherEmLinhaReta(pontos, perfil, segundos, metros, i0, i1, j0, j1);
			return;
		}
		for (int i = i0; i < i1; i++) {
			for (int j = j0; j < j1; j++) {
				int s = bloco[0][i - i0][j - j0];
				int m = bloco[1][i - i0][j - j0];
				if (s < 0 || m < 0) {
					if (i != j) {
						estimada.set(true);
					}
					segundos[i][j] = LinhaReta.segundos(pontos.get(i), pontos.get(j), perfil);
					metros[i][j] = LinhaReta.metros(pontos.get(i), pontos.get(j));
				}
				else {
					segundos[i][j] = s;
					metros[i][j] = m;
					cache.guardar(perfil, pontos.get(i), pontos.get(j), s, m);
				}
			}
		}
	}

	/** Preenche o bloco com o que está no cache; true se nenhum par faltou. */
	private boolean completarDoCache(List<Coordenada> pontos, PerfilRota perfil, int[][] segundos, int[][] metros,
			int i0, int i1, int j0, int j1) {
		for (int i = i0; i < i1; i++) {
			for (int j = j0; j < j1; j++) {
				if (i == j) {
					continue;
				}
				var guardado = cache.ler(perfil, pontos.get(i), pontos.get(j));
				if (guardado == null) {
					return false;
				}
				segundos[i][j] = (int) (guardado >>> 32);
				metros[i][j] = (int) (long) guardado;
			}
		}
		return true;
	}

	private static void preencherEmLinhaReta(List<Coordenada> pontos, PerfilRota perfil, int[][] segundos,
			int[][] metros, int i0, int i1, int j0, int j1) {
		for (int i = i0; i < i1; i++) {
			for (int j = j0; j < j1; j++) {
				segundos[i][j] = i == j ? 0 : LinhaReta.segundos(pontos.get(i), pontos.get(j), perfil);
				metros[i][j] = i == j ? 0 : LinhaReta.metros(pontos.get(i), pontos.get(j));
			}
		}
	}

	private static final class Indisponivel extends ServicoExternoIndisponivelException {

		Indisponivel() {
			super("Serviço de rotas indisponível.");
		}

	}

	/**
	 * Pares por perfil e coordenadas arredondadas a ~1 m; o mais antigo sai quando enche.
	 */
	static final class Cache {

		private final Map<Par, Long> pares;

		Cache(int maximo) {
			this.pares = new LinkedHashMap<>(1024, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Par, Long> mais) {
					return size() > maximo;
				}
			};
		}

		synchronized @Nullable Long ler(PerfilRota perfil, Coordenada de, Coordenada para) {
			return pares.get(Par.de(perfil, de, para));
		}

		synchronized void guardar(PerfilRota perfil, Coordenada de, Coordenada para, int segundos, int metros) {
			pares.put(Par.de(perfil, de, para), ((long) segundos << 32) | (metros & 0xffffffffL));
		}

		synchronized int tamanho() {
			return pares.size();
		}

	}

	record Par(int perfil, int latDe, int lonDe, int latPara, int lonPara) {

		static Par de(PerfilRota perfil, Coordenada de, Coordenada para) {
			return new Par(perfil.ordinal(), (int) Math.round(de.latitude() * 1e5),
					(int) Math.round(de.longitude() * 1e5), (int) Math.round(para.latitude() * 1e5),
					(int) Math.round(para.longitude() * 1e5));
		}
	}

}
