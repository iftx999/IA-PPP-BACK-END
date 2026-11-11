package dev.aurora.acesso.config;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Janela fixa de um minuto por chave, em memória (ADR 0037): sem escrita no banco a cada
 * requisição. Vale por instância da API; com várias réplicas o limite efetivo é
 * multiplicado pelo número delas.
 */
final class LimitadorDeRequisicoes {

	private static final long MINUTO_EM_MS = 60_000;

	private final int limitePorMinuto;

	private final Clock relogio;

	private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();

	private volatile long ultimaLimpeza;

	LimitadorDeRequisicoes(int limitePorMinuto, Clock relogio) {
		if (limitePorMinuto < 1) {
			throw new IllegalArgumentException("o limite por minuto precisa ser positivo");
		}
		this.limitePorMinuto = limitePorMinuto;
		this.relogio = relogio;
	}

	/** Conta a requisição e diz se ela ainda cabe no minuto atual. */
	boolean permitir(String chave) {
		var minuto = relogio.millis() / MINUTO_EM_MS;
		limparJanelasAntigas(minuto);
		var janela = janelas.compute(chave, (k, atual) -> atual == null || atual.minuto() != minuto
				? new Janela(minuto, new AtomicInteger()) : atual);
		return janela.contagem().incrementAndGet() <= limitePorMinuto;
	}

	/** Segundos até a próxima janela (cabeçalho Retry-After). */
	long segundosAteLiberar() {
		var restante = MINUTO_EM_MS - relogio.millis() % MINUTO_EM_MS;
		return Math.max(1, (restante + 999) / 1000);
	}

	int chavesEmMemoria() {
		return janelas.size();
	}

	/** Na virada do minuto, descarta as janelas que já passaram (memória limitada). */
	private void limparJanelasAntigas(long minuto) {
		if (minuto != ultimaLimpeza) {
			ultimaLimpeza = minuto;
			janelas.values().removeIf(j -> j.minuto() < minuto);
		}
	}

	private record Janela(long minuto, AtomicInteger contagem) {
	}

}
