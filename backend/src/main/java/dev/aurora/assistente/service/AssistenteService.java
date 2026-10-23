package dev.aurora.assistente.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.aurora.assistente.model.Conversa;
import dev.aurora.assistente.model.Mensagem;
import dev.aurora.assistente.model.TurnoAnterior;
import dev.aurora.assistente.repository.ConversaRepository;
import dev.aurora.assistente.repository.UsoRepository;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.LimiteDeUsoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Assistente com fontes (épico G, RN-11, ADR 0018). A chamada ao modelo acontece fora de
 * transação (não prende conexão); cada ferramenta abre a sua, curta e só de leitura. A
 * pergunta e a resposta são gravadas juntas no fim: falha do serviço de IA não deixa
 * pergunta sem resposta na conversa.
 */
@Service
public class AssistenteService {

	private static final Logger LOG = LoggerFactory.getLogger(AssistenteService.class);

	static final int TURNOS_NO_HISTORICO = 12;

	static final String RESPOSTA_RECUSADA = "Não posso responder a essa pergunta. Reformule com foco nos indicadores "
			+ "de adesão, metas, unidades ou alertas.";

	private static final Pattern MARCA = Pattern.compile("\\[(\\d+)]");

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/**
	 * Resultado de uma pergunta: a conversa (nova ou não) e as duas mensagens gravadas.
	 */
	public record Troca(Conversa conversa, Mensagem pergunta, Mensagem resposta) {
	}

	/** Situação do assistente para a tela. */
	public record Situacao(boolean disponivel, long perguntasNoMes, @Nullable BigDecimal custoNoMesUsd,
			@Nullable BigDecimal limiteMensalUsd) {
	}

	private final ModeloDoAssistente modelo;

	private final FerramentasDoAssistente ferramentas;

	private final ConversaRepository conversas;

	private final UsoRepository usos;

	private final Unidades unidades;

	private final Auditoria auditoria;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	AssistenteService(ModeloDoAssistente modelo, FerramentasDoAssistente ferramentas, ConversaRepository conversas,
			UsoRepository usos, Unidades unidades, Auditoria auditoria, TransactionTemplate transacao, Clock relogio) {
		this.modelo = modelo;
		this.ferramentas = ferramentas;
		this.conversas = conversas;
		this.usos = usos;
		this.unidades = unidades;
		this.auditoria = auditoria;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	/**
	 * Custo só aparece para quem atua no cliente inteiro (é informação do contrato com o
	 * provedor de IA, não da operação).
	 */
	@Transactional(readOnly = true)
	public Situacao situacao() {
		var uso = usos.buscarUso(YearMonth.now(relogio));
		var verCusto = ContextoAcesso.exigir().abrangeTenantInteiro();
		var limite = usos.buscarLimite().map(UsoRepository.Limite::usdPorMes).orElse(null);
		return new Situacao(modelo.disponivel(), uso.perguntas(), verCusto ? uso.custoUsd() : null,
				verCusto ? limite : null);
	}

	/**
	 * G1: responde à pergunta, continuando a conversa ou abrindo uma nova.
	 * @param unidadeContexto G5: unidade de onde a pessoa perguntou (ex.: o painel)
	 */
	public Troca perguntar(@Nullable UUID conversaId, String pergunta, @Nullable UUID unidadeContexto) {
		if (!modelo.disponivel()) {
			throw new AssistenteIndisponivelException(
					"O assistente está desligado neste ambiente. Peça ao administrador para configurar a chave da IA.");
		}
		var contexto = ContextoAcesso.exigir();
		var preparo = emTransacao(() -> preparar(conversaId, unidadeContexto));
		var coletor = new Coletor();
		var mensagem = mensagemComContexto(pergunta.strip(), preparo.unidadeContexto());

		var resposta = modelo.responder(preparo.historico(), mensagem, FerramentasDoAssistente.DEFINICOES,
				(nome, entrada) -> ferramentas.executar(nome, entrada, contexto, coletor));

		String texto;
		var fontes = coletor.fontesCitadas(resposta.texto());
		List<String> semFonte = List.of();
		if (resposta.recusada()) {
			texto = RESPOSTA_RECUSADA;
			fontes = List.of();
		}
		else {
			texto = limparMarcas(resposta.texto(), coletor.fontes().size());
			if (fontes.isEmpty() && !coletor.fontes().isEmpty()) {
				fontes = coletor.fontes();
			}
			semFonte = VerificadorDeNumeros.semFonte(texto, coletor.saidas());
			if (!semFonte.isEmpty()) {
				LOG.warn("Resposta do assistente com números sem fonte: {}", semFonte);
			}
		}
		var numeros = fontes.stream().map(f -> f.numero()).toList();
		var dados = coletor.dados().stream().filter(d -> numeros.contains(d.fonte())).toList();
		var custo = Optional.ofNullable(resposta.custoUsd());
		var textoFinal = texto;
		var fontesFinais = fontes;
		var semFonteFinal = semFonte;
		return emTransacao(() -> {
			var conversa = preparo.conversa();
			if (conversa == null) {
				var id = UUID.randomUUID();
				conversas.criar(id, titulo(pergunta));
				conversa = conversas.buscar(id).orElseThrow();
			}
			var idPergunta = UUID.randomUUID();
			var idResposta = UUID.randomUUID();
			conversas.inserirPergunta(idPergunta, conversa.id(), pergunta.strip());
			conversas.inserirResposta(idResposta, conversa.id(), textoFinal, fontesFinais, dados, semFonteFinal,
					resposta.modelo(), resposta.uso(), custo.orElse(BigDecimal.ZERO));
			usos.somar(YearMonth.now(relogio), resposta.uso(), custo.orElse(BigDecimal.ZERO), custo.isEmpty());
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("conversa", conversa.id().toString());
			detalhes.put("mensagem", idResposta.toString());
			detalhes.put("modelo", resposta.modelo());
			detalhes.put("fontes", fontesFinais.size());
			detalhes.put("numerosSemFonte", semFonteFinal.size());
			detalhes.put("recusada", resposta.recusada());
			detalhes.put("tokensEntrada", resposta.uso().entrada());
			detalhes.put("tokensSaida", resposta.uso().saida());
			detalhes.put("custoUsd", custo.map(BigDecimal::toPlainString).orElse(null));
			auditoria.registrar(TipoEvento.PERGUNTA_IA, "assistente/conversas/" + conversa.id(), detalhes);
			return new Troca(conversas.buscar(conversa.id()).orElseThrow(),
					conversas.buscarMensagem(idPergunta).orElseThrow(),
					conversas.buscarMensagem(idResposta).orElseThrow());
		});
	}

	@Transactional(readOnly = true)
	public List<Conversa> listar(Optional<String> busca) {
		return conversas.listar(busca.map(String::strip).filter(b -> !b.isEmpty()).orElse(null), 50);
	}

	@Transactional(readOnly = true)
	public List<Mensagem> mensagens(UUID conversa) {
		conversas.buscar(conversa).orElseThrow(RecursoNaoEncontradoException::new);
		return conversas.listarMensagens(conversa);
	}

	@Transactional(readOnly = true)
	public Conversa conversa(UUID id) {
		return conversas.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
	}

	/** G4: "Ajudou? Sim / Tem erro", com comentário opcional no "Tem erro". */
	@Transactional
	public void avaliar(UUID mensagem, int avaliacao, @Nullable String comentario) {
		var limpo = avaliacao < 0 && comentario != null && !comentario.isBlank() ? comentario.strip() : null;
		if (!conversas.avaliar(mensagem, avaliacao, limpo)) {
			throw new RecursoNaoEncontradoException();
		}
		var detalhes = new LinkedHashMap<String, Object>();
		detalhes.put("mensagem", mensagem.toString());
		detalhes.put("avaliacao", avaliacao);
		auditoria.registrar(TipoEvento.RESPOSTA_IA_AVALIADA, "assistente/mensagens/" + mensagem, detalhes);
	}

	private record Preparo(@Nullable Conversa conversa, List<TurnoAnterior> historico,
			@Nullable String unidadeContexto) {
	}

	private Preparo preparar(@Nullable UUID conversaId, @Nullable UUID unidadeContexto) {
		var uso = usos.buscarUso(YearMonth.now(relogio));
		usos.buscarLimite().ifPresent(limite -> {
			if (limite.usdPorMes() != null && uso.custoUsd().compareTo(limite.usdPorMes()) >= 0
					|| limite.tokensPorMes() != null && uso.tokens() >= limite.tokensPorMes()) {
				throw new LimiteDeUsoException(
						"O limite mensal do assistente deste cliente foi atingido. Ele volta no próximo mês.");
			}
		});
		Conversa conversa = null;
		var historico = new ArrayList<TurnoAnterior>();
		if (conversaId != null) {
			conversa = conversas.buscar(conversaId).orElseThrow(RecursoNaoEncontradoException::new);
			var mensagens = conversas.listarMensagens(conversaId);
			mensagens.stream()
				.skip(Math.max(0, mensagens.size() - TURNOS_NO_HISTORICO * 2L))
				.forEach(m -> historico.add(new TurnoAnterior(m.papel(), m.texto())));
		}
		var nome = unidadeContexto == null ? null
				: unidades.buscarNoEscopo(unidadeContexto).map(u -> u.nome()).orElse(null);
		return new Preparo(conversa, historico, nome);
	}

	/**
	 * Contexto do pedido vai na mensagem do usuário (depois do prefixo em cache): data de
	 * hoje e a unidade de onde perguntou. O texto da pessoa vai marcado como pergunta.
	 */
	String mensagemComContexto(String pergunta, @Nullable String unidade) {
		var texto = new StringBuilder("<contexto>\nHoje: ").append(DATA.format(LocalDate.now(relogio))).append('\n');
		if (unidade != null) {
			texto.append("A pessoa perguntou a partir da unidade: ").append(unidade).append('\n');
		}
		return texto.append("</contexto>\n<pergunta>\n").append(pergunta).append("\n</pergunta>").toString();
	}

	/** Tira marcas [n] que não correspondem a nenhuma fonte lida. */
	static String limparMarcas(String texto, int fontes) {
		return MARCA.matcher(texto).replaceAll(m -> {
			int n = Integer.parseInt(m.group(1));
			return n >= 1 && n <= fontes ? m.group() : "";
		}).strip();
	}

	static String titulo(String pergunta) {
		var limpo = pergunta.strip().replaceAll("\\s+", " ");
		return limpo.length() <= 80 ? limpo : limpo.substring(0, 79) + "…";
	}

	private <T> T emTransacao(java.util.function.Supplier<T> acao) {
		return transacao.execute(status -> acao.get());
	}

}
