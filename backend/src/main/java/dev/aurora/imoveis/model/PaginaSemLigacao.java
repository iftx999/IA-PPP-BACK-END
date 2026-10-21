package dev.aurora.imoveis.model;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Página da lista. {@code proximo} é o cursor opaco da página seguinte (nulo na última);
 * {@code matriculasVisiveis} indica se as matrículas vieram completas.
 */
public record PaginaSemLigacao(UUID unidadeId, String unidadeNome, YearMonth competencia, Totais totais,
		List<ImovelSemLigacao> itens, @Nullable String proximo, boolean matriculasVisiveis) {
}
