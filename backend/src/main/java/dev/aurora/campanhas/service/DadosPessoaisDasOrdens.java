package dev.aurora.campanhas.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.model.OrdemServico;
import dev.aurora.comum.Matricula;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * Matrícula das OS (doc 09, 9.7): aparece inteira só onde a pessoa tem
 * {@code dados_pessoais:ler} (na unidade do imóvel ou acima); a exibição fica na trilha
 * como acesso a dado pessoal. Nas demais, mascarada.
 */
@Component
class DadosPessoaisDasOrdens {

	static final String PERMISSAO = "dados_pessoais:ler";

	private final Unidades unidades;

	private final Auditoria auditoria;

	DadosPessoaisDasOrdens(Unidades unidades, Auditoria auditoria) {
		this.unidades = unidades;
		this.auditoria = auditoria;
	}

	List<OrdemServico> aplicar(List<OrdemServico> ordens, String motivo) {
		var contexto = ContextoAcesso.exigir();
		var podePorUnidade = new HashMap<UUID, Boolean>();
		var expostas = new int[1];
		var resultado = ordens.stream().map(o -> {
			boolean pode = podePorUnidade.computeIfAbsent(o.unidadeId(),
					id -> unidades.caminhoNoEscopo(id).map(c -> contexto.podeEm(PERMISSAO, c)).orElse(false));
			if (pode) {
				expostas[0]++;
				return o;
			}
			return o.comMatricula(Matricula.mascarar(o.matricula()));
		}).toList();
		if (expostas[0] > 0) {
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("quantidade", expostas[0]);
			detalhes.put("motivo", motivo);
			auditoria.registrar(TipoEvento.ACESSO_DADO_PESSOAL, "ordens-de-servico", detalhes);
		}
		return resultado;
	}

}
