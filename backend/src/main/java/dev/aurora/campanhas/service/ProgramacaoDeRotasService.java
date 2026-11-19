package dev.aurora.campanhas.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.aurora.campanhas.ProgramacaoDeRotas;
import dev.aurora.campanhas.repository.OrdemServicoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Publicação das rotas da roteirização como programação das OS (ADR 0046). */
@Service
class ProgramacaoDeRotasService implements ProgramacaoDeRotas {

	private final OrdemServicoRepository ordens;

	ProgramacaoDeRotasService(OrdemServicoRepository ordens) {
		this.ordens = ordens;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public int programarEmSequencia(UUID equipe, LocalDate data, List<UUID> lista, UUID usuario) {
		return ordens.programarEmSequencia(equipe, data, List.copyOf(lista), usuario);
	}

}
