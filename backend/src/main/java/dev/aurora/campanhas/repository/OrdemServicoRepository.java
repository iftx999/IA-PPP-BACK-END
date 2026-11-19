package dev.aurora.campanhas.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.campanhas.CampanhaDivergente;
import dev.aurora.campanhas.model.AlvoOrdem;
import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.ContagemMotivo;
import dev.aurora.campanhas.model.FaixaDoLote;
import dev.aurora.campanhas.model.FiltroOrdens;
import dev.aurora.campanhas.model.OrdemServico;
import dev.aurora.campanhas.model.OrigemOrdem;
import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.campanhas.model.Produtividade;
import dev.aurora.campanhas.model.ResultadoOrdem;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.model.TipoEquipe;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Ordens de serviço (ADR 0036, 0044), limitadas pelo RLS ao escopo da unidade do alvo.
 * Numeração única no cliente ({@code contador_os}); um alvo não tem duas OS abertas do
 * mesmo serviço (índices únicos parciais). A conferência pela carga (RN-19) é uma única
 * instrução idempotente.
 */
@Repository
public class OrdemServicoRepository {

	private static final String COM_EQUIPE = "('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA')";

	private static final String PROGRAMAVEIS = "('PLANEJADA', 'PROGRAMADA', 'DEVOLVIDA')";

	private static final String SELECAO = """
			SELECT o.id, o.numero, o.servico_id, s.codigo AS servico_codigo, v.nome AS servico_nome,
			       v.versao AS servico_versao, v.unidade_medida, o.alvo,
			       CASE WHEN o.campanha_id IS NOT NULL THEN 'CAMPANHA' WHEN o.lote_id IS NOT NULL THEN 'LOTE'
			            ELSE 'AVULSA' END AS origem,
			       o.codigo_origem, o.trecho_id, t.codigo AS trecho_codigo, lg.id AS logradouro_id,
			       CASE WHEN lg.tipo IS NULL OR lower(lg.nome) LIKE lower(lg.tipo) || ' %' THEN lg.nome
			            ELSE lg.tipo || ' ' || lg.nome END AS logradouro_nome, ie.numero AS numero_imovel, ie.complemento,
			       o.unidade_id, u.nome AS unidade_nome, o.campanha_id, c.nome AS campanha_nome, o.lote_id,
			       o.situacao_na_criacao, o.prioridade, o.prazo_em, o.instrucoes, o.quantidade_prevista,
			       o.equipe_id, e.nome AS equipe_nome, o.data_programada, o.enviada_em, o.status, o.resultado,
			       o.motivo_id, mo.nome AS motivo_nome, o.observacao, o.quantidade_executada, o.resultado_em,
			       r.nome AS resultado_por, o.movimentada_em, mv.nome AS movimentada_por, o.conferencia,
			       o.confirmada_em,
			       (o.status IN ('PLANEJADA', 'PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA', 'DEVOLVIDA')
			        AND o.prazo_em < :agora) AS atrasada,
			       o.criada_em, cp.nome AS criada_por, o.sequencia_rota,
			       ST_Y(COALESCE(l.geom, ST_LineInterpolatePoint(t.geom, 0.5))) AS latitude,
			       ST_X(COALESCE(l.geom, ST_LineInterpolatePoint(t.geom, 0.5))) AS longitude
			  FROM ordem_servico o
			  JOIN servico s ON s.id = o.servico_id
			  JOIN servico_versao v ON v.id = o.servico_versao_id
			  JOIN unidade u ON u.id = o.unidade_id
			  LEFT JOIN campanha c ON c.id = o.campanha_id
			  LEFT JOIN equipe e ON e.id = o.equipe_id
			  LEFT JOIN motivo mo ON mo.id = o.motivo_id
			  LEFT JOIN usuario r ON r.id = o.resultado_por
			  LEFT JOIN usuario mv ON mv.id = o.movimentada_por
			  LEFT JOIN usuario cp ON cp.id = o.criada_por
			  LEFT JOIN trecho t ON t.id = o.trecho_id
			  LEFT JOIN imovel_endereco ie ON ie.tenant_id = o.tenant_id AND ie.codigo_origem = o.codigo_origem
			  LEFT JOIN logradouro lg ON lg.id = COALESCE(t.logradouro_id, ie.logradouro_id)
			  LEFT JOIN imovel_localizacao l ON l.tenant_id = o.tenant_id AND l.codigo_origem = o.codigo_origem
			""";

	/** Ordem de trabalho: prioridade, prazo, número. */
	private static final String CAMPOS_DA_ORDEM = """
			CASE o.prioridade WHEN 'EMERGENCIAL' THEN 0 WHEN 'ALTA' THEN 1 WHEN 'MEDIA' THEN 2 ELSE 3 END,
			o.prazo_em, o.numero""";

	private static final String ORDEM = " ORDER BY " + CAMPOS_DA_ORDEM;

	private static final String FILTROS = """
			 WHERE (CAST(:campanha AS uuid) IS NULL OR o.campanha_id = CAST(:campanha AS uuid))
			   AND (CAST(:status AS text) IS NULL OR o.status = CAST(:status AS text))
			   AND (CAST(:equipe AS uuid) IS NULL OR o.equipe_id = CAST(:equipe AS uuid))
			   AND (CAST(:conferencia AS text) IS NULL OR o.conferencia = CAST(:conferencia AS text))
			   AND (CAST(:prioridade AS text) IS NULL OR o.prioridade = CAST(:prioridade AS text))
			   AND (NOT :atrasadas OR (o.status IN ('PLANEJADA', 'PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA', 'DEVOLVIDA')
			                           AND o.prazo_em < :agora))
			   AND (CAST(:servico AS uuid) IS NULL OR o.servico_id = CAST(:servico AS uuid))
			   AND (CAST(:unidade AS uuid) IS NULL OR o.unidade_id IN (
			        SELECT x.id FROM unidade x
			         WHERE x.caminho <@ (SELECT caminho FROM unidade WHERE id = CAST(:unidade AS uuid))))
			   AND (CAST(:lote AS uuid) IS NULL OR o.lote_id = CAST(:lote AS uuid))
			   AND (CAST(:origem AS text) IS NULL
			        OR (CAST(:origem AS text) = 'CAMPANHA' AND o.campanha_id IS NOT NULL)
			        OR (CAST(:origem AS text) = 'LOTE' AND o.lote_id IS NOT NULL)
			        OR (CAST(:origem AS text) = 'AVULSA' AND o.campanha_id IS NULL AND o.lote_id IS NULL))
			   AND (CAST(:data AS date) IS NULL OR o.data_programada = CAST(:data AS date))
			   AND (CAST(:busca AS text) IS NULL OR o.codigo_origem = CAST(:busca AS text)
			        OR CAST(o.numero AS text) = CAST(:busca AS text))
			""";

	private static final String IMOVEIS_DO_LOTE = """
			SELECT ie.codigo_origem, ie.unidade_id,
			       EXISTS (SELECT 1 FROM ordem_servico o
			                WHERE o.codigo_origem = ie.codigo_origem AND o.servico_id = :servico
			                  AND o.status IN ('PLANEJADA', 'PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA', 'DEVOLVIDA'))
			              AS com_os_aberta
			  FROM imovel_endereco ie
			 WHERE ie.logradouro_id = :logradouro
			   AND (NOT :comFaixa OR ie.numero_inteiro BETWEEN :inicial AND :final)
			   AND (:lado = 'AMBOS' OR ie.numero_inteiro % 2 = CASE :lado WHEN 'PAR' THEN 0 ELSE 1 END)
			 ORDER BY ie.numero_inteiro % 2, ie.numero_inteiro NULLS LAST, ie.numero, ie.codigo_origem
			""";

	private final JdbcClient jdbc;

	OrdemServicoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// ----- Criação -----

	/**
	 * Uma OS por posição das listas (mesmo tamanho). Imóvel: matrícula preenchida e
	 * trecho nulo; trecho: o contrário.
	 */
	public record NovasOrdens(List<@Nullable String> matriculas, List<@Nullable UUID> trechos, List<UUID> unidades,
			List<UUID> servicos, List<UUID> versoes, List<@Nullable String> situacoes, List<Prioridade> prioridades,
			List<Instant> prazos, List<BigDecimal> quantidades) {

		public int tamanho() {
			return unidades.size();
		}
	}

	/** Origem e autoria comuns às OS criadas de uma vez. */
	public record Origem(@Nullable UUID campanhaId, @Nullable UUID loteId, @Nullable String instrucoes,
			UUID criadaPor) {
	}

	/**
	 * Cria as OS numa instrução, na ordem das listas, numeradas pelo contador do cliente.
	 * O alvo que já tem OS aberta do mesmo serviço fica de fora (também se ganhou uma
	 * nesse meio tempo: índices únicos das abertas).
	 * @return alvos que viraram OS (matrícula ou id do trecho)
	 */
	public List<String> inserir(NovasOrdens novas, Origem origem) {
		if (novas.tamanho() == 0) {
			return List.of();
		}
		return jdbc.sql("""
				WITH dados AS (
				     SELECT d.*
				       FROM unnest(CAST(:matriculas AS text[]), CAST(:trechos AS uuid[]), CAST(:unidades AS uuid[]),
				                   CAST(:servicos AS uuid[]), CAST(:versoes AS uuid[]), CAST(:situacoes AS text[]),
				                   CAST(:prioridades AS text[]), CAST(:prazos AS timestamptz[]),
				                   CAST(:quantidades AS numeric[]))
				            WITH ORDINALITY
				            AS d(matricula, trecho, unidade, servico, versao, situacao, prioridade, prazo, quantidade,
				                 posicao)),
				     livres AS (
				     SELECT d.* FROM dados d
				      WHERE NOT EXISTS (
				            SELECT 1 FROM ordem_servico o
				             WHERE o.servico_id = d.servico
				               AND o.status IN ('PLANEJADA', 'PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA', 'DEVOLVIDA')
				               AND (o.codigo_origem = d.matricula OR o.trecho_id = d.trecho))),
				     reserva AS (
				     INSERT INTO contador_os AS k (tenant_id, ultimo)
				     VALUES (app_tenant(), (SELECT count(*) FROM livres))
				     ON CONFLICT (tenant_id) DO UPDATE SET ultimo = k.ultimo + EXCLUDED.ultimo
				     RETURNING ultimo),
				     numeradas AS (
				     SELECT l.*, (SELECT ultimo FROM reserva) - (SELECT count(*) FROM livres)
				                 + row_number() OVER (ORDER BY l.posicao) AS numero
				       FROM livres l)
				INSERT INTO ordem_servico (id, tenant_id, numero, servico_id, servico_versao_id, alvo, codigo_origem,
				                           trecho_id, unidade_id, campanha_id, lote_id, situacao_na_criacao, prioridade,
				                           prazo_em, quantidade_prevista, instrucoes, conferencia, criada_por)
				SELECT gen_random_uuid(), app_tenant(), n.numero, n.servico, n.versao,
				       CASE WHEN n.trecho IS NULL THEN 'IMOVEL' ELSE 'TRECHO' END, n.matricula, n.trecho, n.unidade,
				       :campanha, :lote, n.situacao, n.prioridade, n.prazo, n.quantidade, :instrucoes,
				       'NAO_SE_APLICA', :criadaPor
				  FROM numeradas n
				 ORDER BY n.posicao
				ON CONFLICT DO NOTHING
				RETURNING COALESCE(codigo_origem, CAST(trecho_id AS text))""")
			.param("matriculas", novas.matriculas().toArray(String[]::new))
			.param("trechos", novas.trechos().toArray(UUID[]::new))
			.param("unidades", novas.unidades().toArray(UUID[]::new))
			.param("servicos", novas.servicos().toArray(UUID[]::new))
			.param("versoes", novas.versoes().toArray(UUID[]::new))
			.param("situacoes", novas.situacoes().toArray(String[]::new))
			.param("prioridades", novas.prioridades().stream().map(Enum::name).toArray(String[]::new))
			.param("prazos", novas.prazos().stream().map(Instant::toString).toArray(String[]::new))
			.param("quantidades", novas.quantidades().stream().map(BigDecimal::toPlainString).toArray(String[]::new))
			.param("campanha", origem.campanhaId())
			.param("lote", origem.loteId())
			.param("instrucoes", origem.instrucoes())
			.param("criadaPor", origem.criadaPor())
			.query(String.class)
			.list();
	}

	/**
	 * Imóvel para OS avulsa: unidade e situação na competência mais recente em que ele
	 * aparece no escopo.
	 */
	public record ImovelAlvo(String matricula, UUID unidadeId, String situacao) {
	}

	public List<ImovelAlvo> imoveis(Collection<String> matriculas) {
		if (matriculas.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT DISTINCT ON (s.codigo_origem) s.codigo_origem, s.unidade_id, s.situacao
				  FROM imovel_situacao_mensal s
				 WHERE s.codigo_origem = ANY (CAST(:matriculas AS text[]))
				 ORDER BY s.codigo_origem, s.competencia DESC""")
			.param("matriculas", matriculas.toArray(String[]::new))
			.query((l, n) -> new ImovelAlvo(l.getString(1), l.getObject(2, UUID.class), l.getString(3)))
			.list();
	}

	/** Trecho para OS de rede: município, logradouro e extensão. */
	public record TrechoAlvo(UUID id, String codigo, UUID unidadeId, UUID logradouroId, BigDecimal extensaoM) {
	}

	public List<TrechoAlvo> trechos(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT id, codigo, unidade_id, logradouro_id, extensao_m FROM trecho
				 WHERE id = ANY (CAST(:ids AS uuid[])) ORDER BY codigo""")
			.param("ids", ids.toArray(UUID[]::new))
			.query((l, n) -> new TrechoAlvo(l.getObject(1, UUID.class), l.getString(2), l.getObject(3, UUID.class),
					l.getObject(4, UUID.class), l.getBigDecimal(5)))
			.list();
	}

	// ----- Lote por logradouro (J5, RN-24) -----

	/** Logradouro do lote: nome e município. */
	public record LogradouroDoLote(UUID id, String nome, UUID unidadeId) {
	}

	public Optional<LogradouroDoLote> logradouro(UUID id) {
		return jdbc.sql("""
				SELECT id, CASE WHEN tipo IS NULL OR lower(nome) LIKE lower(tipo) || ' %' THEN nome
				                ELSE tipo || ' ' || nome END, unidade_id
				  FROM logradouro WHERE id = :id""")
			.param("id", id)
			.query((l, n) -> new LogradouroDoLote(l.getObject(1, UUID.class), l.getString(2),
					l.getObject(3, UUID.class)))
			.optional();
	}

	/** Imóvel do logradouro que entra no lote, com a marca de OS aberta do serviço. */
	public record ImovelDoLote(String matricula, UUID unidadeId, boolean comOsAberta) {
	}

	/**
	 * Imóveis da faixa, ordenados pela numeração (pares, depois ímpares; sem número no
	 * fim).
	 */
	public List<ImovelDoLote> imoveisDoLote(FaixaDoLote faixa, UUID servico) {
		return jdbc.sql(IMOVEIS_DO_LOTE)
			.param("servico", servico)
			.param("logradouro", faixa.logradouroId())
			.param("comFaixa", faixa.comFaixa())
			.param("inicial", faixa.numeroInicial() == null ? 0 : faixa.numeroInicial())
			.param("final", faixa.numeroFinal() == null ? Integer.MAX_VALUE : faixa.numeroFinal())
			.param("lado", faixa.lado().name())
			.query((l, n) -> new ImovelDoLote(l.getString(1), l.getObject(2, UUID.class), l.getBoolean(3)))
			.list();
	}

	public void inserirLote(UUID id, LogradouroDoLote logradouro, UUID servico, UUID versao, FaixaDoLote faixa,
			Prioridade prioridade, UUID autor) {
		jdbc.sql("""
				INSERT INTO lote_os (id, tenant_id, unidade_id, logradouro_id, servico_id, servico_versao_id,
				                     numero_inicial, numero_final, lado, prioridade, criadas, ignoradas, criado_por)
				VALUES (:id, app_tenant(), :unidade, :logradouro, :servico, :versao, :inicial, :final, :lado,
				        :prioridade, 0, 0, :autor)""")
			.param("id", id)
			.param("unidade", logradouro.unidadeId())
			.param("logradouro", logradouro.id())
			.param("servico", servico)
			.param("versao", versao)
			.param("inicial", faixa.numeroInicial())
			.param("final", faixa.numeroFinal())
			.param("lado", faixa.lado().name())
			.param("prioridade", prioridade.name())
			.param("autor", autor)
			.update();
	}

	/** O lote é gravado antes das OS (chave estrangeira); as contagens vêm depois. */
	public void contarLote(UUID id, int criadas, int ignoradas) {
		jdbc.sql("UPDATE lote_os SET criadas = :criadas, ignoradas = :ignoradas WHERE id = :id")
			.param("id", id)
			.param("criadas", criadas)
			.param("ignoradas", ignoradas)
			.update();
	}

	// ----- Consultas -----

	public List<OrdemServico> listar(FiltroOrdens filtro, Instant agora, int pagina, int tamanho) {
		return filtrar(SELECAO + FILTROS + ORDEM + " LIMIT :limite OFFSET :inicio", filtro, agora)
			.param("limite", tamanho)
			.param("inicio", (long) pagina * tamanho)
			.query(OrdemServicoRepository::mapear)
			.list();
	}

	public long contar(FiltroOrdens filtro, Instant agora) {
		return filtrar("SELECT count(*) FROM ordem_servico o" + FILTROS, filtro, agora).query(Long.class).single();
	}

	private JdbcClient.StatementSpec filtrar(String sql, FiltroOrdens filtro, Instant agora) {
		return jdbc.sql(sql)
			.param("campanha", filtro.campanhaId())
			.param("status", filtro.status() == null ? null : filtro.status().name())
			.param("equipe", filtro.equipeId())
			.param("conferencia", filtro.conferencia() == null ? null : filtro.conferencia().name())
			.param("prioridade", filtro.prioridade() == null ? null : filtro.prioridade().name())
			.param("atrasadas", filtro.somenteAtrasadas())
			.param("servico", filtro.servicoId())
			.param("unidade", filtro.unidadeId())
			.param("lote", filtro.loteId())
			.param("origem", filtro.origem() == null ? null : filtro.origem().name())
			.param("data", filtro.dataProgramada() == null ? null : Date.valueOf(filtro.dataProgramada()))
			.param("busca", filtro.busca())
			.param("agora", Timestamp.from(agora));
	}

	/**
	 * OS das equipes informadas: as com a equipe e as encerradas ou devolvidas desde
	 * {@code desde}.
	 */
	public List<OrdemServico> dasEquipes(Collection<UUID> equipes, Instant agora, Instant desde) {
		if (equipes.isEmpty()) {
			return List.of();
		}
		return jdbc
			.sql(SELECAO + " WHERE o.equipe_id IN (:equipes) AND (o.status IN " + COM_EQUIPE
					+ " OR (o.status IN ('ENCERRADA', 'DEVOLVIDA') AND o.movimentada_em >= :desde))"
					+ " ORDER BY o.status NOT IN " + COM_EQUIPE + ", o.data_programada, o.sequencia_rota NULLS LAST, "
					+ CAMPOS_DA_ORDEM + " LIMIT 500")
			.param("equipes", equipes)
			.param("agora", Timestamp.from(agora))
			.param("desde", Timestamp.from(desde))
			.query(OrdemServicoRepository::mapear)
			.list();
	}

	public Optional<OrdemServico> buscar(UUID id, Instant agora) {
		return jdbc.sql(SELECAO + " WHERE o.id = :id")
			.param("id", id)
			.param("agora", Timestamp.from(agora))
			.query(OrdemServicoRepository::mapear)
			.optional();
	}

	/**
	 * OS do pacote do dia: id, versão do serviço, última alteração e o último evento do
	 * campo aplicado (o celular continua a sequência dele).
	 */
	public record NoPacote(UUID id, UUID servicoVersaoId, Instant atualizadaEm, int ultimaSequencia) {
	}

	/**
	 * Pacote do dia da equipe (10.9.2): OS programadas até hoje (inclusive atrasadas), em
	 * execução ou paralisadas.
	 */
	public List<NoPacote> doPacote(UUID equipe, LocalDate hoje) {
		return jdbc.sql("""
				SELECT id, servico_versao_id, atualizado_em, ultima_sequencia FROM ordem_servico
				 WHERE equipe_id = :equipe AND (status IN ('EM_EXECUCAO', 'PARALISADA')
				                                OR (status = 'PROGRAMADA' AND data_programada <= :hoje))
				 LIMIT 500""")
			.param("equipe", equipe)
			.param("hoje", Date.valueOf(hoje))
			.query((l, n) -> new NoPacote(l.getObject("id", UUID.class), l.getObject("servico_versao_id", UUID.class),
					l.getTimestamp("atualizado_em").toInstant(), l.getInt("ultima_sequencia")))
			.list();
	}

	/** Várias OS, na ordem de trabalho. */
	public List<OrdemServico> buscarVarias(Collection<UUID> ids, Instant agora) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.sql(SELECAO + " WHERE o.id IN (:ids)" + ORDEM)
			.param("ids", ids)
			.param("agora", Timestamp.from(agora))
			.query(OrdemServicoRepository::mapear)
			.list();
	}

	// ----- Movimentos -----

	/** Trava a OS para um movimento (encerrar, devolver, cancelar). */
	public Optional<Travada> travar(UUID id) {
		return jdbc.sql("""
				SELECT o.campanha_id, o.equipe_id, o.status, o.servico_id, s.codigo, v.conferido_pela_carga,
				       o.quantidade_prevista
				  FROM ordem_servico o
				  JOIN servico s ON s.id = o.servico_id
				  JOIN servico_versao v ON v.id = o.servico_versao_id
				 WHERE o.id = :id FOR UPDATE OF o""")
			.param("id", id)
			.query((l, n) -> new Travada(l.getObject("campanha_id", UUID.class), l.getObject("equipe_id", UUID.class),
					StatusOrdem.valueOf(l.getString("status")), l.getObject("servico_id", UUID.class),
					l.getString("codigo"), l.getBoolean("conferido_pela_carga"),
					l.getBigDecimal("quantidade_prevista")))
			.optional();
	}

	public record Travada(@Nullable UUID campanhaId, @Nullable UUID equipeId, StatusOrdem status, UUID servicoId,
			String servicoCodigo, boolean conferidoPelaCarga, BigDecimal quantidadePrevista) {
	}

	/**
	 * OS planejadas ou devolvidas da campanha, na ordem de trabalho, até {@code limite}.
	 */
	public List<UUID> paraProgramarDaCampanha(UUID campanha, @Nullable Prioridade prioridade, int limite) {
		return jdbc.sql("""
				SELECT o.id FROM ordem_servico o
				 WHERE o.campanha_id = :campanha AND o.status IN ('PLANEJADA', 'DEVOLVIDA')
				   AND (CAST(:prioridade AS text) IS NULL OR o.prioridade = CAST(:prioridade AS text))
				""" + ORDEM + " LIMIT :limite")
			.param("campanha", campanha)
			.param("prioridade", prioridade == null ? null : prioridade.name())
			.param("limite", limite)
			.query(UUID.class)
			.list();
	}

	/**
	 * Programa (ou reprograma) as OS para a equipe na data: só as que estão em situação
	 * de programar (planejada, programada, devolvida) e cujo alvo fica na unidade onde a
	 * equipe atua ou abaixo e cujo serviço exige habilidade e veículo que a equipe tem
	 * (RN-23). {@code campanha} não nula restringe às OS dela.
	 */
	public Envio programar(List<UUID> ordens, @Nullable UUID campanha, UUID equipe, LocalDate data, UUID usuario) {
		return jdbc
			.sql("""
					WITH escolhidas AS (
					     SELECT o.id FROM ordem_servico o
					      WHERE o.id = ANY (CAST(:ordens AS uuid[])) AND o.status IN %s
					        AND (CAST(:campanha AS uuid) IS NULL OR o.campanha_id = CAST(:campanha AS uuid))),
					     area AS (SELECT caminho FROM unidade WHERE id = (SELECT unidade_id FROM equipe WHERE id = :equipe)),
					     na_area AS (
					     SELECT o.id, o.servico_versao_id FROM ordem_servico o JOIN unidade u ON u.id = o.unidade_id
					      WHERE o.id IN (SELECT id FROM escolhidas) AND u.caminho <@ (SELECT caminho FROM area)),
					     compativeis AS (
					     SELECT a.id FROM na_area a JOIN servico_versao v ON v.id = a.servico_versao_id
					      WHERE (v.habilidade_id IS NULL OR EXISTS (
					                SELECT 1 FROM equipe_habilidade h
					                 WHERE h.equipe_id = :equipe AND h.habilidade_id = v.habilidade_id))
					        AND veiculo_atende(v.tipo_veiculo_id, (SELECT tipo_veiculo_id FROM equipe WHERE id = :equipe))),
					     enviadas AS (
					     UPDATE ordem_servico o
					        SET equipe_id = :equipe, data_programada = :data, status = 'PROGRAMADA', enviada_em = now(),
					            sequencia_rota = NULL, motivo_id = NULL, observacao = NULL, movimentada_em = now(),
					            movimentada_por = :usuario, atualizado_em = now()
					      WHERE o.id IN (SELECT id FROM compativeis)
					     RETURNING o.id)
					SELECT (SELECT count(*) FROM enviadas) AS enviadas,
					       (SELECT count(*) FROM escolhidas) - (SELECT count(*) FROM na_area) AS fora_da_area,
					       (SELECT count(*) FROM na_area) - (SELECT count(*) FROM enviadas) AS incompativeis,
					       cardinality(CAST(:ordens AS uuid[])) - (SELECT count(*) FROM escolhidas) AS ignoradas
					"""
				.formatted(PROGRAMAVEIS))
			.param("ordens", ordens.toArray(UUID[]::new))
			.param("campanha", campanha)
			.param("equipe", equipe)
			.param("data", Date.valueOf(data))
			.param("usuario", usuario)
			.query((l, n) -> new Envio(l.getInt("enviadas"), l.getInt("fora_da_area"), l.getInt("incompativeis"),
					l.getInt("ignoradas")))
			.single();
	}

	/**
	 * Rota publicada (ADR 0046): programa ou ressequencia as OS da lista para a equipe na
	 * data, na ordem dada, com as mesmas regras de {@link #programar} (situação, área e
	 * RN-23); as demais OS da equipe no dia ficam sem sequência.
	 */
	public int programarEmSequencia(UUID equipe, LocalDate data, List<UUID> ordens, UUID usuario) {
		jdbc.sql("""
				UPDATE ordem_servico SET sequencia_rota = NULL, atualizado_em = now()
				 WHERE equipe_id = :equipe AND data_programada = :data AND sequencia_rota IS NOT NULL
				   AND id <> ALL (CAST(:ordens AS uuid[]))""")
			.param("equipe", equipe)
			.param("data", Date.valueOf(data))
			.param("ordens", ordens.toArray(UUID[]::new))
			.update();
		return jdbc
			.sql("""
					WITH pedidas AS (
					     SELECT id, ordem::int AS sequencia FROM unnest(CAST(:ordens AS uuid[])) WITH ORDINALITY AS x(id, ordem)),
					     area AS (SELECT caminho FROM unidade WHERE id = (SELECT unidade_id FROM equipe WHERE id = :equipe)),
					     validas AS (
					     SELECT o.id, p.sequencia, o.status IN %s AS programar
					       FROM ordem_servico o
					       JOIN pedidas p ON p.id = o.id
					       JOIN unidade u ON u.id = o.unidade_id
					       JOIN servico_versao v ON v.id = o.servico_versao_id
					      WHERE (o.status IN %s
					             OR (o.status IN ('EM_EXECUCAO', 'PARALISADA') AND o.equipe_id = :equipe
					                 AND o.data_programada = :data))
					        AND u.caminho <@ (SELECT caminho FROM area)
					        AND (v.habilidade_id IS NULL OR EXISTS (
					                SELECT 1 FROM equipe_habilidade h
					                 WHERE h.equipe_id = :equipe AND h.habilidade_id = v.habilidade_id))
					        AND veiculo_atende(v.tipo_veiculo_id, (SELECT tipo_veiculo_id FROM equipe WHERE id = :equipe)))
					UPDATE ordem_servico o
					   SET sequencia_rota = va.sequencia,
					       equipe_id = :equipe, data_programada = :data,
					       status = CASE WHEN va.programar THEN 'PROGRAMADA' ELSE o.status END,
					       enviada_em = CASE WHEN va.programar THEN now() ELSE o.enviada_em END,
					       motivo_id = CASE WHEN va.programar THEN NULL ELSE o.motivo_id END,
					       observacao = CASE WHEN va.programar THEN NULL ELSE o.observacao END,
					       movimentada_em = CASE WHEN va.programar THEN now() ELSE o.movimentada_em END,
					       movimentada_por = CASE WHEN va.programar THEN :usuario ELSE o.movimentada_por END,
					       atualizado_em = now()
					  FROM validas va
					 WHERE o.id = va.id"""
				.formatted(PROGRAMAVEIS, PROGRAMAVEIS))
			.param("ordens", ordens.toArray(UUID[]::new))
			.param("equipe", equipe)
			.param("data", Date.valueOf(data))
			.param("usuario", usuario)
			.update();
	}

	public record Envio(int enviadas, int foraDaArea, int incompativeis, int ignoradas) {
	}

	/** Devolver (volta para a programação) ou cancelar, com motivo do catálogo. */
	public void movimentar(UUID id, StatusOrdem status, UUID motivo, @Nullable String observacao, UUID usuario) {
		jdbc.sql("""
				UPDATE ordem_servico
				   SET status = :status, motivo_id = :motivo, observacao = :observacao, movimentada_em = now(),
				       movimentada_por = :usuario, atualizado_em = now()
				 WHERE id = :id""")
			.param("id", id)
			.param("status", status.name())
			.param("motivo", motivo)
			.param("observacao", observacao)
			.param("usuario", usuario)
			.update();
	}

	/** Encerra a OS com o resultado (doc 10, 10.6). */
	public void encerrar(UUID id, ResultadoOrdem resultado, @Nullable UUID motivo, @Nullable String observacao,
			BigDecimal quantidade, UUID usuario, Conferencia conferencia) {
		jdbc.sql("""
				UPDATE ordem_servico
				   SET status = 'ENCERRADA', resultado = :resultado, motivo_id = :motivo, observacao = :observacao,
				       quantidade_executada = :quantidade, resultado_em = now(), resultado_por = :usuario,
				       movimentada_em = now(), movimentada_por = :usuario, conferencia = :conferencia,
				       confirmada_em = NULL, atualizado_em = now()
				 WHERE id = :id""")
			.param("id", id)
			.param("resultado", resultado.name())
			.param("motivo", motivo)
			.param("observacao", observacao)
			.param("quantidade", quantidade)
			.param("usuario", usuario)
			.param("conferencia", conferencia.name())
			.update();
	}

	/**
	 * Cancela as OS da campanha que ainda não começaram (encerrar ou cancelar a
	 * campanha); as em execução ou paralisadas seguem com a equipe (vale o campo).
	 */
	public int cancelarAbertas(UUID campanha, UUID usuario) {
		return jdbc.sql("""
				UPDATE ordem_servico
				   SET status = 'CANCELADA', observacao = 'Campanha finalizada', movimentada_em = now(),
				       movimentada_por = :usuario, atualizado_em = now()
				 WHERE campanha_id = :campanha AND status IN %s""".formatted(PROGRAMAVEIS))
			.param("campanha", campanha)
			.param("usuario", usuario)
			.update();
	}

	// ----- Conferência e painel -----

	/**
	 * RN-19: OS encerrada como executada de serviço conferido pela carga é confirmada
	 * pela primeira competência, a partir do mês da execução, com o imóvel ligado; fica
	 * divergente se a competência seguinte à da execução já foi carregada e ele continua
	 * sem ligação; senão segue pendente. Idempotente (também desfaz uma divergência).
	 * @param fuso fuso do mês da execução (ex.: America/Sao_Paulo)
	 * @return quantas OS mudaram de conferência
	 */
	public int conferir(String fuso) {
		return jdbc
			.sql("""
					WITH ultima AS (SELECT max(competencia) AS competencia FROM imovel_situacao_mensal),
					     alvo AS (
					     SELECT o.id, o.conferencia, o.confirmada_em,
					            date_trunc('month', o.resultado_em AT TIME ZONE :fuso)::date AS mes,
					            (SELECT min(s.competencia) FROM imovel_situacao_mensal s
					              WHERE s.tenant_id = o.tenant_id AND s.codigo_origem = o.codigo_origem
					                AND s.situacao = 'LIGADO'
					                AND s.competencia >= date_trunc('month', o.resultado_em AT TIME ZONE :fuso)::date)
					              AS ligado_em
					       FROM ordem_servico o
					      WHERE o.status = 'ENCERRADA' AND o.resultado = 'EXECUTADA'
					        AND o.conferencia IN ('PENDENTE', 'DIVERGENTE')),
					     apurado AS (
					     SELECT a.id, a.ligado_em,
					            CASE WHEN a.ligado_em IS NOT NULL THEN 'CONFIRMADA'
					                 WHEN (SELECT competencia FROM ultima) >= (a.mes + interval '1 month')::date THEN 'DIVERGENTE'
					                 ELSE 'PENDENTE' END AS conferencia
					       FROM alvo a)
					UPDATE ordem_servico o
					   SET conferencia = p.conferencia, confirmada_em = p.ligado_em, atualizado_em = now()
					  FROM apurado p
					 WHERE o.id = p.id AND o.conferencia IS DISTINCT FROM p.conferencia""")
			.param("fuso", fuso)
			.update();
	}

	public List<Produtividade> produtividade(UUID campanha, Instant agora) {
		return jdbc.sql("""
				SELECT e.id, e.nome, e.tipo,
				       count(*) FILTER (WHERE o.status IN ('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA')) AS em_campo,
				       count(*) FILTER (WHERE o.resultado = 'EXECUTADA') AS executadas,
				       count(*) FILTER (WHERE o.conferencia = 'CONFIRMADA') AS confirmadas,
				       count(*) FILTER (WHERE o.resultado = 'NAO_EXECUTADA') AS nao_executadas,
				       count(*) FILTER (WHERE o.status = 'DEVOLVIDA') AS devolvidas,
				       count(*) FILTER (WHERE o.status IN ('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA')
				                          AND o.prazo_em < :agora) AS atrasadas
				  FROM ordem_servico o
				  JOIN equipe e ON e.id = o.equipe_id
				 WHERE o.campanha_id = :campanha AND o.status <> 'CANCELADA'
				 GROUP BY e.id, e.nome, e.tipo
				 ORDER BY count(*) FILTER (WHERE o.resultado = 'EXECUTADA') DESC, e.nome""")
			.param("campanha", campanha)
			.param("agora", Timestamp.from(agora))
			.query((l, n) -> new Produtividade(l.getObject("id", UUID.class), l.getString("nome"),
					TipoEquipe.valueOf(l.getString("tipo")), l.getLong("em_campo"), l.getLong("executadas"),
					l.getLong("confirmadas"), l.getLong("nao_executadas"), l.getLong("devolvidas"),
					l.getLong("atrasadas")))
			.list();
	}

	/** Motivos de não execução e de devolução das OS da campanha. */
	public List<ContagemMotivo> motivos(UUID campanha) {
		return jdbc.sql("""
				SELECT m.tipo, m.codigo, m.nome, count(*) AS quantidade
				  FROM ordem_servico o JOIN motivo m ON m.id = o.motivo_id
				 WHERE o.campanha_id = :campanha
				   AND (o.resultado = 'NAO_EXECUTADA' OR o.status = 'DEVOLVIDA')
				 GROUP BY m.tipo, m.codigo, m.nome ORDER BY count(*) DESC, m.nome""")
			.param("campanha", campanha)
			.query((l, n) -> new ContagemMotivo(l.getString("tipo"), l.getString("codigo"), l.getString("nome"),
					l.getLong("quantidade")))
			.list();
	}

	public List<CampanhaDivergente> campanhasComDivergencia() {
		return jdbc.sql("""
				SELECT c.id, c.nome, c.unidade_id, count(*) AS divergentes
				  FROM ordem_servico o JOIN campanha c ON c.id = o.campanha_id
				 WHERE o.conferencia = 'DIVERGENTE'
				 GROUP BY c.id, c.nome, c.unidade_id
				 ORDER BY count(*) DESC, c.nome""")
			.query((l, n) -> new CampanhaDivergente(l.getObject("id", UUID.class), l.getString("nome"),
					l.getObject("unidade_id", UUID.class), l.getLong("divergentes")))
			.list();
	}

	private static @Nullable Instant instante(ResultSet l, String coluna) throws SQLException {
		var t = l.getTimestamp(coluna);
		return t == null ? null : t.toInstant();
	}

	private static OrdemServico mapear(ResultSet l, int n) throws SQLException {
		var programada = l.getDate("data_programada");
		var confirmada = l.getDate("confirmada_em");
		var resultado = l.getString("resultado");
		var latitude = l.getDouble("latitude");
		var semPonto = l.wasNull();
		var longitude = l.getDouble("longitude");
		return new OrdemServico(l.getObject("id", UUID.class), l.getLong("numero"),
				l.getObject("servico_id", UUID.class), l.getString("servico_codigo"), l.getString("servico_nome"),
				l.getInt("servico_versao"), l.getString("unidade_medida"), AlvoOrdem.valueOf(l.getString("alvo")),
				OrigemOrdem.valueOf(l.getString("origem")), l.getString("codigo_origem"),
				l.getObject("trecho_id", UUID.class), l.getString("trecho_codigo"),
				l.getObject("logradouro_id", UUID.class), emBranco(l.getString("logradouro_nome")),
				l.getString("numero_imovel"), l.getString("complemento"), l.getObject("unidade_id", UUID.class),
				l.getString("unidade_nome"), l.getObject("campanha_id", UUID.class), l.getString("campanha_nome"),
				l.getObject("lote_id", UUID.class), l.getString("situacao_na_criacao"),
				Prioridade.valueOf(l.getString("prioridade")), l.getTimestamp("prazo_em").toInstant(),
				l.getString("instrucoes"), l.getBigDecimal("quantidade_prevista"), l.getObject("equipe_id", UUID.class),
				l.getString("equipe_nome"), programada == null ? null : programada.toLocalDate(),
				instante(l, "enviada_em"), StatusOrdem.valueOf(l.getString("status")),
				resultado == null ? null : ResultadoOrdem.valueOf(resultado), l.getObject("motivo_id", UUID.class),
				l.getString("motivo_nome"), l.getString("observacao"), l.getBigDecimal("quantidade_executada"),
				instante(l, "resultado_em"), l.getString("resultado_por"), instante(l, "movimentada_em"),
				l.getString("movimentada_por"), Conferencia.valueOf(l.getString("conferencia")),
				confirmada == null ? null : YearMonth.from(confirmada.toLocalDate()), l.getBoolean("atrasada"),
				l.getTimestamp("criada_em").toInstant(), l.getString("criada_por"), semPonto ? null : latitude,
				semPonto ? null : longitude, l.getObject("sequencia_rota", Integer.class));
	}

	private static @Nullable String emBranco(@Nullable String texto) {
		return texto == null || texto.isBlank() ? null : texto;
	}

}
