package dev.aurora.servicos;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** API do catálogo de serviços para os demais módulos. */
public interface Servicos {

	/**
	 * Semeia o catálogo inicial do cliente atual (doc 10, 10.5), se ele ainda não tem
	 * nenhum grupo. Chamado no provisionamento.
	 */
	void semearCatalogoInicial();

	/** Serviços ativos, por grupo e nome, na versão atual (para criar OS). */
	List<ServicoDaOrdem> servicosParaOrdem();

	/** Serviço na versão atual, ativo ou não. */
	Optional<ServicoDaOrdem> servicoParaOrdem(UUID id);

	/** Serviço pelo código (ex.: os de campanha: LIGACAO_NOVA, RELIGACAO). */
	Optional<ServicoDaOrdem> servicoParaOrdem(String codigo);

	/** Motivos ativos de todos os tipos. */
	List<MotivoDaOrdem> motivosParaOrdem();

	Optional<MotivoDaOrdem> motivo(UUID id);

	/** Parâmetros de encerramento de cada versão pedida, na ordem do serviço. */
	Map<UUID, List<ParametroDaOrdem>> parametros(Collection<UUID> versoes);

	/**
	 * ADR 0053: o que o app exige em cada fase da OS (foto, observação), por versão
	 * pedida.
	 */
	Map<UUID, ExigenciasNasFases> exigenciasNasFases(Collection<UUID> versoes);

	/**
	 * RN-21: o {@code dias}-ésimo dia útil depois de {@code inicio}, sem sábados,
	 * domingos, feriados nacionais e os cadastrados para a unidade ou para as de cima.
	 */
	LocalDate somarDiasUteis(LocalDate inicio, int dias, UUID unidade);

}
