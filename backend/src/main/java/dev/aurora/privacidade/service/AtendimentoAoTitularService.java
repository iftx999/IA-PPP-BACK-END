package dev.aurora.privacidade.service;

import java.time.Clock;
import java.util.LinkedHashMap;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.Matricula;
import dev.aurora.privacidade.model.RelatorioDoTitular;
import dev.aurora.privacidade.repository.TitularRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatório do titular (ADR 0041). Cada emissão vai para a trilha com o protocolo do
 * pedido, se achou dados e quantos, e a matrícula só mascarada: a trilha não vira uma
 * segunda cópia do dado pessoal.
 */
@Service
public class AtendimentoAoTitularService {

	private final TitularRepository titulares;

	private final Auditoria auditoria;

	private final Clock relogio;

	@Autowired
	AtendimentoAoTitularService(TitularRepository titulares, Auditoria auditoria) {
		this(titulares, auditoria, Clock.systemUTC());
	}

	AtendimentoAoTitularService(TitularRepository titulares, Auditoria auditoria, Clock relogio) {
		this.titulares = titulares;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	@Transactional
	public RelatorioDoTitular relatorio(String matricula, String protocolo) {
		var relatorio = new RelatorioDoTitular(matricula, protocolo, relogio.instant(), titulares.situacoes(matricula),
				titulares.localizacao(matricula).orElse(null), titulares.ordens(matricula));
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("protocolo", protocolo);
		detalhes.put("matricula", Matricula.mascarar(matricula));
		detalhes.put("encontrado", relatorio.encontrado());
		detalhes.put("competencias", relatorio.situacoes().size());
		detalhes.put("ordensDeServico", relatorio.ordens().size());
		auditoria.registrar(TipoEvento.ATENDIMENTO_AO_TITULAR, "titular", detalhes);
		return relatorio;
	}

}
