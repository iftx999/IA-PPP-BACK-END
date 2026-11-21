package dev.aurora.operacao.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.operacao.model.Indicadores;
import dev.aurora.operacao.model.Indicadores.Resumo;
import dev.aurora.operacao.model.Indicadores.TempoDoServico;
import dev.aurora.operacao.repository.OperacaoRepository;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores da operação num período (J12): prazo por serviço, tempo real × padrão (com
 * a sugestão de ajuste do tempo padrão), motivos e produtividade por equipe.
 */
@Service
public class IndicadoresDaOperacaoService {

	static final int MAXIMO_DE_DIAS = 92;

	/** Amostras mínimas para sugerir um novo tempo padrão. */
	static final int AMOSTRAS_MINIMAS = 10;

	/** Diferença mínima (mediana real ÷ padrão) para sugerir ajuste. */
	static final double TOLERANCIA = 0.15;

	private final OperacaoRepository repositorio;

	private final Unidades unidades;

	private final Clock relogio;

	IndicadoresDaOperacaoService(OperacaoRepository repositorio, Unidades unidades, Clock relogio) {
		this.repositorio = repositorio;
		this.unidades = unidades;
		this.relogio = relogio;
	}

	@Transactional(readOnly = true)
	public Indicadores calcular(@Nullable UUID unidade, @Nullable LocalDate de, @Nullable LocalDate ate) {
		var area = OperacaoDoDiaService.unidadeDaConsulta(unidades, unidade);
		var hoje = LocalDate.now(relogio);
		var fim = ate != null ? ate : hoje;
		var inicio = de != null ? de : fim.minusDays(29);
		if (inicio.isAfter(fim)) {
			throw new RegraVioladaException("A data inicial deve ser antes da final.");
		}
		if (ChronoUnit.DAYS.between(inicio, fim) >= MAXIMO_DE_DIAS) {
			throw new RegraVioladaException("Escolha um período de até " + MAXIMO_DE_DIAS + " dias.");
		}
		var agora = relogio.instant();
		var zona = relogio.getZone();
		var resumo = repositorio.resumo(area, inicio, fim, agora);
		var prazos = repositorio.prazos(area, inicio, fim, agora);
		var tempos = repositorio.tempos(area, inicio, fim).stream().map(t -> t.comSugestao(sugerir(t))).toList();
		var motivos = repositorio.motivos(area, inicio, fim);
		var equipes = repositorio.produtividade(area, inicio, fim, inicio.atStartOfDay(zona).toInstant(),
				fim.plusDays(1).atStartOfDay(zona).toInstant(), agora);

		double km = 0;
		double deslocamento = 0;
		int pesoDeslocamento = 0;
		for (var e : equipes) {
			km += e.km();
			int peso = e.executadas() + e.naoExecutadas() + e.devolvidas();
			if (e.deslocamentoMedioMin() != null && peso > 0) {
				deslocamento += e.deslocamentoMedioMin() * peso;
				pesoDeslocamento += peso;
			}
		}
		double execucao = 0;
		int amostras = 0;
		for (var t : tempos) {
			execucao += t.execucaoMediaMin() * t.amostras();
			amostras += t.amostras();
		}
		var completo = new Resumo(resumo.ordens(), resumo.executadas(), resumo.naoExecutadas(), resumo.devolvidas(),
				resumo.noPrazo(), resumo.foraDoPrazo(), resumo.vencidasAbertas(),
				pesoDeslocamento > 0 ? deslocamento / pesoDeslocamento : null,
				amostras > 0 ? execucao / amostras : null, km);
		return new Indicadores(inicio, fim, completo, prazos, tempos, motivos, equipes);
	}

	/**
	 * J12: o tempo real medido sugere um novo tempo padrão quando há amostras suficientes
	 * e a mediana real ÷ padrão passa da tolerância. Por OS, em minutos inteiros; por
	 * unidade de medida, com duas casas.
	 */
	static @Nullable Double sugerir(TempoDoServico t) {
		var razao = t.razaoMediana();
		if (t.amostras() < AMOSTRAS_MINIMAS || razao == null || Math.abs(razao - 1) < TOLERANCIA) {
			return null;
		}
		double casas = t.tempoPorUnidade() ? 100 : 1;
		double minimo = t.tempoPorUnidade() ? 0.01 : 1;
		var sugerido = Math.round(t.tempoPadraoAtual() * razao * casas) / casas;
		return Math.min(1440, Math.max(minimo, sugerido));
	}

}
