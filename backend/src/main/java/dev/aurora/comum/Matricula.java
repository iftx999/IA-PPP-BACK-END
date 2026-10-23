package dev.aurora.comum;

/**
 * Matrícula do imóvel no sistema comercial: dado pessoal indireto (doc 09, 9.7). Sem a
 * permissão {@code dados_pessoais:ler}, só os 3 últimos caracteres aparecem.
 */
public final class Matricula {

	private static final int VISIVEIS = 3;

	private Matricula() {
	}

	public static String mascarar(String matricula) {
		if (matricula.length() <= VISIVEIS) {
			return "•".repeat(VISIVEIS);
		}
		return "••••" + matricula.substring(matricula.length() - VISIVEIS);
	}

}
