package dev.aurora.roteirizacao.service;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.roteirizacao.integracao.MalhaViaria;
import dev.aurora.roteirizacao.model.Calculo;
import dev.aurora.roteirizacao.model.Matriz;
import dev.aurora.roteirizacao.model.OrigemRoteirizacao;
import dev.aurora.roteirizacao.model.PedidoRoteirizacao;
import dev.aurora.roteirizacao.model.PerfilRota;
import dev.aurora.roteirizacao.otimizacao.Otimizador;
import dev.aurora.roteirizacao.repository.RoteirizacaoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O cálculo em segundo plano, no contexto de acesso de quem pediu (o RLS vale também
 * aqui): lê OS e equipes, monta a matriz pela malha viária, otimiza, traça as rotas e
 * grava o resultado. Poucos cálculos ao mesmo tempo, porque o otimizador usa CPU o tempo
 * todo.
 */
@Component
class CalculoDaRoteirizacao {

	private static final Logger LOG = LoggerFactory.getLogger(CalculoDaRoteirizacao.class);

	private final RoteirizacaoRepository repositorio;

	private final MalhaViaria malha;

	private final Otimizador otimizador = new Otimizador();

	private final TransactionTemplate transacao;

	private final Executor executor;

	private final Clock relogio;

	private final Semaphore vagas;

	private final Duration tempoPrevia;

	private final Duration tempoReotimizacao;

	private final Duration semMelhora;

	private final Timer duracao;

	CalculoDaRoteirizacao(RoteirizacaoRepository repositorio, MalhaViaria malha, TransactionTemplate transacao,
			@Qualifier("applicationTaskExecutor") Executor executor, Clock relogio, MeterRegistry metricas,
			@Value("${aurora.roteirizacao.tempo-previa}") Duration tempoPrevia,
			@Value("${aurora.roteirizacao.tempo-reotimizacao}") Duration tempoReotimizacao,
			@Value("${aurora.roteirizacao.sem-melhora}") Duration semMelhora,
			@Value("${aurora.roteirizacao.calculos-simultaneos:2}") int simultaneos) {
		this.repositorio = repositorio;
		this.malha = malha;
		this.transacao = transacao;
		this.executor = executor;
		this.relogio = relogio;
		this.tempoPrevia = tempoPrevia;
		this.tempoReotimizacao = tempoReotimizacao;
		this.semMelhora = semMelhora;
		this.vagas = new Semaphore(simultaneos);
		this.duracao = Timer.builder("aurora.roteirizacao.calculo")
			.description("Duração do cálculo de uma roteirização (matriz, otimização e traçado)")
			.register(metricas);
	}

	void iniciar(ContextoAcesso contexto, UUID id, PedidoRoteirizacao pedido) {
		executor.execute(() -> {
			try {
				ContextoAcesso.executar(contexto, () -> {
					executar(id, pedido);
					return null;
				});
			}
			catch (Exception erro) {
				LOG.error("Roteirização {} falhou", id, erro);
				var mensagem = erro instanceof RegraVioladaException ? erro.getMessage()
						: "Falha inesperada no cálculo. Tente de novo; se persistir, acione o suporte.";
				try {
					ContextoAcesso.executar(contexto, () -> {
						transacao.executeWithoutResult(s -> repositorio.falhar(id, mensagem));
						return null;
					});
				}
				catch (Exception ignorada) {
					LOG.error("Não deu para registrar a falha da roteirização {}", id, ignorada);
				}
			}
		});
	}

	void executar(UUID id, PedidoRoteirizacao pedido) throws InterruptedException {
		vagas.acquire();
		try {
			duracao.record(() -> calcular(id, pedido));
		}
		finally {
			vagas.release();
		}
	}

	private void calcular(UUID id, PedidoRoteirizacao pedido) {
		var montagem = transacao
			.execute(s -> new MontagemDoPlano(pedido.data(), relogio.getZone(), pedido.origem(), relogio.instant())
				.preparar(repositorio.equipes(pedido.equipes()), repositorio.ordens(pedido.ordens()),
						repositorio.compatibilidades(pedido.equipes(), pedido.ordens())));
		var pontos = montagem.pontos();
		var matrizes = new EnumMap<PerfilRota, Matriz>(PerfilRota.class);
		var calculo = Calculo.VALHALLA;
		for (var perfil : montagem.perfis()) {
			var resultado = malha.matriz(pontos, perfil);
			matrizes.put(perfil, resultado.matriz());
			if (resultado.calculo() == Calculo.LINHA_RETA) {
				calculo = Calculo.LINHA_RETA;
			}
		}
		var limite = pedido.origem() == OrigemRoteirizacao.PREVIA ? tempoPrevia : tempoReotimizacao;
		var solucao = otimizador.resolver(montagem.plano(matrizes, pedido.fixadas()), limite, semMelhora);
		var porMalha = calculo == Calculo.VALHALLA;
		var resultado = montagem.resultado(solucao,
				(trajeto, perfil) -> porMalha ? malha.tracado(trajeto, perfil) : java.util.List.of());
		var pontuacao = solucao.getPontuacao() == null ? "" : solucao.getPontuacao().toString();
		var calculoFinal = calculo;
		transacao.executeWithoutResult(
				s -> repositorio.concluir(id, calculoFinal, pontuacao, resultado.rotas(), resultado.pendentes()));
		LOG.info("Roteirização {}: {} rotas, {} OS sem rota, {} pontos, cálculo {}, pontuação {}", id,
				resultado.rotas().size(), resultado.pendentes().size(), pontos.size(), calculoFinal, pontuacao);
	}

}
