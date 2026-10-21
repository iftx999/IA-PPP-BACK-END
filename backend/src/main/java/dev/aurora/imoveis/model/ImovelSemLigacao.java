package dev.aurora.imoveis.model;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Imóvel com rede disponível e sem ligação na competência. {@code ultimoMesLigado} nulo =
 * não aparece ligado no histórico disponível (desde {@code primeiraCompetencia}).
 */
public record ImovelSemLigacao(String matricula, UUID unidadeId, String unidadeNome, SituacaoImovel situacao,
		@Nullable YearMonth ultimoMesLigado, YearMonth primeiraCompetencia) {

	/**
	 * "Sem ligação há": meses desde a última competência em que estava ligado (nulo se
	 * nunca esteve).
	 */
	public @Nullable Long mesesSemLigacao(YearMonth competencia) {
		return ultimoMesLigado == null ? null : ChronoUnit.MONTHS.between(ultimoMesLigado, competencia);
	}

	public ImovelSemLigacao comMatricula(String outra) {
		return new ImovelSemLigacao(outra, unidadeId, unidadeNome, situacao, ultimoMesLigado, primeiraCompetencia);
	}

}
