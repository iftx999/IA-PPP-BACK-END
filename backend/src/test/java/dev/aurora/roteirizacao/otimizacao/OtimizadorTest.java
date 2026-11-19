package dev.aurora.roteirizacao.otimizacao;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.aurora.roteirizacao.model.Matriz;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RN-25 com uma malha sintética: pontos numa linha, 60 s entre vizinhos. Ponto 0 e 9 são
 * as partidas das equipes A e B; 1 a 8, as OS.
 */
class OtimizadorTest {

	private static final UUID A = UUID.randomUUID();

	private static final UUID B = UUID.randomUUID();

	private static final long OITO_HORAS = 8 * 3600;

	private final Otimizador otimizador = new Otimizador();

	private static Matriz linha(int n, int segundosPorPasso) {
		var s = new int[n][n];
		var m = new int[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				s[i][j] = Math.abs(i - j) * segundosPorPasso;
				m[i][j] = Math.abs(i - j) * 500;
			}
		}
		return new Matriz(s, m, false);
	}

	private static Parada parada(int ponto, PrioridadeDaParada prioridade, int minutos, Set<UUID> equipes) {
		return new Parada(UUID.randomUUID(), prioridade, ponto, ponto, minutos * 60, Parada.SEM_PRAZO, equipes);
	}

	private PlanoDoDia resolver(PlanoDoDia plano) {
		return otimizador.resolver(plano, Duration.ofSeconds(3), Duration.ofSeconds(1));
	}

	@Test
	void cadaOsVaiParaAEquipeMaisPertoECompativel() {
		var matriz = linha(10, 60);
		var a = new RotaDaEquipe(A, matriz, 0, OITO_HORAS, OITO_HORAS + 8 * 3600, 0);
		var b = new RotaDaEquipe(B, matriz, 9, OITO_HORAS, OITO_HORAS + 8 * 3600, 0);
		var perto1 = parada(1, PrioridadeDaParada.MEDIA, 30, Set.of(A, B));
		var perto2 = parada(2, PrioridadeDaParada.MEDIA, 30, Set.of(A, B));
		var longe = parada(8, PrioridadeDaParada.MEDIA, 30, Set.of(A, B));
		// Perto de A, mas só B tem a habilidade (RN-23).
		var soDeB = parada(3, PrioridadeDaParada.MEDIA, 30, Set.of(B));

		var resultado = resolver(new PlanoDoDia(List.of(a, b), List.of(perto1, perto2, longe, soDeB)));

		assertThat(resultado.getPontuacao().isFeasible()).isTrue();
		var rotaA = rota(resultado, A).getParadas();
		var rotaB = rota(resultado, B).getParadas();
		assertThat(ids(rotaA)).containsExactly(perto1.getOrdemServicoId(), perto2.getOrdemServicoId());
		assertThat(ids(rotaB)).contains(longe.getOrdemServicoId(), soDeB.getOrdemServicoId());
		assertThat(rotaB.getFirst().getOrdemServicoId()).isEqualTo(longe.getOrdemServicoId());
	}

	@Test
	void jornadaCurtaDeixaAsMenosPrioritariasDeForaEEmergencialPrimeiro() {
		var matriz = linha(10, 60);
		// Uma hora de jornada: cabem duas OS de 25 min.
		var a = new RotaDaEquipe(A, matriz, 0, OITO_HORAS, OITO_HORAS + 3600, 0);
		var baixa = parada(1, PrioridadeDaParada.BAIXA, 25, Set.of(A));
		var alta = parada(2, PrioridadeDaParada.ALTA, 25, Set.of(A));
		var emergencial = parada(5, PrioridadeDaParada.EMERGENCIAL, 25, Set.of(A));

		var resultado = resolver(new PlanoDoDia(List.of(a), List.of(baixa, alta, emergencial)));

		assertThat(resultado.getPontuacao().isFeasible()).isTrue();
		var paradas = rota(resultado, A).getParadas();
		assertThat(ids(paradas)).containsExactly(emergencial.getOrdemServicoId(), alta.getOrdemServicoId());
		var linha = rota(resultado, A).linhaDoTempo();
		assertThat(linha.fimSeg()).isLessThanOrEqualTo(OITO_HORAS + 3600);
	}

	@Test
	void osFixadasFicamNoComecoNaOrdemDada() {
		var matriz = linha(10, 60);
		var a = new RotaDaEquipe(A, matriz, 0, OITO_HORAS, OITO_HORAS + 8 * 3600, 0);
		var longe = parada(8, PrioridadeDaParada.MEDIA, 10, Set.of(A));
		var perto = parada(1, PrioridadeDaParada.MEDIA, 10, Set.of(A));
		a.fixar(List.of(longe));

		var resultado = resolver(new PlanoDoDia(List.of(a), List.of(longe, perto)));

		assertThat(ids(rota(resultado, A).getParadas())).containsExactly(longe.getOrdemServicoId(),
				perto.getOrdemServicoId());
	}

	@Test
	void pausaEntraNoMeioDaJornada() {
		var matriz = linha(10, 60);
		var a = new RotaDaEquipe(A, matriz, 0, OITO_HORAS, OITO_HORAS + 4 * 3600, 3600);
		var primeira = parada(1, PrioridadeDaParada.MEDIA, 120, Set.of(A));
		var segunda = parada(2, PrioridadeDaParada.MEDIA, 30, Set.of(A));
		a.setParadas(new java.util.ArrayList<>(List.of(primeira, segunda)));

		var itens = a.linhaDoTempo().itens();

		// Chega na primeira às 8h01, sai às 10h01 (meio da jornada é 10h); pausa antes da
		// segunda.
		assertThat(itens.get(0).chegadaSeg()).isEqualTo(OITO_HORAS + 60);
		assertThat(itens.get(1).chegadaSeg()).isEqualTo(OITO_HORAS + 60 + 7200 + 60 + 3600);
		assertThat(a.excessoSeg()).isZero();
	}

	private static RotaDaEquipe rota(PlanoDoDia plano, UUID equipe) {
		return plano.getRotas().stream().filter(r -> r.getEquipeId().equals(equipe)).findFirst().orElseThrow();
	}

	private static List<UUID> ids(List<Parada> paradas) {
		return paradas.stream().map(Parada::getOrdemServicoId).toList();
	}

}
