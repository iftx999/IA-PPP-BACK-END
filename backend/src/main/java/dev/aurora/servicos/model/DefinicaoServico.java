package dev.aurora.servicos.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.servicos.FaseDaOrdem;
import org.jspecify.annotations.Nullable;

/**
 * O que é versionado no serviço (RN-20): mudar qualquer campo daqui cria nova versão;
 * grupo e situação (ativo) ficam no serviço.
 *
 * @param tempoPadraoMin minutos por OS ou, com {@code tempoPorUnidade}, por unidade de
 * medida
 * @param conferidoPelaCarga a carga mensal confirma a execução (RN-19); só em imóvel
 * @param fasesComFoto fases em que o app exige foto (ADR 0053)
 * @param fasesComObservacao fases em que o app exige observação (ADR 0053)
 */
public record DefinicaoServico(String nome, @Nullable String descricao, UnidadeMedida unidadeMedida, AlvoServico alvo,
		BigDecimal tempoPadraoMin, boolean tempoPorUnidade, Prazos prazos, @Nullable UUID tipoVeiculoId,
		@Nullable UUID habilidadeId, boolean conferidoPelaCarga, List<FaseDaOrdem> fasesComFoto,
		List<FaseDaOrdem> fasesComObservacao, List<Parametro> parametros) {

	public static final int MAXIMO_DE_PARAMETROS = 40;

	private static final BigDecimal UM_DIA_EM_MINUTOS = BigDecimal.valueOf(1440);

	public DefinicaoServico {
		fasesComFoto = FaseDaOrdem.normalizadas(fasesComFoto);
		fasesComObservacao = FaseDaOrdem.normalizadas(fasesComObservacao);
		parametros = List.copyOf(parametros);
	}

	/** Com foto e observação em todas as fases (o padrão). */
	public DefinicaoServico(String nome, @Nullable String descricao, UnidadeMedida unidadeMedida, AlvoServico alvo,
			BigDecimal tempoPadraoMin, boolean tempoPorUnidade, Prazos prazos, @Nullable UUID tipoVeiculoId,
			@Nullable UUID habilidadeId, boolean conferidoPelaCarga, List<Parametro> parametros) {
		this(nome, descricao, unidadeMedida, alvo, tempoPadraoMin, tempoPorUnidade, prazos, tipoVeiculoId, habilidadeId,
				conferidoPelaCarga, FaseDaOrdem.TODAS, FaseDaOrdem.TODAS, parametros);
	}

	/**
	 * Valida e normaliza (textos limpos, números sem zeros à direita): base da comparação
	 * de versões.
	 */
	public DefinicaoServico normalizada() {
		var nomeLimpo = Nomes.obrigatorio(nome, 3, 120, "Nome do serviço");
		var descricaoLimpa = Nomes.opcional(descricao, 500, "Descrição");
		var tempo = Parametro.limpo(tempoPadraoMin);
		if (tempo == null || tempo.signum() <= 0 || tempo.compareTo(UM_DIA_EM_MINUTOS) > 0 || tempo.scale() > 2) {
			throw new RegraVioladaException("Tempo padrão: mais de 0 e até 1.440 minutos, com até 2 casas decimais.");
		}
		if (tempoPorUnidade && unidadeMedida == UnidadeMedida.UN) {
			throw new RegraVioladaException("Tempo por unidade só em serviço medido em m, m² ou m³.");
		}
		prazos.validar();
		if (conferidoPelaCarga && alvo != AlvoServico.IMOVEL) {
			throw new RegraVioladaException("Só serviço em imóvel pode ser conferido pela carga mensal.");
		}
		if (parametros.size() > MAXIMO_DE_PARAMETROS) {
			throw new RegraVioladaException("Um serviço tem no máximo " + MAXIMO_DE_PARAMETROS + " parâmetros.");
		}
		var anteriores = new LinkedHashMap<String, Parametro>();
		var lista = new ArrayList<Parametro>();
		for (var parametro : parametros) {
			var limpo = parametro.normalizado(anteriores);
			if (anteriores.putIfAbsent(limpo.chave(), limpo) != null) {
				throw new RegraVioladaException("Chave de parâmetro repetida: \"" + limpo.chave() + "\".");
			}
			lista.add(limpo);
		}
		return new DefinicaoServico(nomeLimpo, descricaoLimpa, unidadeMedida, alvo, tempo, tempoPorUnidade, prazos,
				tipoVeiculoId, habilidadeId, conferidoPelaCarga, fasesComFoto, fasesComObservacao, lista);
	}

}
