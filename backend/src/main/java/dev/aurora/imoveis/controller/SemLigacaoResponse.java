package dev.aurora.imoveis.controller;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.aurora.imoveis.model.ImovelSemLigacao;
import dev.aurora.imoveis.model.PaginaSemLigacao;
import dev.aurora.imoveis.model.SituacaoImovel;
import dev.aurora.imoveis.model.Totais;
import org.jspecify.annotations.Nullable;

/**
 * Resposta de GET /api/v1/imoveis/sem-ligacao. {@code proximo} é o cursor da página
 * seguinte (nulo na última). Quando {@code matriculasVisiveis} é falso, as matrículas vêm
 * mascaradas.
 */
record SemLigacaoResponse(UUID unidadeId, String unidadeNome, YearMonth competencia, TotaisDto totais,
		List<ItemDto> itens, @Nullable String proximo, boolean matriculasVisiveis) {

	record TotaisDto(long todos, long factiveis, long cortados) {

		static TotaisDto de(Totais t) {
			return new TotaisDto(t.todos(), t.factiveis(), t.cortados());
		}
	}

	/**
	 * {@code ultimoMesLigado} nulo = não aparece ligado desde
	 * {@code primeiraCompetencia}.
	 */
	record ItemDto(String matricula, UUID unidadeId, String unidadeNome, SituacaoImovel situacao,
			@Nullable YearMonth ultimoMesLigado, YearMonth primeiraCompetencia, @Nullable Long mesesSemLigacao) {
	}

	static SemLigacaoResponse de(PaginaSemLigacao p) {
		return new SemLigacaoResponse(p.unidadeId(), p.unidadeNome(), p.competencia(), TotaisDto.de(p.totais()),
				p.itens().stream().map(i -> item(i, p.competencia())).toList(), p.proximo(), p.matriculasVisiveis());
	}

	private static ItemDto item(ImovelSemLigacao i, YearMonth competencia) {
		return new ItemDto(i.matricula(), i.unidadeId(), i.unidadeNome(), i.situacao(), i.ultimoMesLigado(),
				i.primeiraCompetencia(), i.mesesSemLigacao(competencia));
	}

}
