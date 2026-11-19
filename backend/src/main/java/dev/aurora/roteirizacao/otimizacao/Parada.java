package dev.aurora.roteirizacao.otimizacao;

import java.util.Set;
import java.util.UUID;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import org.jspecify.annotations.Nullable;

/**
 * Uma OS a visitar (doc 10, 10.8). Em OS de trecho a equipe chega no início e sai no fim
 * do trecho; a duração já inclui o percurso dele.
 */
@PlanningEntity
public class Parada {

	/** Sem prazo informado. */
	public static final long SEM_PRAZO = Long.MAX_VALUE;

	@PlanningId
	private UUID ordemServicoId;

	private PrioridadeDaParada prioridade;

	/** Índices na matriz: onde a equipe chega e de onde sai. */
	private int entrada;

	private int saida;

	private int duracaoSeg;

	/** Prazo (RN-21) em segundos desde a meia-noite do dia roteirizado. */
	private long prazoSeg;

	/** Equipes que podem receber a OS (RN-23: área, habilidade e veículo). */
	private Set<UUID> equipesCompativeis;

	/** Fim do dia roteirizado: sem rota e com prazo até ele, a OS estoura o prazo. */
	private long fimDoDiaSeg = SEM_PRAZO;

	@InverseRelationShadowVariable(sourceVariableName = "paradas")
	private @Nullable RotaDaEquipe rota;

	/** Para o Timefold (clonagem da solução). */
	Parada() {
		this.ordemServicoId = new UUID(0, 0);
		this.prioridade = PrioridadeDaParada.BAIXA;
		this.equipesCompativeis = Set.of();
	}

	public Parada(UUID ordemServicoId, PrioridadeDaParada prioridade, int entrada, int saida, int duracaoSeg,
			long prazoSeg, Set<UUID> equipesCompativeis) {
		this.ordemServicoId = ordemServicoId;
		this.prioridade = prioridade;
		this.entrada = entrada;
		this.saida = saida;
		this.duracaoSeg = duracaoSeg;
		this.prazoSeg = prazoSeg;
		this.equipesCompativeis = Set.copyOf(equipesCompativeis);
	}

	public UUID getOrdemServicoId() {
		return ordemServicoId;
	}

	public PrioridadeDaParada getPrioridade() {
		return prioridade;
	}

	public int getEntrada() {
		return entrada;
	}

	public int getSaida() {
		return saida;
	}

	public int getDuracaoSeg() {
		return duracaoSeg;
	}

	public long getPrazoSeg() {
		return prazoSeg;
	}

	public Set<UUID> getEquipesCompativeis() {
		return equipesCompativeis;
	}

	public boolean atendidaPor(UUID equipe) {
		return equipesCompativeis.contains(equipe);
	}

	/** Fica sem rota e o prazo vence no dia (RN-25, objetivo 2). */
	public boolean venceSemRota() {
		return rota == null && prazoSeg <= fimDoDiaSeg;
	}

	void setFimDoDiaSeg(long fimDoDiaSeg) {
		this.fimDoDiaSeg = fimDoDiaSeg;
	}

	public @Nullable RotaDaEquipe getRota() {
		return rota;
	}

	public void setRota(@Nullable RotaDaEquipe rota) {
		this.rota = rota;
	}

	@Override
	public String toString() {
		return "Parada[" + ordemServicoId + "]";
	}

}
