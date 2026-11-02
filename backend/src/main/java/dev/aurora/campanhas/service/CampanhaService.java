package dev.aurora.campanhas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.adesao.IndicadorDaUnidade;
import dev.aurora.adesao.Medicoes;
import dev.aurora.adesao.Situacao;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.model.AcaoCampanha;
import dev.aurora.campanhas.model.Campanha;
import dev.aurora.campanhas.model.Candidato;
import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.Distribuicao;
import dev.aurora.campanhas.model.FiltroOrdens;
import dev.aurora.campanhas.model.ImpactoNaMeta;
import dev.aurora.campanhas.model.NovaCampanha;
import dev.aurora.campanhas.model.OrigemCampanha;
import dev.aurora.campanhas.model.PaginaOrdens;
import dev.aurora.campanhas.model.PainelCampanha;
import dev.aurora.campanhas.model.Previa;
import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.campanhas.model.Recorte;
import dev.aurora.campanhas.model.ResultadoDistribuicao;
import dev.aurora.campanhas.model.StatusCampanha;
import dev.aurora.campanhas.repository.CampanhaRepository;
import dev.aurora.campanhas.repository.EquipeRepository;
import dev.aurora.campanhas.repository.OrdemServicoRepository;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.unidades.Unidade;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campanhas de ligação (I2, I4; ADR 0036): recorte dos imóveis sem ligação da competência
 * mais recente, OS priorizadas pelo semáforo da unidade (RN-19), envio às equipes e o
 * painel com funil, produtividade e efeito na adesão.
 */
@Service
public class CampanhaService {

	/** Tamanho máximo de uma campanha: acima disso, dividir por setor ou área. */
	static final int LIMITE_DE_ORDENS = 5_000;

	/** Prazo máximo de uma campanha a partir de hoje. */
	static final int PRAZO_MAXIMO_EM_DIAS = 366;

	private static final String SETOR = "SETOR_COMERCIAL";

	private final CampanhaRepository campanhas;

	private final OrdemServicoRepository ordens;

	private final EquipeRepository equipes;

	private final Medicoes medicoes;

	private final Unidades unidades;

	private final Auditoria auditoria;

	private final DadosPessoaisDasOrdens dadosPessoais;

	private final Clock relogio;

	CampanhaService(CampanhaRepository campanhas, OrdemServicoRepository ordens, EquipeRepository equipes,
			Medicoes medicoes, Unidades unidades, Auditoria auditoria, DadosPessoaisDasOrdens dadosPessoais,
			Clock relogio) {
		this.campanhas = campanhas;
		this.ordens = ordens;
		this.equipes = equipes;
		this.medicoes = medicoes;
		this.unidades = unidades;
		this.auditoria = auditoria;
		this.dadosPessoais = dadosPessoais;
		this.relogio = relogio;
	}

	@Transactional(readOnly = true)
	public Previa previa(Recorte recorte) {
		var unidade = validar(recorte);
		var competencia = competencia();
		var candidatos = campanhas.candidatos(recorte, competencia);
		var novos = candidatos.stream().filter(c -> !c.comOsAberta()).toList();
		var prioridades = prioridades(novos, competencia);
		var contagem = new ContagemPorPrioridade();
		novos.forEach(c -> contagem.somar(prioridades.get(c.unidadeId())));
		return new Previa(competencia, novos.size(),
				novos.stream().filter(c -> "FACTIVEL".equals(c.situacao())).count(),
				novos.stream().filter(c -> "CORTADO".equals(c.situacao())).count(), candidatos.size() - novos.size(),
				contagem.alta, contagem.media, contagem.baixa, LIMITE_DE_ORDENS,
				impacto(unidade, competencia, novos.size()));
	}

	@Transactional
	public Campanha criar(NovaCampanha nova) {
		var nome = nova.nome().strip().replaceAll("\\s+", " ");
		if (nome.length() < 3) {
			throw new RegraVioladaException("Dê um nome à campanha (ao menos 3 letras).");
		}
		var hoje = LocalDate.now(relogio);
		if (nova.prazo().isBefore(hoje) || nova.prazo().isAfter(hoje.plusDays(PRAZO_MAXIMO_EM_DIAS))) {
			throw new RegraVioladaException("O prazo deve ficar entre hoje e daqui a um ano.");
		}
		var unidade = validar(nova.recorte());
		var competencia = competencia();
		var novos = campanhas.candidatos(nova.recorte(), competencia).stream().filter(c -> !c.comOsAberta()).toList();
		if (novos.isEmpty()) {
			throw new RegraVioladaException("Nenhum imóvel sem ligação neste recorte (sem OS em aberto).");
		}
		if (novos.size() > LIMITE_DE_ORDENS) {
			throw new RegraVioladaException("O recorte tem " + novos.size() + " imóveis; o limite por campanha é "
					+ LIMITE_DE_ORDENS + ". Divida a campanha por setor ou por área do mapa.");
		}
		var impacto = impacto(unidade, competencia, novos.size());
		var id = UUID.randomUUID();
		var usuario = ContextoAcesso.exigir().usuarioId();
		if (usuario == null) {
			throw new IllegalStateException("Campanha criada fora de uma requisição");
		}
		campanhas.inserir(id, nome, nova.recorte(), nova.acao(), competencia, nova.prazo(), impacto.ligados(),
				impacto.comRede(), usuario);
		var criadas = ordens.inserir(id, nova.prazo(),
				novasOrdens(novos, nova.acao(), prioridades(novos, competencia)));
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("nome", nome);
		detalhes.put("origem", nova.recorte().origem().name());
		detalhes.put("unidade", unidade.id().toString());
		detalhes.put("acao", nova.acao().name());
		detalhes.put("competencia", competencia.toString());
		detalhes.put("prazo", nova.prazo().toString());
		detalhes.put("ordens", criadas);
		auditoria.registrar(TipoEvento.CAMPANHA_CRIADA, "campanha/" + id, detalhes);
		return campanhas.buscar(id, hoje).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<Campanha> listar(@Nullable StatusCampanha status) {
		return campanhas.listar(status, LocalDate.now(relogio));
	}

	@Transactional(readOnly = true)
	public PainelCampanha painel(UUID id) {
		var hoje = LocalDate.now(relogio);
		var campanha = campanhas.buscar(id, hoje).orElseThrow(RecursoNaoEncontradoException::new);
		var unidade = unidades.buscarNoEscopo(campanha.unidadeId()).orElseThrow(RecursoNaoEncontradoException::new);
		var competencias = medicoes.competencias();
		var funil = campanha.funil();
		// Se as OS de ligação ainda em aberto virarem ligação.
		var abertasDeLigacao = campanha.acao() == AcaoCampanha.LIGACAO
				? funil.planejadas() - funil.executadas() - funil.recusadas() - funil.inviaveis() : 0;
		var agora = competencias.isEmpty() ? impacto(unidade, null, null, 0) : impacto(unidade, competencias.getFirst(),
				indicador(unidade.id(), competencias.getFirst()), Math.max(0, abertasDeLigacao));
		return new PainelCampanha(campanha, ordens.produtividade(id, hoje), ordens.motivos(id), agora);
	}

	@Transactional
	public PaginaOrdens ordens(UUID id, FiltroOrdens filtro, int pagina, int tamanho) {
		var hoje = LocalDate.now(relogio);
		var campanha = campanhas.buscar(id, hoje).orElseThrow(RecursoNaoEncontradoException::new);
		var itens = ordens.listar(id, filtro, hoje, pagina, tamanho);
		var visiveis = dadosPessoais.aplicar(itens, "Ordens de serviço da campanha " + campanha.nome());
		return new PaginaOrdens(visiveis, ordens.contar(id, filtro, hoje), pagina, tamanho);
	}

	@Transactional
	public ResultadoDistribuicao distribuir(UUID id, Distribuicao pedido) {
		var status = campanhas.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (status != StatusCampanha.ATIVA) {
			throw new ConflitoException("A campanha não está ativa.");
		}
		var equipe = equipes.buscar(pedido.equipeId())
			.orElseThrow(() -> new RegraVioladaException("Escolha uma equipe da sua área."));
		if (!equipe.ativa()) {
			throw new RegraVioladaException("A equipe está desativada.");
		}
		if (pedido.quantidade() != null && pedido.quantidade() <= 0) {
			throw new RegraVioladaException("Informe quantas OS enviar.");
		}
		var lista = pedido.ordens().isEmpty() ? null : pedido.ordens();
		if (lista != null && lista.size() > LIMITE_DE_ORDENS) {
			throw new RegraVioladaException("Envie no máximo " + LIMITE_DE_ORDENS + " OS por vez.");
		}
		var limite = pedido.quantidade() == null ? LIMITE_DE_ORDENS : Math.min(pedido.quantidade(), LIMITE_DE_ORDENS);
		var envio = ordens.distribuir(id, equipe.id(), lista, pedido.prioridade(), limite);
		if (envio.enviadas() > 0) {
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("equipe", equipe.id().toString());
			detalhes.put("equipeNome", equipe.nome());
			detalhes.put("enviadas", envio.enviadas());
			detalhes.put("foraDaArea", envio.foraDaArea());
			auditoria.registrar(TipoEvento.ORDENS_DISTRIBUIDAS, "campanha/" + id, detalhes);
		}
		return new ResultadoDistribuicao(envio.enviadas(), envio.foraDaArea());
	}

	@Transactional
	public Campanha encerrar(UUID id) {
		return finalizar(id, StatusCampanha.ENCERRADA, TipoEvento.CAMPANHA_ENCERRADA);
	}

	@Transactional
	public Campanha cancelar(UUID id) {
		return finalizar(id, StatusCampanha.CANCELADA, TipoEvento.CAMPANHA_CANCELADA);
	}

	private Campanha finalizar(UUID id, StatusCampanha novo, TipoEvento evento) {
		var status = campanhas.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (status != StatusCampanha.ATIVA) {
			throw new ConflitoException("A campanha já foi encerrada ou cancelada.");
		}
		var usuario = ContextoAcesso.exigir().usuarioId();
		if (usuario == null) {
			throw new IllegalStateException("Campanha finalizada fora de uma requisição");
		}
		var canceladas = ordens.cancelarAbertas(id);
		campanhas.finalizar(id, novo, usuario);
		auditoria.registrar(evento, "campanha/" + id, Map.of("ordensCanceladas", canceladas));
		return campanhas.buscar(id, LocalDate.now(relogio)).orElseThrow();
	}

	private Unidade validar(Recorte recorte) {
		var unidade = unidades.buscarNoEscopo(recorte.unidadeId())
			.orElseThrow(() -> new RegraVioladaException("Escolha uma unidade onde você gerencia campanhas."));
		if (recorte.origem() == OrigemCampanha.SETOR && !SETOR.equals(unidade.tipo())) {
			throw new RegraVioladaException("Na campanha por setor, escolha um setor comercial.");
		}
		if ((recorte.origem() == OrigemCampanha.AREA_DO_MAPA) != (recorte.area() != null)) {
			throw new RegraVioladaException(recorte.area() == null ? "Informe a área do mapa."
					: "A área do mapa só vale na campanha por área.");
		}
		if (recorte.situacao() != null && !List.of("FACTIVEL", "CORTADO").contains(recorte.situacao())) {
			throw new RegraVioladaException("Situação deve ser factível ou cortado.");
		}
		if (recorte.motivoCorte() != null) {
			if (!List.of("INADIMPLENCIA", "A_PEDIDO").contains(recorte.motivoCorte())) {
				throw new RegraVioladaException("Motivo do corte inválido.");
			}
			if ("FACTIVEL".equals(recorte.situacao())) {
				throw new RegraVioladaException("O motivo do corte só vale para imóveis cortados.");
			}
		}
		return unidade;
	}

	private YearMonth competencia() {
		return campanhas.ultimaCompetencia()
			.orElseThrow(() -> new RegraVioladaException("Ainda não há carga de imóveis na sua área."));
	}

	/** RN-19: prioridade de cada unidade pelo semáforo (RN-03) na competência. */
	private Map<UUID, Prioridade> prioridades(List<Candidato> candidatos, YearMonth competencia) {
		var ids = candidatos.stream().map(Candidato::unidadeId).distinct().toList();
		var resultado = new HashMap<UUID, Prioridade>();
		ids.forEach(id -> resultado.put(id, Prioridade.BAIXA));
		medicoes.detalhar(ids, competencia).forEach(i -> resultado.put(i.unidadeId(), prioridade(i.situacao())));
		return resultado;
	}

	static Prioridade prioridade(Situacao situacao) {
		return switch (situacao) {
			case ABAIXO_DO_ALERTA -> Prioridade.ALTA;
			case ATENCAO -> Prioridade.MEDIA;
			case META_ATINGIDA, SEM_META -> Prioridade.BAIXA;
		};
	}

	private static OrdemServicoRepository.NovasOrdens novasOrdens(List<Candidato> candidatos, AcaoCampanha acao,
			Map<UUID, Prioridade> prioridades) {
		var ordenados = new ArrayList<>(candidatos);
		ordenados.sort((a, b) -> {
			var p = prioridades.get(a.unidadeId()).compareTo(prioridades.get(b.unidadeId()));
			return p != 0 ? p : a.matricula().compareTo(b.matricula());
		});
		var matriculas = new ArrayList<String>();
		var unidadesDasOrdens = new ArrayList<UUID>();
		var tipos = new ArrayList<String>();
		var situacoes = new ArrayList<String>();
		var prioridadesDasOrdens = new ArrayList<String>();
		var conferencias = new ArrayList<String>();
		for (var c : ordenados) {
			matriculas.add(c.matricula());
			unidadesDasOrdens.add(c.unidadeId());
			tipos.add(acao.tipoPara(c.situacao()).name());
			situacoes.add(c.situacao());
			prioridadesDasOrdens.add(prioridades.get(c.unidadeId()).name());
			// A conferência começa quando a OS de ligação é executada.
			conferencias.add(Conferencia.NAO_SE_APLICA.name());
		}
		return new OrdemServicoRepository.NovasOrdens(matriculas, unidadesDasOrdens, tipos, situacoes,
				prioridadesDasOrdens, conferencias);
	}

	private ImpactoNaMeta impacto(Unidade unidade, YearMonth competencia, long novasLigacoes) {
		return impacto(unidade, competencia, indicador(unidade.id(), competencia), novasLigacoes);
	}

	private @Nullable IndicadorDaUnidade indicador(UUID unidade, YearMonth competencia) {
		return medicoes.detalhar(List.of(unidade), competencia).stream().findFirst().orElse(null);
	}

	/**
	 * Adesão da unidade e o que muda se {@code novasLigacoes} imóveis se ligarem (RN-01:
	 * ligados ÷ com rede; a rede não muda).
	 */
	static ImpactoNaMeta impacto(Unidade unidade, @Nullable YearMonth competencia,
			@Nullable IndicadorDaUnidade indicador, long novasLigacoes) {
		if (indicador == null || indicador.comRede() == 0) {
			return new ImpactoNaMeta(unidade.id(), unidade.nome(), competencia, 0, 0, null, null, null, null);
		}
		var comRede = BigDecimal.valueOf(indicador.comRede());
		var ligados = indicador.ligados();
		var adesao = BigDecimal.valueOf(ligados * 100).divide(comRede, 2, RoundingMode.HALF_UP);
		var seTodas = BigDecimal.valueOf(Math.min(ligados + novasLigacoes, indicador.comRede()) * 100)
			.divide(comRede, 2, RoundingMode.HALF_UP);
		Long faltam = null;
		if (indicador.valorMeta() != null) {
			var necessarios = indicador.valorMeta()
				.multiply(comRede)
				.divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING);
			faltam = Math.max(0, necessarios.longValueExact() - ligados);
		}
		return new ImpactoNaMeta(unidade.id(), unidade.nome(), indicador.competencia(), ligados, indicador.comRede(),
				adesao, seTodas, indicador.valorMeta(), faltam);
	}

	/** Contagem por prioridade. */
	private static final class ContagemPorPrioridade {

		long alta;

		long media;

		long baixa;

		void somar(Prioridade prioridade) {
			switch (prioridade) {
				case ALTA -> alta++;
				case MEDIA -> media++;
				case BAIXA -> baixa++;
			}
		}

	}

}
