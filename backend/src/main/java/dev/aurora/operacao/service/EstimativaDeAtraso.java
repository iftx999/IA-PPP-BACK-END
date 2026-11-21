package dev.aurora.operacao.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import dev.aurora.operacao.model.ParadaPrevista;
import org.jspecify.annotations.Nullable;

/**
 * Atraso estimado da equipe em relação à rota publicada (J12; estimativa, RN-25). Olha a
 * primeira parada que ainda não saiu da rota:
 * <ul>
 * <li>em execução ou paralisada: término projetado (início real + duração prevista, nunca
 * antes de agora) × saída prevista;</li>
 * <li>a caminho: chegada projetada (último fim real, ou o início da jornada, + o
 * deslocamento previsto, nunca antes de agora) × chegada prevista.</li>
 * </ul>
 * Negativo: adiantada. Rota toda feita: zero.
 */
final class EstimativaDeAtraso {

	private static final Set<String> EM_ANDAMENTO = Set.of("EM_EXECUCAO", "PARALISADA");

	private static final Set<String> FEITAS_PELA_EQUIPE = Set.of("ENCERRADA", "DEVOLVIDA");

	private static final Set<String> FORA_DA_ROTA = Set.of("ENCERRADA", "DEVOLVIDA", "CANCELADA");

	private EstimativaDeAtraso() {
	}

	static int minutos(List<ParadaPrevista> paradas, @Nullable Instant inicioDaJornada, Instant agora) {
		var fimAnterior = inicioDaJornada;
		for (var p : paradas) {
			if (p.fimEm() != null || FORA_DA_ROTA.contains(p.status())) {
				// Só o que a equipe fez (encerrar, devolver) marca o último fim; OS
				// cancelada ou
				// passada para outra equipe só sai da conta.
				if (p.fimEm() != null && FEITAS_PELA_EQUIPE.contains(p.status())
						&& (fimAnterior == null || p.fimEm().isAfter(fimAnterior))) {
					fimAnterior = p.fimEm();
				}
				continue;
			}
			if (EM_ANDAMENTO.contains(p.status())) {
				var duracao = Duration.between(p.chegadaPrevista(), p.saidaPrevista());
				var inicio = p.iniciadaEm() != null ? p.iniciadaEm() : agora;
				return emMinutos(Duration.between(p.saidaPrevista(), maisTarde(inicio.plus(duracao), agora)));
			}
			var base = fimAnterior != null ? fimAnterior : agora;
			var chegada = maisTarde(base.plus(Duration.ofMinutes(p.minutosAte())), agora);
			return emMinutos(Duration.between(p.chegadaPrevista(), chegada));
		}
		return 0;
	}

	private static Instant maisTarde(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}

	private static int emMinutos(Duration d) {
		return (int) Math.round(d.toSeconds() / 60.0);
	}

}
