package dev.aurora.logradouros.model;

import java.util.List;

/**
 * Relatório da importação. Na simulação nada é gravado.
 *
 * @param inalterados válidos que já estavam iguais
 * @param rejeicoes até as 200 primeiras
 * @param semTrecho endereços sem trecho pela faixa nem pelo ponto mais próximo (só em
 * endereços)
 * @param ausentes trechos que já existiam no município e não vieram no arquivo (só em
 * trechos; não são apagados)
 */
public record ResultadoImportacao(TipoImportacao tipo, boolean simulacao, int lidos, int novos, int atualizados,
		int inalterados, int rejeitados, List<Rejeicao> rejeicoes, int semTrecho, int ausentes) {
}
