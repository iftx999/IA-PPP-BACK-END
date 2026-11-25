package dev.aurora.operacao.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.operacao.model.Gatilho;
import dev.aurora.operacao.model.SituacaoSugestao;
import dev.aurora.operacao.model.Sugestao;
import dev.aurora.operacao.repository.SugestaoRepository;
import dev.aurora.roteirizacao.Reotimizacoes;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decisão do programador sobre as sugestões de reotimização (J7, ADR 0049). Aceitar junta
 * todas as abertas da mesma unidade no dia num só pedido de reotimização (as equipes
 * afetadas e as emergenciais sem rota); o resultado é uma roteirização que ele confere e
 * publica na tela de roteirização. Nada é publicado sozinho.
 */
@Service
public class SugestoesDeReotimizacaoService {

	private final SugestaoRepository repositorio;

	private final Reotimizacoes reotimizacoes;

	private final Unidades unidades;

	private final Auditoria auditoria;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	SugestoesDeReotimizacaoService(SugestaoRepository repositorio, Reotimizacoes reotimizacoes, Unidades unidades,
			Auditoria auditoria, TransactionTemplate transacao, Clock relogio) {
		this.repositorio = repositorio;
		this.reotimizacoes = reotimizacoes;
		this.unidades = unidades;
		this.auditoria = auditoria;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	public List<Sugestao> doDia(@Nullable UUID unidade, @Nullable LocalDate data) {
		var area = OperacaoDoDiaService.unidadeDaConsulta(unidades, unidade);
		var dia = data != null ? data : LocalDate.now(relogio);
		return transacao.execute(s -> repositorio.doDia(area, dia));
	}

	/** Abertas hoje em todo o escopo e as 5 mais novas (avisos da web). */
	public record Resumo(int abertas, List<Sugestao> recentes) {
	}

	public Resumo resumo() {
		var hoje = LocalDate.now(relogio);
		return transacao
			.execute(s -> new Resumo(repositorio.abertasNoEscopo(hoje), repositorio.recentesNoEscopo(hoje)));
	}

	/** Pedido montado a partir das sugestões abertas da unidade. */
	private record Pedido(UUID unidade, List<UUID> sugestoes, List<UUID> equipes, List<UUID> extras) {
	}

	/**
	 * Pede a reotimização (o cálculo roda em segundo plano, fora desta transação) e marca
	 * as sugestões juntadas como aceitas.
	 * @return a sugestão aceita, com o id da roteirização
	 */
	public Sugestao aceitar(UUID id) {
		var pedido = transacao.execute(s -> {
			var sugestao = aberta(id);
			var equipes = new LinkedHashSet<UUID>();
			var extras = new LinkedHashSet<UUID>();
			var juntas = new ArrayList<UUID>();
			for (var outra : repositorio.abertasDaUnidade(sugestao.unidadeId(), sugestao.data())) {
				juntas.add(outra.id());
				if (outra.equipeId() != null) {
					equipes.add(outra.equipeId());
				}
				if (outra.gatilho() == Gatilho.EMERGENCIAL && outra.osId() != null) {
					extras.add(outra.osId());
					if (outra.equipeId() == null) {
						equipes.addAll(repositorio.equipesEmJornadaParaOrdem(outra.osId()));
					}
				}
			}
			if (equipes.isEmpty()) {
				throw new RegraVioladaException(
						"Nenhuma equipe em jornada atende esta área agora. Programe a OS pela tela de programação.");
			}
			return new Pedido(sugestao.unidadeId(), juntas, List.copyOf(equipes), List.copyOf(extras));
		});
		var roteirizacao = reotimizacoes.pedirReotimizacao(pedido.unidade(), pedido.equipes(), pedido.extras());
		return transacao.execute(s -> {
			repositorio.decidir(pedido.sugestoes(), SituacaoSugestao.ACEITA, usuario(), roteirizacao);
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("sugestoes", pedido.sugestoes().size());
			detalhes.put("equipes", pedido.equipes().size());
			detalhes.put("emergenciais", pedido.extras().size());
			detalhes.put("roteirizacao", roteirizacao.toString());
			auditoria.registrar(TipoEvento.REOTIMIZACAO_SUGERIDA_ACEITA, "sugestao-reotimizacao/" + id, detalhes);
			return repositorio.buscar(id).orElseThrow();
		});
	}

	public Sugestao ignorar(UUID id) {
		return transacao.execute(s -> {
			var sugestao = aberta(id);
			repositorio.decidir(List.of(id), SituacaoSugestao.IGNORADA, usuario(), null);
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("gatilho", sugestao.gatilho().name());
			auditoria.registrar(TipoEvento.REOTIMIZACAO_SUGERIDA_IGNORADA, "sugestao-reotimizacao/" + id, detalhes);
			return repositorio.buscar(id).orElseThrow();
		});
	}

	private Sugestao aberta(UUID id) {
		var sugestao = repositorio.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (sugestao.situacao() != SituacaoSugestao.ABERTA) {
			throw new ConflitoException(switch (sugestao.situacao()) {
				case ACEITA -> "Esta sugestão já foi aceita.";
				case IGNORADA -> "Esta sugestão já foi ignorada.";
				default -> "Esta sugestão expirou: o que a abriu já mudou.";
			});
		}
		if (!sugestao.data().equals(LocalDate.now(relogio))) {
			throw new ConflitoException("Esta sugestão é de outro dia.");
		}
		return sugestao;
	}

	private static UUID usuario() {
		var usuario = ContextoAcesso.exigir().usuarioId();
		if (usuario == null) {
			throw new IllegalStateException("Decisão sem usuário no contexto");
		}
		return usuario;
	}

}
