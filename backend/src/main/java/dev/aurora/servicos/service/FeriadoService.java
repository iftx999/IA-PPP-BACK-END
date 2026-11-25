package dev.aurora.servicos.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.servicos.model.Calendario;
import dev.aurora.servicos.model.Feriado;
import dev.aurora.servicos.repository.FeriadoRepository;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feriados que contam no prazo das OS (RN-21, P6): os nacionais são calculados; os
 * municipais (ou de qualquer unidade) são cadastrados e valem para a unidade e as de
 * baixo. Cadastro e remoção auditados.
 */
@Service
public class FeriadoService {

	/** Anos aceitos a partir do atual, para trás e para a frente. */
	static final int ANOS = 5;

	private final FeriadoRepository feriados;

	private final Unidades unidades;

	private final Auditoria auditoria;

	private final Clock relogio;

	FeriadoService(FeriadoRepository feriados, Unidades unidades, Auditoria auditoria, Clock relogio) {
		this.feriados = feriados;
		this.unidades = unidades;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	/** Nacionais calculados e os cadastrados no ano, por data. */
	@Transactional(readOnly = true)
	public List<Feriado> doAno(int ano) {
		var lista = new ArrayList<Feriado>();
		Calendario.nacionais(ano)
			.forEach((data, nome) -> lista
				.add(new Feriado(new UUID(0, data.toEpochDay()), new UUID(0, 0), "Brasil", data, nome, true)));
		lista.addAll(feriados.doAno(ano));
		lista.sort(Comparator.comparing(Feriado::data).thenComparing(f -> !f.nacional()));
		return lista;
	}

	@Transactional
	public Feriado cadastrar(UUID unidade, LocalDate data, String nome) {
		var u = unidades.buscarNoEscopo(unidade)
			.orElseThrow(() -> new RegraVioladaException("Escolha uma unidade da sua área."));
		var limpo = nome == null ? "" : nome.strip().replaceAll("\\s+", " ");
		if (limpo.length() < 2 || limpo.length() > 80) {
			throw new RegraVioladaException("Dê um nome ao feriado (de 2 a 80 caracteres).");
		}
		var ano = LocalDate.now(relogio).getYear();
		if (data.getYear() < ano - ANOS || data.getYear() > ano + ANOS) {
			throw new RegraVioladaException("A data deve ficar a até " + ANOS + " anos do ano atual.");
		}
		if (Calendario.feriadoNacional(data)) {
			throw new ConflitoException("Esta data já é feriado nacional.");
		}
		if (feriados.existe(unidade, data)) {
			throw new ConflitoException("Já existe um feriado nesta data para " + u.nome() + ".");
		}
		var id = UUID.randomUUID();
		feriados.inserir(id, unidade, data, limpo, usuario());
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("unidade", unidade.toString());
		detalhes.put("data", data.toString());
		detalhes.put("nome", limpo);
		auditoria.registrar(TipoEvento.FERIADO_CADASTRADO, "feriado/" + id, detalhes);
		return feriados.buscar(id).orElseThrow();
	}

	@Transactional
	public void remover(UUID id) {
		var feriado = feriados.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (unidades.buscarNoEscopo(feriado.unidadeId()).isEmpty()) {
			throw new RecursoNaoEncontradoException();
		}
		feriados.remover(id);
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("unidade", feriado.unidadeId().toString());
		detalhes.put("data", feriado.data().toString());
		detalhes.put("nome", feriado.nome());
		auditoria.registrar(TipoEvento.FERIADO_REMOVIDO, "feriado/" + id, detalhes);
	}

	private static UUID usuario() {
		var id = ContextoAcesso.exigir().usuarioId();
		if (id == null) {
			throw new IllegalStateException("Feriado alterado fora de uma requisição");
		}
		return id;
	}

}
