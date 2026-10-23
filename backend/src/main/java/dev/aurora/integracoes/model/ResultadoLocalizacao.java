package dev.aurora.integracoes.model;

import java.util.List;

/**
 * @param preservados localizações corrigidas em campo ou desenhadas, que o geocódigo não
 * substitui
 */
public record ResultadoLocalizacao(long lidos, long novos, long atualizados, long inalterados, long preservados,
		long rejeitados, List<Rejeicao> rejeicoes) {
}
