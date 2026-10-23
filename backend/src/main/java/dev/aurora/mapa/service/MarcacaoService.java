package dev.aurora.mapa.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.mapa.model.Area;
import dev.aurora.mapa.model.Marcacao;
import dev.aurora.mapa.model.NovaMarcacao;
import dev.aurora.mapa.repository.MarcacaoRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E4: marcações de visita. A unidade da marcação é a mais específica, entre as que a
 * pessoa pode marcar, cujo polígono contém o ponto. Na trilha vão só identificadores: o
 * texto pode ter dado pessoal.
 */
@Service
public class MarcacaoService {

	static final int LIMITE_NA_AREA = 500;

	private final MarcacaoRepository marcacoes;

	private final Auditoria auditoria;

	MarcacaoService(MarcacaoRepository marcacoes, Auditoria auditoria) {
		this.marcacoes = marcacoes;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<Marcacao> listar(Area area) {
		return marcacoes.listar(area, LIMITE_NA_AREA);
	}

	@Transactional
	public Marcacao criar(NovaMarcacao nova) {
		var unidade = marcacoes.buscarUnidadeNoPonto(nova.longitude(), nova.latitude())
			.orElseThrow(() -> new RegraVioladaException(
					"O ponto fica fora dos municípios e setores onde você pode marcar locais."));
		var id = UUID.randomUUID();
		var autor = ContextoAcesso.exigir().usuarioId();
		if (autor == null) {
			throw new IllegalStateException("marcação sem usuário identificado");
		}
		var limpa = new NovaMarcacao(nova.longitude(), nova.latitude(), nova.titulo().strip(),
				nova.observacao() == null || nova.observacao().isBlank() ? null : nova.observacao().strip());
		marcacoes.inserir(id, unidade, limpa, autor);
		auditoria.registrar(TipoEvento.MARCACAO_CRIADA, "mapa/marcacoes/" + id, Map.of("unidade", unidade.toString()));
		return marcacoes.buscar(id).orElseThrow();
	}

	/** Só quem criou remove (a marcação é o registro de uma visita). */
	@Transactional
	public void remover(UUID id) {
		var marcacao = marcacoes.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (!marcacao.autorId().equals(ContextoAcesso.exigir().usuarioId())) {
			throw new AccessDeniedException("Só quem criou a marcação pode removê-la.");
		}
		marcacoes.remover(id);
		auditoria.registrar(TipoEvento.MARCACAO_REMOVIDA, "mapa/marcacoes/" + id,
				Map.of("unidade", marcacao.unidadeId().toString()));
	}

}
