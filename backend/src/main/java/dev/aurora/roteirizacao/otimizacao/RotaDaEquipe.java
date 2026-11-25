package dev.aurora.roteirizacao.otimizacao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPinToIndex;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import dev.aurora.roteirizacao.model.Matriz;

/**
 * A rota de uma equipe no dia (RN-25): parte do ponto de partida no início da jornada,
 * visita as paradas em ordem e termina em aberto. As primeiras {@code fixadas} paradas
 * não mudam (OS em execução e as fixadas pelo programador). A pausa entra uma vez, na
 * primeira parada a partir do meio da jornada.
 */
@PlanningEntity
public class RotaDaEquipe {

	@PlanningId
	private UUID equipeId;

	private Matriz matriz;

	private int partida;

	/** Segundos desde a meia-noite do dia roteirizado. */
	private long inicioSeg;

	private long fimSeg;

	private long meioSeg;

	private int pausaSeg;

	@PlanningListVariable(allowsUnassignedValues = true)
	private List<Parada> paradas = new ArrayList<>();

	@PlanningPinToIndex
	private int fixadas;

	/** Para o Timefold (clonagem da solução). */
	RotaDaEquipe() {
		this.equipeId = new UUID(0, 0);
		this.matriz = new Matriz(new int[0][0], new int[0][0], false);
	}

	public RotaDaEquipe(UUID equipeId, Matriz matriz, int partida, long inicioSeg, long fimSeg, int pausaSeg) {
		this.equipeId = equipeId;
		this.matriz = matriz;
		this.partida = partida;
		this.inicioSeg = inicioSeg;
		this.fimSeg = fimSeg;
		this.meioSeg = inicioSeg + (fimSeg - inicioSeg) / 2;
		this.pausaSeg = pausaSeg;
	}

	/** Paradas fixadas no começo da rota, nesta ordem. */
	public void fixar(List<Parada> lista) {
		this.paradas = new ArrayList<>(lista);
		this.fixadas = lista.size();
	}

	/** Chegada, saída e deslocamento até cada parada, na ordem atual. */
	public LinhaDoTempo linhaDoTempo() {
		var itens = new ArrayList<LinhaDoTempo.Item>(paradas.size());
		long t = inicioSeg;
		int anterior = partida;
		boolean pausou = pausaSeg == 0;
		long deslocamento = 0;
		long metros = 0;
		for (var parada : paradas) {
			int viagem = matriz.segundos(anterior, parada.getEntrada());
			int distancia = matriz.metros(anterior, parada.getEntrada());
			t += viagem;
			deslocamento += viagem;
			metros += distancia;
			if (!pausou && t >= meioSeg) {
				t += pausaSeg;
				pausou = true;
			}
			long chegada = t;
			t += parada.getDuracaoSeg();
			itens.add(new LinhaDoTempo.Item(parada, chegada, t, viagem, distancia));
			anterior = parada.getSaida();
		}
		return new LinhaDoTempo(itens, inicioSeg, t, deslocamento, metros);
	}

	/** Tempo além do fim da jornada (restrição dura da RN-25). */
	public long excessoSeg() {
		return Math.max(0, linhaDoTempo().fimSeg() - fimSeg);
	}

	/** Paradas que a equipe não pode atender (RN-23). */
	public long incompativeis() {
		return paradas.stream().filter(p -> !p.atendidaPor(equipeId)).count();
	}

	public long deslocamentoSeg() {
		return linhaDoTempo().deslocamentoSeg();
	}

	/** Paradas com chegada depois do prazo (RN-21). */
	public long atrasadas() {
		return linhaDoTempo().itens().stream().filter(i -> i.chegadaSeg() > i.parada().getPrazoSeg()).count();
	}

	/** Minutos do início da jornada até a chegada em cada emergencial, somados. */
	public long esperaDasEmergenciaisMin() {
		return linhaDoTempo().itens()
			.stream()
			.filter(i -> i.parada().getPrioridade() == PrioridadeDaParada.EMERGENCIAL)
			.mapToLong(i -> (i.chegadaSeg() - inicioSeg) / 60)
			.sum();
	}

	/** Minutos de trabalho (deslocamento e serviço), para equilibrar a carga. */
	public long trabalhoMin() {
		var linha = linhaDoTempo();
		return paradas.isEmpty() ? 0 : (linha.fimSeg() - inicioSeg) / 60;
	}

	public UUID getEquipeId() {
		return equipeId;
	}

	public List<Parada> getParadas() {
		return paradas;
	}

	public void setParadas(List<Parada> paradas) {
		this.paradas = paradas;
	}

	public int getFixadas() {
		return fixadas;
	}

	public int getPartida() {
		return partida;
	}

	public long getInicioSeg() {
		return inicioSeg;
	}

	public long getFimSeg() {
		return fimSeg;
	}

	@Override
	public String toString() {
		return "Rota[" + equipeId + "]";
	}

}
