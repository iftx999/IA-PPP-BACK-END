package dev.aurora.servicos.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

/**
 * Campo que a equipe preenche ao encerrar a OS (J2). A ordem é a posição na lista do
 * serviço.
 *
 * @param minimo valor mínimo (número), caracteres (texto) ou fotos (foto)
 * @param opcoes só em lista e lista múltipla
 * @param condicao só aparece quando outro parâmetro anterior tem o valor indicado
 */
public record Parametro(String chave, String rotulo, @Nullable String ajuda, TipoParametro tipo, boolean obrigatorio,
		@Nullable BigDecimal minimo, @Nullable BigDecimal maximo, @Nullable String unidade, List<String> opcoes,
		@Nullable Condicao condicao) {

	static final int MAXIMO_DE_OPCOES = 30;

	static final int MAXIMO_DE_FOTOS = 10;

	static final int MAXIMO_DE_TEXTO = 500;

	private static final Pattern CHAVE = Pattern.compile("^[a-z][a-z0-9_]{0,39}$");

	public Parametro {
		opcoes = List.copyOf(opcoes);
	}

	/**
	 * Valida e normaliza o parâmetro.
	 * @param anteriores parâmetros já validados que vêm antes, pela chave (alvo da
	 * condição)
	 */
	Parametro normalizado(Map<String, Parametro> anteriores) {
		var chaveLimpa = chave.strip().toLowerCase(Locale.ROOT);
		var nome = Nomes.obrigatorio(rotulo, 2, 80, "Rótulo do parâmetro");
		if (!CHAVE.matcher(chaveLimpa).matches()) {
			throw new RegraVioladaException("Parâmetro \"" + nome
					+ "\": a chave usa de 1 a 40 letras minúsculas, números ou \"_\", começando por letra.");
		}
		var textoAjuda = Nomes.opcional(ajuda, 200, "Ajuda do parâmetro \"" + nome + "\"");
		var faixa = faixa(nome);
		@Nullable String unidadeLimpa = Nomes.opcional(unidade, 10, "Unidade do parâmetro \"" + nome + "\"");
		if (unidadeLimpa != null && !tipo.numerico()) {
			throw new RegraVioladaException("Parâmetro \"" + nome + "\": unidade só em número.");
		}
		var lista = opcoesLimpas(nome);
		var condicaoLimpa = condicaoValida(nome, anteriores);
		return new Parametro(chaveLimpa, nome, textoAjuda, tipo, obrigatorio, faixa[0], faixa[1], unidadeLimpa, lista,
				condicaoLimpa);
	}

	private @Nullable BigDecimal[] faixa(String nome) {
		@Nullable BigDecimal min = limpo(minimo);
		@Nullable BigDecimal max = limpo(maximo);
		if (!tipo.temFaixa()) {
			if (min != null || max != null) {
				throw new RegraVioladaException(
						"Parâmetro \"" + nome + "\": mínimo e máximo só em número, texto ou foto.");
			}
			return new @Nullable BigDecimal[] { null, null };
		}
		if (tipo != TipoParametro.DECIMAL && (naoInteiro(min) || naoInteiro(max))) {
			throw new RegraVioladaException("Parâmetro \"" + nome + "\": mínimo e máximo devem ser inteiros.");
		}
		if (tipo == TipoParametro.FOTO) {
			if (min == null) {
				min = obrigatorio ? BigDecimal.ONE : BigDecimal.ZERO;
			}
			if (max == null) {
				max = BigDecimal.valueOf(Math.max(5, min.intValue()));
			}
			if (obrigatorio && min.signum() == 0) {
				throw new RegraVioladaException("Parâmetro \"" + nome + "\": foto obrigatória exige ao menos 1.");
			}
			if (min.signum() < 0 || max.compareTo(BigDecimal.valueOf(MAXIMO_DE_FOTOS)) > 0) {
				throw new RegraVioladaException("Parâmetro \"" + nome + "\": de 0 a " + MAXIMO_DE_FOTOS + " fotos.");
			}
		}
		if (tipo == TipoParametro.TEXTO) {
			if (max == null) {
				max = BigDecimal.valueOf(MAXIMO_DE_TEXTO);
			}
			if ((min != null && min.signum() < 0) || max.compareTo(BigDecimal.valueOf(MAXIMO_DE_TEXTO)) > 0) {
				throw new RegraVioladaException(
						"Parâmetro \"" + nome + "\": texto de 0 a " + MAXIMO_DE_TEXTO + " caracteres.");
			}
		}
		if (min != null && max != null && min.compareTo(max) > 0) {
			throw new RegraVioladaException("Parâmetro \"" + nome + "\": o mínimo é maior que o máximo.");
		}
		return new @Nullable BigDecimal[] { min, max };
	}

	private List<String> opcoesLimpas(String nome) {
		if (!tipo.temOpcoes()) {
			if (!opcoes.isEmpty()) {
				throw new RegraVioladaException("Parâmetro \"" + nome + "\": opções só em lista.");
			}
			return List.of();
		}
		var lista = new ArrayList<String>();
		var vistas = new HashSet<String>();
		for (var opcao : opcoes) {
			var limpa = Nomes.obrigatorio(opcao, 1, 60, "Opção do parâmetro \"" + nome + "\"");
			if (!vistas.add(limpa.toLowerCase(Locale.ROOT))) {
				throw new RegraVioladaException("Parâmetro \"" + nome + "\": opção repetida \"" + limpa + "\".");
			}
			lista.add(limpa);
		}
		if (lista.size() < 2 || lista.size() > MAXIMO_DE_OPCOES) {
			throw new RegraVioladaException(
					"Parâmetro \"" + nome + "\": a lista tem de 2 a " + MAXIMO_DE_OPCOES + " opções.");
		}
		return List.copyOf(lista);
	}

	private @Nullable Condicao condicaoValida(String nome, Map<String, Parametro> anteriores) {
		if (condicao == null) {
			return null;
		}
		var origem = anteriores.get(condicao.chave().strip().toLowerCase(Locale.ROOT));
		if (origem == null || !origem.tipo().condiciona()) {
			throw new RegraVioladaException("Parâmetro \"" + nome
					+ "\": a condição deve apontar para um parâmetro sim/não ou lista que venha antes.");
		}
		var valor = condicao.valor().strip();
		var valido = origem.tipo() == TipoParametro.SIM_NAO ? valor.equals("true") || valor.equals("false")
				: origem.opcoes().contains(valor);
		if (!valido) {
			throw new RegraVioladaException(
					"Parâmetro \"" + nome + "\": o valor da condição não existe em \"" + origem.rotulo() + "\".");
		}
		return new Condicao(origem.chave(), valor);
	}

	/**
	 * Sem zeros à direita e sem notação científica (100, não 1E+2): versões iguais
	 * comparam iguais.
	 */
	public static @Nullable BigDecimal limpo(@Nullable BigDecimal valor) {
		if (valor == null) {
			return null;
		}
		var limpo = valor.stripTrailingZeros();
		return limpo.scale() < 0 ? limpo.setScale(0) : limpo;
	}

	private static boolean naoInteiro(@Nullable BigDecimal valor) {
		return valor != null && valor.stripTrailingZeros().scale() > 0;
	}

}
