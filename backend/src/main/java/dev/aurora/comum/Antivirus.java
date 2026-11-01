package dev.aurora.comum;

/**
 * Varredura de arquivos enviados (doc 09, 9.7; ADR 0033). O arquivo vai inteiro para o
 * antivírus antes de ser gravado; nada é executado ou aberto antes disso além da checagem
 * do tipo.
 */
public interface Antivirus {

	Verificacao verificar(byte[] conteudo);

	/** Resultado da varredura. */
	sealed interface Verificacao {

	}

	record Limpo() implements Verificacao {
	}

	/**
	 * @param assinatura nome da ameaça encontrada (ex.: "Win.Test.EICAR_HDB-1")
	 */
	record Infectado(String assinatura) implements Verificacao {
	}

	/** Antivírus desligado ou fora do ar: o arquivo não foi verificado. */
	record NaoVerificado(String motivo) implements Verificacao {
	}

}
