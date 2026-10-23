package dev.aurora.assistente.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.aurora.adesao.IndicadorDaUnidade;
import dev.aurora.adesao.Medicoes;
import dev.aurora.adesao.Situacao;
import dev.aurora.assistente.model.DefinicaoFerramenta;
import dev.aurora.assistente.model.SaidaFerramenta;
import dev.aurora.assistente.repository.AlertaLeituraRepository;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.unidades.Unidade;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ferramentas do assistente (doc 05, 5.4): consultas fixas e parametrizadas, nunca SQL do
 * modelo. Rodam no escopo onde a pessoa pode usar o assistente <em>e</em> ler indicadores
 * ({@link ContextoAcesso#restritoATodas}); unidade fora dele é "não encontrada", sem
 * revelar que existe (RN-11, RN-13). Cada resultado vira uma fonte numerada.
 */
@Component
class FerramentasDoAssistente {

	static final String PERMISSAO_ASSISTENTE = "assistente:usar";

	static final String PERMISSAO_INDICADORES = "indicador:ler";

	static final int MAXIMO_DE_UNIDADES = 60;

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMM/yyyy", PT_BR);

	private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR)
		.withZone(ZoneId.of("America/Sao_Paulo"));

	private static final Map<String, String> TIPOS = Map.of("GRUPO", "Grupo econômico", "CONCESSAO", "Concessão / SPE",
			"MUNICIPIO", "Município", "SETOR_COMERCIAL", "Setor comercial", "ETE", "Estação de tratamento de esgoto",
			"EEE", "Estação elevatória de esgoto");

	private static final Set<String> COM_MEDICAO = Set.of("GRUPO", "CONCESSAO", "MUNICIPIO", "SETOR_COMERCIAL");

	private static final Map<Situacao, String> SITUACOES = Map.of(Situacao.META_ATINGIDA, "Meta atingida",
			Situacao.ATENCAO, "Atenção", Situacao.ABAIXO_DO_ALERTA, "Abaixo do alerta", Situacao.SEM_META, "Sem meta");

	static final List<DefinicaoFerramenta> DEFINICOES = List.of(new DefinicaoFerramenta("consultar_adesao", """
			Adesão à rede de esgoto de unidades numa competência (mês): percentual, ligados, imóveis com rede, \
			sem ligação (factíveis e cortados), variação sobre o mês anterior, meta e limite de alerta \
			aplicáveis (própria ou herdada, com a cláusula do contrato), quanto falta para a meta e a \
			situação (Meta atingida, Atenção, Abaixo do alerta, Sem meta). Use para qualquer pergunta sobre \
			adesão, metas, situação ou imóveis sem ligação.""", Map.of("unidades",
			Map.of("type", "array", "items", Map.of("type", "string"), "description",
					"Nomes das unidades como o usuário escreveu (ou ids). Lista vazia = unidade principal do escopo."),
			"incluir_subordinadas",
			Map.of("type", "boolean", "description",
					"true para trazer também as unidades logo abaixo de cada uma (ex.: setores de um município)."),
			"competencia",
			Map.of("type", List.of("string", "null"), "description",
					"Mês no formato AAAA-MM; null = mês mais recente com dados.")),
			List.of("unidades", "incluir_subordinadas", "competencia")),
			new DefinicaoFerramenta("listar_unidades",
					"Unidades do escopo do usuário (grupo, concessão, município, setor comercial, ETE, EEE) com a unidade "
							+ "superior. Use para saber quais unidades existem ou para achar o nome certo.",
					Map.of("tipo",
							Map.of("type", List.of("string", "null"), "enum",
									java.util.Arrays.asList("CONCESSAO", "MUNICIPIO", "SETOR_COMERCIAL", "ETE", "EEE",
											null),
									"description", "Filtra pelo tipo; null = todos."),
							"nome_contem",
							Map.of("type", List.of("string", "null"), "description",
									"Parte do nome, sem diferenciar acentos; null = todos.")),
					List.of("tipo", "nome_contem")),
			new DefinicaoFerramenta(
					"listar_competencias", "Meses com dados de adesão, do mais recente ao mais antigo.", Map.of(), List
						.of()),
			new DefinicaoFerramenta("listar_alertas_abertos",
					"Alertas ainda abertos (adesão abaixo da meta ou do alerta, fonte de dados atrasada).",
					Map.of("unidade", Map.of("type", List.of("string", "null"), "description",
							"Limita à unidade e às de baixo; null = todo o escopo.")),
					List.of("unidade")));

	private final Unidades unidades;

	private final Medicoes medicoes;

	private final AlertaLeituraRepository alertas;

	private final TransactionTemplate transacao;

	private final JsonMapper json;

	FerramentasDoAssistente(Unidades unidades, Medicoes medicoes, AlertaLeituraRepository alertas,
			TransactionTemplate transacao, JsonMapper json) {
		this.unidades = unidades;
		this.medicoes = medicoes;
		this.alertas = alertas;
		this.transacao = transacao;
		this.json = json;
	}

	/**
	 * Executa a ferramenta numa transação curta só de leitura, no escopo restrito.
	 * Entrada inválida volta como erro para o modelo corrigir (não derruba a pergunta).
	 */
	SaidaFerramenta executar(String nome, Map<String, Object> entrada, ContextoAcesso contexto, Coletor coletor) {
		var restrito = contexto.restritoATodas(PERMISSAO_ASSISTENTE, PERMISSAO_INDICADORES);
		try {
			var resultado = ContextoAcesso.executar(restrito, () -> transacao.execute(status -> {
				status.setRollbackOnly();
				return switch (nome) {
					case "consultar_adesao" -> consultarAdesao(entrada, coletor);
					case "listar_unidades" -> listarUnidades(entrada, coletor);
					case "listar_competencias" -> listarCompetencias(coletor);
					case "listar_alertas_abertos" -> listarAlertas(entrada, restrito, coletor);
					default -> throw new EntradaInvalida("Ferramenta desconhecida: " + nome + ".");
				};
			}));
			var saida = json.writeValueAsString(resultado);
			coletor.guardarSaida(saida);
			return new SaidaFerramenta(saida, false);
		}
		catch (EntradaInvalida erro) {
			return new SaidaFerramenta(json.writeValueAsString(Map.of("erro", erro.getMessage())), true);
		}
		catch (RuntimeException erro) {
			throw erro;
		}
		catch (Exception erro) {
			throw new IllegalStateException(erro);
		}
	}

	private Map<String, Object> consultarAdesao(Map<String, Object> entrada, Coletor coletor) {
		var competencias = medicoes.competencias();
		if (competencias.isEmpty()) {
			return Map.of("resultado", "Não há medições de adesão no seu escopo.");
		}
		var competencia = competencia(entrada.get("competencia"), competencias);
		var pedidas = resolverUnidades(texto(entrada.get("unidades")));
		var alvo = new LinkedHashSet<>(pedidas);
		if (Boolean.TRUE.equals(entrada.get("incluir_subordinadas"))) {
			for (var unidade : pedidas) {
				unidades.doEscopo()
					.stream()
					.filter(u -> unidade.id().equals(u.unidadePaiId()) && COM_MEDICAO.contains(u.tipo()))
					.forEach(alvo::add);
			}
		}
		if (alvo.size() > MAXIMO_DE_UNIDADES) {
			throw new EntradaInvalida(
					"Unidades demais (" + alvo.size() + "); peça no máximo " + MAXIMO_DE_UNIDADES + " por vez.");
		}
		var porId = new LinkedHashMap<UUID, Unidade>();
		alvo.forEach(u -> porId.put(u.id(), u));
		var detalhes = medicoes.detalhar(porId.keySet(), competencia);
		var linhas = new ArrayList<Map<String, Object>>();
		var comMedicao = new LinkedHashSet<UUID>();
		for (var d : detalhes) {
			var unidade = porId.get(d.unidadeId());
			comMedicao.add(d.unidadeId());
			var fonte = coletor.registrar("Adesão à rede de esgoto", unidade.nome() + " · " + mes(competencia),
					unidade.nome() + " · " + mes(competencia), campos(unidade, d));
			linhas.add(linha(fonte, unidade, d));
		}
		var semMedicao = porId.values().stream().filter(u -> !comMedicao.contains(u.id())).map(Unidade::nome).toList();
		var resultado = new LinkedHashMap<String, Object>();
		resultado.put("competencia", competencia.toString());
		resultado.put("unidades", linhas);
		if (!semMedicao.isEmpty()) {
			resultado.put("unidadesSemMedicaoNoMes", semMedicao);
		}
		return resultado;
	}

	private Map<String, Object> linha(int fonte, Unidade unidade, IndicadorDaUnidade d) {
		var linha = new LinkedHashMap<String, Object>();
		linha.put("fonte", fonte);
		linha.put("unidade", unidade.nome());
		linha.put("tipo", TIPOS.getOrDefault(unidade.tipo(), unidade.tipo()));
		linha.put("adesaoPercentual", duasCasas(d.adesao()));
		linha.put("ligados", d.ligados());
		linha.put("imoveisComRede", d.comRede());
		linha.put("semLigacao", d.comRede() - d.ligados());
		linha.put("factiveisSemLigacao", d.factiveis());
		linha.put("cortados", d.cortados());
		linha.put("adesaoMesAnteriorPercentual", duasCasas(d.adesaoAnterior()));
		linha.put("variacaoPontosPercentuais", duasCasas(d.variacaoPp()));
		linha.put("metaPercentual", duasCasas(d.valorMeta()));
		linha.put("limiteDeAlertaPercentual", duasCasas(d.valorAlerta()));
		linha.put("faltaParaMetaPontosPercentuais", duasCasas(d.faltaParaMetaPp()));
		linha.put("metaHerdadaDe", d.metaHerdadaDe());
		linha.put("origemDaMeta",
				d.origemMeta() == null ? (d.valorMeta() == null ? null : "Meta manual") : d.origemMeta());
		linha.put("vigenciaFimDaMeta", d.vigenciaFimMeta() == null ? null : d.vigenciaFimMeta().toString());
		linha.put("situacao", SITUACOES.get(d.situacao()));
		return linha;
	}

	private static Map<String, String> campos(Unidade unidade, IndicadorDaUnidade d) {
		var campos = new LinkedHashMap<String, String>();
		campos.put("Tipo", TIPOS.getOrDefault(unidade.tipo(), unidade.tipo()));
		campos.put("Adesão", percentual(d.adesao()));
		campos.put("Ligados", inteiro(d.ligados()));
		campos.put("Com rede", inteiro(d.comRede()));
		campos.put("Sem ligação", inteiro(d.comRede() - d.ligados()) + " (" + inteiro(d.factiveis()) + " factíveis, "
				+ inteiro(d.cortados()) + " cortados)");
		campos.put("Variação", d.variacaoPp() == null ? "—" : sinal(d.variacaoPp()) + " p.p.");
		campos.put("Meta", d.valorMeta() == null ? "—" : percentual(d.valorMeta())
				+ (d.metaHerdadaDe() == null ? "" : " (herdada de " + d.metaHerdadaDe() + ")"));
		campos.put("Limite de alerta", percentual(d.valorAlerta()));
		campos.put("Situação", SITUACOES.get(d.situacao()));
		if (d.origemMeta() != null) {
			campos.put("Origem da meta", d.origemMeta());
		}
		return campos;
	}

	private Map<String, Object> listarUnidades(Map<String, Object> entrada, Coletor coletor) {
		var tipo = entrada.get("tipo") instanceof String t ? t : null;
		var nome = entrada.get("nome_contem") instanceof String n && !n.isBlank() ? normalizar(n) : null;
		var todas = unidades.doEscopo();
		var nomes = new LinkedHashMap<UUID, String>();
		todas.forEach(u -> nomes.put(u.id(), u.nome()));
		var encontradas = todas.stream()
			.filter(u -> tipo == null || tipo.equals(u.tipo()))
			.filter(u -> nome == null || normalizar(u.nome()).contains(nome))
			.limit(200)
			.toList();
		var campos = new LinkedHashMap<String, String>();
		encontradas.stream().limit(40).forEach(u -> campos.put(u.nome(), TIPOS.getOrDefault(u.tipo(), u.tipo())));
		var fonte = coletor.registrar("Cadastro de unidades", encontradas.size() + " unidade(s)",
				"Unidades encontradas", campos);
		var lista = encontradas.stream().map(u -> {
			var item = new LinkedHashMap<String, Object>();
			item.put("nome", u.nome());
			item.put("tipo", TIPOS.getOrDefault(u.tipo(), u.tipo()));
			item.put("unidadeSuperior", u.unidadePaiId() == null ? null : nomes.get(u.unidadePaiId()));
			return item;
		}).toList();
		return Map.of("fonte", fonte, "quantidade", encontradas.size(), "unidades", lista);
	}

	private Map<String, Object> listarCompetencias(Coletor coletor) {
		var competencias = medicoes.competencias();
		var campos = new LinkedHashMap<String, String>();
		competencias.stream().limit(24).forEach(c -> campos.put(c.toString(), mes(c)));
		var fonte = coletor.registrar("Competências com dados", competencias.size() + " mês(es)", "Meses com medição",
				campos);
		return Map.of("fonte", fonte, "competencias", competencias.stream().map(YearMonth::toString).toList());
	}

	private Map<String, Object> listarAlertas(Map<String, Object> entrada, ContextoAcesso contexto, Coletor coletor) {
		String caminho = null;
		String onde = "todo o escopo";
		if (entrada.get("unidade") instanceof String nome && !nome.isBlank()) {
			var unidade = resolverUnidades(List.of(nome)).getFirst();
			caminho = unidades.caminhoNoEscopo(unidade.id()).orElseThrow();
			onde = unidade.nome();
		}
		var abertos = alertas.listarAbertos(caminho, contexto.abrangeTenantInteiro(), 50);
		var campos = new LinkedHashMap<String, String>();
		abertos.stream()
			.limit(20)
			.forEach(a -> campos.put(a.titulo(),
					rotuloSeveridade(a.severidade()) + " · desde " + DATA_HORA.format(a.desde())));
		var fonte = coletor.registrar("Alertas abertos", abertos.size() + " alerta(s) · " + onde, "Alertas abertos",
				campos);
		var lista = abertos.stream().map(a -> {
			var item = new LinkedHashMap<String, Object>();
			item.put("titulo", a.titulo());
			item.put("descricao", a.descricao());
			item.put("severidade", rotuloSeveridade(a.severidade()));
			item.put("unidade", a.unidade());
			item.put("desde", DATA_HORA.format(a.desde()));
			return item;
		}).toList();
		return Map.of("fonte", fonte, "quantidade", abertos.size(), "alertas", lista);
	}

	/**
	 * Por id, nome exato ou parte do nome (sem acento nem caixa). Ambígua ou
	 * desconhecida: erro para o modelo perguntar ou listar, sem revelar unidades fora do
	 * escopo.
	 */
	List<Unidade> resolverUnidades(List<String> pedidas) {
		var escopo = unidades.doEscopo();
		if (pedidas.isEmpty()) {
			return List.of(unidades.principalDoEscopo()
				.orElseThrow(() -> new EntradaInvalida("O usuário não tem unidades no escopo.")));
		}
		var resolvidas = new ArrayList<Unidade>();
		for (var pedida : pedidas) {
			var chave = normalizar(pedida);
			var exatas = escopo.stream()
				.filter(u -> u.id().toString().equals(pedida.strip()) || normalizar(u.nome()).equals(chave))
				.toList();
			var candidatas = exatas.isEmpty()
					? escopo.stream().filter(u -> normalizar(u.nome()).contains(chave)).toList() : exatas;
			if (candidatas.isEmpty()) {
				throw new EntradaInvalida("Unidade \"" + pedida
						+ "\" não encontrada no escopo do usuário. Use listar_unidades para ver os nomes.");
			}
			if (candidatas.size() > 1) {
				throw new EntradaInvalida("\"" + pedida + "\" corresponde a várias unidades: "
						+ String.join(", ", candidatas.stream().limit(10).map(Unidade::nome).toList())
						+ ". Use o nome completo.");
			}
			resolvidas.add(candidatas.getFirst());
		}
		return resolvidas;
	}

	private static YearMonth competencia(@Nullable Object pedida, List<YearMonth> disponiveis) {
		if (!(pedida instanceof String texto) || texto.isBlank()) {
			return disponiveis.getFirst();
		}
		try {
			var mes = YearMonth.parse(texto.strip());
			if (!disponiveis.contains(mes)) {
				throw new EntradaInvalida("Não há medição em " + texto + ". Meses disponíveis: "
						+ String.join(", ", disponiveis.stream().limit(12).map(YearMonth::toString).toList()) + ".");
			}
			return mes;
		}
		catch (DateTimeParseException erro) {
			throw new EntradaInvalida("Competência inválida: use AAAA-MM.");
		}
	}

	private static List<String> texto(@Nullable Object valor) {
		if (valor instanceof List<?> lista) {
			return lista.stream()
				.filter(String.class::isInstance)
				.map(String.class::cast)
				.filter(s -> !s.isBlank())
				.toList();
		}
		return List.of();
	}

	static String normalizar(String texto) {
		return Normalizer.normalize(texto, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("\\s+", " ")
			.strip();
	}

	private static @Nullable BigDecimal duasCasas(@Nullable BigDecimal valor) {
		return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
	}

	private static String percentual(@Nullable BigDecimal valor) {
		if (valor == null) {
			return "—";
		}
		var formato = NumberFormat.getNumberInstance(PT_BR);
		formato.setMinimumFractionDigits(2);
		formato.setMaximumFractionDigits(2);
		return formato.format(valor) + "%";
	}

	private static String sinal(BigDecimal valor) {
		var formato = NumberFormat.getNumberInstance(PT_BR);
		formato.setMinimumFractionDigits(2);
		formato.setMaximumFractionDigits(2);
		return (valor.signum() > 0 ? "+" : "") + formato.format(valor);
	}

	private static String inteiro(long valor) {
		return NumberFormat.getIntegerInstance(PT_BR).format(valor);
	}

	private static String mes(YearMonth competencia) {
		return MES.format(competencia).replace(".", "");
	}

	private static String rotuloSeveridade(String severidade) {
		return "CRITICO".equals(severidade) ? "Crítico" : "Atenção";
	}

	/** Entrada que o modelo pode corrigir (volta como erro da ferramenta). */
	static final class EntradaInvalida extends RuntimeException {

		EntradaInvalida(String mensagem) {
			super(mensagem);
		}

	}

}
