package dev.aurora.roteirizacao.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.roteirizacao.model.Calculo;
import dev.aurora.roteirizacao.model.Candidatas;
import dev.aurora.roteirizacao.model.Compatibilidade;
import dev.aurora.roteirizacao.model.Coordenada;
import dev.aurora.roteirizacao.model.EquipeParaRota;
import dev.aurora.roteirizacao.model.MotivoPendente;
import dev.aurora.roteirizacao.model.OrdemParaRota;
import dev.aurora.roteirizacao.model.OrigemRoteirizacao;
import dev.aurora.roteirizacao.model.PerfilRota;
import dev.aurora.roteirizacao.model.Roteirizacao;
import dev.aurora.roteirizacao.model.Roteirizacao.ParadaCalculada;
import dev.aurora.roteirizacao.model.Roteirizacao.Pendente;
import dev.aurora.roteirizacao.model.Roteirizacao.RotaCalculada;
import dev.aurora.roteirizacao.model.SituacaoRoteirizacao;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Leituras das OS e equipes para o cálculo e a gravação das roteirizações (ADR 0046).
 * Tudo limitado pelo RLS ao escopo de quem pediu.
 */
@Repository
public class RoteirizacaoRepository {

	private static final String CABECALHO = """
			SELECT r.id, r.unidade_id, u.nome AS unidade_nome, r.data, r.origem, r.situacao, r.calculo, r.erro,
			       r.anterior_id, r.criada_em, c.nome AS criada_por, r.concluida_em, r.publicada_em
			  FROM roteirizacao r
			  JOIN unidade u ON u.id = r.unidade_id
			  LEFT JOIN usuario c ON c.id = r.criada_por
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	RoteirizacaoRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	// ----- Leituras para o cálculo -----

	/**
	 * OS pedidas, com o local de chegada e de saída e a duração (tempo padrão ×
	 * quantidade prevista quando o serviço é medido por unidade).
	 */
	public List<OrdemParaRota> ordens(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT o.id, o.numero, v.nome AS servico_nome, o.status, o.prioridade, o.prazo_em, o.equipe_id,
				       o.data_programada,
				       ST_Y(COALESCE(l.geom, ST_StartPoint(t.geom))) AS lat_entrada,
				       ST_X(COALESCE(l.geom, ST_StartPoint(t.geom))) AS lon_entrada,
				       ST_Y(COALESCE(l.geom, ST_EndPoint(t.geom))) AS lat_saida,
				       ST_X(COALESCE(l.geom, ST_EndPoint(t.geom))) AS lon_saida,
				       round(60 * v.tempo_padrao_min
				             * CASE WHEN v.tempo_por_unidade THEN COALESCE(o.quantidade_prevista, 1) ELSE 1 END)
				         AS duracao_seg
				  FROM ordem_servico o
				  JOIN servico_versao v ON v.id = o.servico_versao_id
				  LEFT JOIN trecho t ON t.id = o.trecho_id
				  LEFT JOIN imovel_localizacao l
				         ON o.alvo = 'IMOVEL' AND l.tenant_id = o.tenant_id AND l.codigo_origem = o.codigo_origem
				 WHERE o.id = ANY (CAST(:ids AS uuid[]))
				 ORDER BY o.numero""")
			.param("ids", ids.toArray(UUID[]::new))
			.query((l, n) -> new OrdemParaRota(l.getObject("id", UUID.class), l.getLong("numero"),
					l.getString("servico_nome"), l.getString("status"), l.getString("prioridade"),
					instante(l, "prazo_em"), coordenada(l, "lat_entrada", "lon_entrada"),
					coordenada(l, "lat_saida", "lon_saida"),
					(int) Math.min(Integer.MAX_VALUE, l.getLong("duracao_seg")), l.getObject("equipe_id", UUID.class),
					data(l, "data_programada")))
			.list();
	}

	/** OS da equipe na data ainda abertas (reotimização e "Iniciar jornada"). */
	public List<UUID> ordensDoDia(UUID equipe, LocalDate data) {
		return jdbc.sql("""
				SELECT id FROM ordem_servico
				 WHERE equipe_id = :equipe AND data_programada = :data
				   AND status IN ('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA')
				 ORDER BY status = 'EM_EXECUCAO' DESC, sequencia_rota NULLS LAST, numero""")
			.param("equipe", equipe)
			.param("data", Date.valueOf(data))
			.query(UUID.class)
			.list();
	}

	/** Equipes pedidas, com o perfil do veículo e a última posição na jornada aberta. */
	public List<EquipeParaRota> equipes(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT e.id, e.nome, e.ativa, ST_Y(e.base) AS lat_base, ST_X(e.base) AS lon_base, e.jornada_inicio,
				       e.jornada_fim, e.pausa_min, tv.perfil_rota,
				       ST_Y(p.geom) AS lat_posicao, ST_X(p.geom) AS lon_posicao
				  FROM equipe e
				  LEFT JOIN tipo_veiculo tv ON tv.id = e.tipo_veiculo_id
				  LEFT JOIN LATERAL (
				       SELECT pe.geom FROM posicao_equipe pe JOIN jornada j ON j.id = pe.jornada_id AND j.fim IS NULL
				        WHERE pe.equipe_id = e.id ORDER BY pe.momento DESC LIMIT 1) p ON true
				 WHERE e.id = ANY (CAST(:ids AS uuid[]))
				 ORDER BY e.nome""")
			.param("ids", ids.toArray(UUID[]::new))
			.query((l, n) -> new EquipeParaRota(l.getObject("id", UUID.class), l.getString("nome"),
					l.getBoolean("ativa"), coordenada(l, "lat_base", "lon_base"),
					l.getObject("jornada_inicio", LocalTime.class), l.getObject("jornada_fim", LocalTime.class),
					l.getInt("pausa_min"), PerfilRota.de(l.getString("perfil_rota")),
					coordenada(l, "lat_posicao", "lon_posicao")))
			.list();
	}

	/** RN-23 de cada par equipe × OS: área, habilidade e veículo. */
	public List<Compatibilidade> compatibilidades(Collection<UUID> equipes, Collection<UUID> ordens) {
		if (equipes.isEmpty() || ordens.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT e.id AS equipe_id, o.id AS ordem_id, u.caminho <@ ue.caminho AS area,
				       (v.habilidade_id IS NULL OR EXISTS (
				            SELECT 1 FROM equipe_habilidade h
				             WHERE h.equipe_id = e.id AND h.habilidade_id = v.habilidade_id)) AS habilidade,
				       veiculo_atende(v.tipo_veiculo_id, e.tipo_veiculo_id) AS veiculo
				  FROM equipe e
				  JOIN unidade ue ON ue.id = e.unidade_id
				  CROSS JOIN ordem_servico o
				  JOIN unidade u ON u.id = o.unidade_id
				  JOIN servico_versao v ON v.id = o.servico_versao_id
				 WHERE e.id = ANY (CAST(:equipes AS uuid[])) AND o.id = ANY (CAST(:ordens AS uuid[]))""")
			.param("equipes", equipes.toArray(UUID[]::new))
			.param("ordens", ordens.toArray(UUID[]::new))
			.query((l, n) -> new Compatibilidade(l.getObject("equipe_id", UUID.class),
					l.getObject("ordem_id", UUID.class), l.getBoolean("area"), l.getBoolean("habilidade"),
					l.getBoolean("veiculo")))
			.list();
	}

	/**
	 * OS da unidade (e de baixo) que dá para roteirizar no dia: planejadas, devolvidas e
	 * as programadas para a data; por prioridade e prazo. Lê uma a mais que o limite para
	 * saber se truncou.
	 */
	public List<Candidatas.OrdemCandidata> ordensCandidatas(UUID unidade, LocalDate data, int limite) {
		return jdbc
			.sql("""
					SELECT o.id, o.numero, v.nome AS servico_nome, o.prioridade, o.status, o.prazo_em,
					       o.prazo_em < now() AS atrasada, o.equipe_id, e.nome AS equipe_nome, o.data_programada,
					       (l.geom IS NOT NULL OR t.geom IS NOT NULL) AS tem_local
					  FROM ordem_servico o
					  JOIN unidade u ON u.id = o.unidade_id
					  JOIN servico_versao v ON v.id = o.servico_versao_id
					  LEFT JOIN equipe e ON e.id = o.equipe_id
					  LEFT JOIN trecho t ON t.id = o.trecho_id
					  LEFT JOIN imovel_localizacao l
					         ON o.alvo = 'IMOVEL' AND l.tenant_id = o.tenant_id AND l.codigo_origem = o.codigo_origem
					 WHERE u.caminho <@ (SELECT caminho FROM unidade WHERE id = :unidade)
					   AND (o.status IN ('PLANEJADA', 'DEVOLVIDA') OR (o.status = 'PROGRAMADA' AND o.data_programada = :data))
					 ORDER BY CASE o.prioridade WHEN 'EMERGENCIAL' THEN 0 WHEN 'ALTA' THEN 1 WHEN 'MEDIA' THEN 2 ELSE 3 END,
					          o.prazo_em, o.numero
					 LIMIT :limite""")
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.param("limite", limite + 1)
			.query((l, n) -> new Candidatas.OrdemCandidata(l.getObject("id", UUID.class), l.getLong("numero"),
					l.getString("servico_nome"), l.getString("prioridade"), l.getString("status"),
					instante(l, "prazo_em"), l.getBoolean("atrasada"), l.getObject("equipe_id", UUID.class),
					l.getString("equipe_nome"), data(l, "data_programada"), l.getBoolean("tem_local")))
			.list();
	}

	/** Equipes ativas que atuam na unidade: nela, acima (a cobrem) ou abaixo. */
	public List<Candidatas.EquipeCandidata> equipesCandidatas(UUID unidade) {
		return jdbc.sql("""
				SELECT e.id, e.nome, e.base IS NOT NULL AS tem_base, tv.nome AS veiculo_nome, e.jornada_inicio,
				       e.jornada_fim, (SELECT count(*) FROM equipe_habilidade h WHERE h.equipe_id = e.id) AS habilidades
				  FROM equipe e
				  JOIN unidade ue ON ue.id = e.unidade_id
				  LEFT JOIN tipo_veiculo tv ON tv.id = e.tipo_veiculo_id
				  CROSS JOIN (SELECT caminho FROM unidade WHERE id = :unidade) alvo
				 WHERE e.ativa AND (alvo.caminho <@ ue.caminho OR ue.caminho <@ alvo.caminho)
				 ORDER BY e.nome""")
			.param("unidade", unidade)
			.query((l, n) -> new Candidatas.EquipeCandidata(l.getObject("id", UUID.class), l.getString("nome"),
					l.getBoolean("tem_base"), l.getString("veiculo_nome"),
					l.getObject("jornada_inicio", LocalTime.class), l.getObject("jornada_fim", LocalTime.class),
					l.getLong("habilidades")))
			.list();
	}

	// ----- Roteirização -----

	/**
	 * Registra o pedido; falha (violação do índice único) se já há um cálculo em
	 * andamento para a unidade e o dia.
	 */
	public void criar(UUID id, UUID unidade, LocalDate data, OrigemRoteirizacao origem, @Nullable UUID anterior,
			@Nullable UUID usuario) {
		// Cálculo esquecido (a API reiniciou no meio): libera o dia e a unidade para um
		// novo.
		jdbc.sql("""
				UPDATE roteirizacao SET situacao = 'FALHOU', erro = 'Cálculo interrompido.', concluida_em = now()
				 WHERE unidade_id = :unidade AND data = :data AND situacao = 'CALCULANDO'
				   AND criada_em < now() - interval '15 minutes'""")
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.update();
		jdbc.sql("""
				INSERT INTO roteirizacao (id, tenant_id, unidade_id, data, origem, situacao, anterior_id, criada_por)
				VALUES (:id, app_tenant(), :unidade, :data, :origem, 'CALCULANDO', :anterior, :usuario)""")
			.param("id", id)
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.param("origem", origem.name())
			.param("anterior", anterior)
			.param("usuario", usuario)
			.update();
	}

	public void concluir(UUID id, Calculo calculo, String pontuacao, List<RotaCalculada> rotas,
			List<Pendente> pendentes) {
		for (var rota : rotas) {
			var rotaId = UUID.randomUUID();
			var partida = rota.partida();
			jdbc.sql("""
					INSERT INTO rota (id, tenant_id, roteirizacao_id, equipe_id, partida, inicio, fim_previsto, km,
					                  minutos_deslocamento, minutos_servico, geom)
					VALUES (:id, app_tenant(), :roteirizacao, :equipe,
					        CASE WHEN CAST(:lat AS double precision) IS NULL THEN NULL
					             ELSE ST_SetSRID(ST_MakePoint(:lon, :lat), 4674) END,
					        :inicio, :fim, :km, :deslocamento, :servico,
					        CASE WHEN CAST(:tracado AS text) IS NULL THEN NULL
					             ELSE ST_Multi(ST_GeomFromText(:tracado, 4674)) END)""")
				.param("id", rotaId)
				.param("roteirizacao", id)
				.param("equipe", rota.equipeId())
				.param("lat", partida == null ? null : partida.latitude(), java.sql.Types.DOUBLE)
				.param("lon", partida == null ? null : partida.longitude(), java.sql.Types.DOUBLE)
				.param("inicio", Timestamp.from(rota.inicio()))
				.param("fim", Timestamp.from(rota.fimPrevisto()))
				.param("km", BigDecimal.valueOf(rota.km()).setScale(2, java.math.RoundingMode.HALF_UP))
				.param("deslocamento", rota.minutosDeslocamento())
				.param("servico", rota.minutosServico())
				.param("tracado", wkt(rota.tracado()))
				.update();
			if (rota.paradas().isEmpty()) {
				continue;
			}
			var p = rota.paradas();
			jdbc.sql("""
					INSERT INTO rota_parada (tenant_id, rota_id, ordem_servico_id, ordem, chegada_prevista,
					                         saida_prevista, km_ate, minutos_ate, fixada, atrasa)
					SELECT app_tenant(), :rota, x.os, x.ordem, x.chegada, x.saida, x.km, x.minutos, x.fixada, x.atrasa
					  FROM unnest(CAST(:os AS uuid[]), CAST(:ordem AS int[]), CAST(:chegada AS timestamptz[]),
					              CAST(:saida AS timestamptz[]), CAST(:km AS numeric[]), CAST(:minutos AS int[]),
					              CAST(:fixada AS boolean[]), CAST(:atrasa AS boolean[]))
					       AS x(os, ordem, chegada, saida, km, minutos, fixada, atrasa)""")
				.param("rota", rotaId)
				.param("os", p.stream().map(ParadaCalculada::ordemServicoId).toArray(UUID[]::new))
				.param("ordem", p.stream().map(ParadaCalculada::ordem).toArray(Integer[]::new))
				.param("chegada", p.stream().map(x -> x.chegadaPrevista().toString()).toArray(String[]::new))
				.param("saida", p.stream().map(x -> x.saidaPrevista().toString()).toArray(String[]::new))
				.param("km",
						p.stream()
							.map(x -> String.format(java.util.Locale.ROOT, "%.2f", x.kmAte()))
							.toArray(String[]::new))
				.param("minutos", p.stream().map(ParadaCalculada::minutosAte).toArray(Integer[]::new))
				.param("fixada", p.stream().map(ParadaCalculada::fixada).toArray(Boolean[]::new))
				.param("atrasa", p.stream().map(ParadaCalculada::atrasa).toArray(Boolean[]::new))
				.update();
		}
		if (!pendentes.isEmpty()) {
			jdbc.sql("""
					INSERT INTO roteirizacao_pendente (tenant_id, roteirizacao_id, ordem_servico_id, motivo)
					SELECT app_tenant(), :roteirizacao, x.os, x.motivo
					  FROM unnest(CAST(:os AS uuid[]), CAST(:motivo AS text[])) AS x(os, motivo)""")
				.param("roteirizacao", id)
				.param("os", pendentes.stream().map(Pendente::ordemServicoId).toArray(UUID[]::new))
				.param("motivo", pendentes.stream().map(x -> x.motivo().name()).toArray(String[]::new))
				.update();
		}
		jdbc.sql("""
				UPDATE roteirizacao SET situacao = 'PRONTA', calculo = :calculo, pontuacao = :pontuacao,
				       concluida_em = now()
				 WHERE id = :id AND situacao = 'CALCULANDO'""")
			.param("id", id)
			.param("calculo", calculo.name())
			.param("pontuacao", pontuacao.length() > 200 ? pontuacao.substring(0, 200) : pontuacao)
			.update();
	}

	public void falhar(UUID id, String erro) {
		jdbc.sql("""
				UPDATE roteirizacao SET situacao = 'FALHOU', erro = :erro, concluida_em = now()
				 WHERE id = :id AND situacao = 'CALCULANDO'""")
			.param("id", id)
			.param("erro", erro.length() > 500 ? erro.substring(0, 500) : erro)
			.update();
	}

	/** Trava a roteirização para publicar ou descartar sem corrida. */
	public Optional<SituacaoRoteirizacao> travar(UUID id) {
		return jdbc.sql("SELECT situacao FROM roteirizacao WHERE id = :id FOR UPDATE")
			.param("id", id)
			.query(String.class)
			.optional()
			.map(SituacaoRoteirizacao::valueOf);
	}

	public void publicar(UUID id, UUID usuario) {
		jdbc.sql("""
				UPDATE roteirizacao SET situacao = 'PUBLICADA', publicada_em = now(), publicada_por = :usuario
				 WHERE id = :id""").param("id", id).param("usuario", usuario).update();
	}

	/** A publicada antes, do mesmo dia e unidade, deixa de valer. */
	public void substituirPublicadas(UUID unidade, LocalDate data, UUID exceto) {
		jdbc.sql("""
				UPDATE roteirizacao SET situacao = 'DESCARTADA'
				 WHERE unidade_id = :unidade AND data = :data AND situacao = 'PUBLICADA' AND id <> :exceto""")
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.param("exceto", exceto)
			.update();
	}

	public void descartar(UUID id) {
		jdbc.sql("UPDATE roteirizacao SET situacao = 'DESCARTADA' WHERE id = :id").param("id", id).update();
	}

	public Optional<Roteirizacao> buscar(UUID id) {
		var cabecalho = jdbc.sql(CABECALHO + " WHERE r.id = :id")
			.param("id", id)
			.query(RoteirizacaoRepository::mapear)
			.optional();
		return cabecalho.map(r -> r.com(rotas(id), pendentes(id)));
	}

	/** Roteirizações do dia, da mais nova para a mais antiga (sem rotas). */
	public List<Roteirizacao> listar(LocalDate data) {
		return jdbc.sql(CABECALHO + " WHERE r.data = :data ORDER BY r.criada_em DESC LIMIT 50")
			.param("data", Date.valueOf(data))
			.query(RoteirizacaoRepository::mapear)
			.list();
	}

	private List<RotaCalculada> rotas(UUID roteirizacao) {
		var paradas = jdbc
			.sql("""
					SELECT p.rota_id, p.ordem_servico_id, o.numero, v.nome AS servico_nome, o.prioridade, p.ordem,
					       p.chegada_prevista, p.saida_prevista, p.km_ate, p.minutos_ate, p.fixada, p.atrasa,
					       ST_Y(COALESCE(l.geom, ST_StartPoint(t.geom))) AS lat, ST_X(COALESCE(l.geom, ST_StartPoint(t.geom))) AS lon
					  FROM rota_parada p
					  JOIN rota r ON r.id = p.rota_id
					  JOIN ordem_servico o ON o.id = p.ordem_servico_id
					  JOIN servico_versao v ON v.id = o.servico_versao_id
					  LEFT JOIN trecho t ON t.id = o.trecho_id
					  LEFT JOIN imovel_localizacao l
					         ON o.alvo = 'IMOVEL' AND l.tenant_id = o.tenant_id AND l.codigo_origem = o.codigo_origem
					 WHERE r.roteirizacao_id = :id
					 ORDER BY p.rota_id, p.ordem""")
			.param("id", roteirizacao)
			.query((l, n) -> java.util.Map.entry(l.getObject("rota_id", UUID.class),
					new ParadaCalculada(l.getObject("ordem_servico_id", UUID.class), l.getLong("numero"),
							l.getString("servico_nome"), l.getString("prioridade"), l.getInt("ordem"),
							instanteObrigatorio(l, "chegada_prevista"), instanteObrigatorio(l, "saida_prevista"),
							l.getDouble("km_ate"), l.getInt("minutos_ate"), l.getBoolean("fixada"),
							l.getBoolean("atrasa"), coordenada(l, "lat", "lon"))))
			.list();
		return jdbc.sql("""
				SELECT r.id, r.equipe_id, e.nome AS equipe_nome, ST_Y(r.partida) AS lat, ST_X(r.partida) AS lon,
				       r.inicio, r.fim_previsto, r.km, r.minutos_deslocamento, r.minutos_servico,
				       ST_AsGeoJSON(r.geom, 6) AS tracado
				  FROM rota r JOIN equipe e ON e.id = r.equipe_id
				 WHERE r.roteirizacao_id = :id
				 ORDER BY e.nome""").param("id", roteirizacao).query((l, n) -> {
			var rotaId = l.getObject("id", UUID.class);
			return new RotaCalculada(l.getObject("equipe_id", UUID.class), l.getString("equipe_nome"),
					coordenada(l, "lat", "lon"), instanteObrigatorio(l, "inicio"),
					instanteObrigatorio(l, "fim_previsto"), l.getDouble("km"), l.getInt("minutos_deslocamento"),
					l.getInt("minutos_servico"), tracado(l.getString("tracado")),
					paradas.stream()
						.filter(e -> e.getKey().equals(rotaId))
						.map(java.util.Map.Entry::getValue)
						.toList());
		}).list();
	}

	private List<Pendente> pendentes(UUID roteirizacao) {
		return jdbc.sql("""
				SELECT p.ordem_servico_id, o.numero, v.nome AS servico_nome, o.prioridade, p.motivo
				  FROM roteirizacao_pendente p
				  JOIN ordem_servico o ON o.id = p.ordem_servico_id
				  JOIN servico_versao v ON v.id = o.servico_versao_id
				 WHERE p.roteirizacao_id = :id
				 ORDER BY CASE o.prioridade WHEN 'EMERGENCIAL' THEN 0 WHEN 'ALTA' THEN 1 WHEN 'MEDIA' THEN 2 ELSE 3 END,
				          o.numero""")
			.param("id", roteirizacao)
			.query((l, n) -> new Pendente(l.getObject("ordem_servico_id", UUID.class), l.getLong("numero"),
					l.getString("servico_nome"), l.getString("prioridade"),
					MotivoPendente.valueOf(l.getString("motivo"))))
			.list();
	}

	private List<List<Coordenada>> tracado(@Nullable String geojson) {
		if (geojson == null) {
			return List.of();
		}
		var linhas = new ArrayList<List<Coordenada>>();
		for (var linha : json.readTree(geojson).get("coordinates")) {
			var pontos = new ArrayList<Coordenada>();
			for (var ponto : linha) {
				pontos.add(new Coordenada(ponto.get(1).asDouble(), ponto.get(0).asDouble()));
			}
			linhas.add(pontos);
		}
		return linhas;
	}

	/** MULTILINESTRING em WKT; nulo sem traçado. */
	static @Nullable String wkt(List<List<Coordenada>> linhas) {
		var validas = linhas.stream().filter(l -> l.size() >= 2).toList();
		if (validas.isEmpty()) {
			return null;
		}
		var texto = new StringBuilder("MULTILINESTRING(");
		for (int i = 0; i < validas.size(); i++) {
			texto.append(i == 0 ? "(" : ",(");
			var linha = validas.get(i);
			for (int j = 0; j < linha.size(); j++) {
				var p = linha.get(j);
				texto.append(j == 0 ? "" : ",")
					.append(String.format(java.util.Locale.ROOT, "%.6f %.6f", p.longitude(), p.latitude()));
			}
			texto.append(')');
		}
		return texto.append(')').toString();
	}

	private static Roteirizacao mapear(ResultSet l, int n) throws SQLException {
		var calculo = l.getString("calculo");
		return new Roteirizacao(l.getObject("id", UUID.class), l.getObject("unidade_id", UUID.class),
				l.getString("unidade_nome"), l.getObject("data", LocalDate.class),
				OrigemRoteirizacao.valueOf(l.getString("origem")),
				SituacaoRoteirizacao.valueOf(l.getString("situacao")),
				calculo == null ? null : Calculo.valueOf(calculo), l.getString("erro"),
				l.getObject("anterior_id", UUID.class), instanteObrigatorio(l, "criada_em"), l.getString("criada_por"),
				instante(l, "concluida_em"), instante(l, "publicada_em"), List.of(), List.of());
	}

	private static @Nullable Coordenada coordenada(ResultSet l, String lat, String lon) throws SQLException {
		var latitude = l.getObject(lat, Double.class);
		var longitude = l.getObject(lon, Double.class);
		return latitude == null || longitude == null ? null : new Coordenada(latitude, longitude);
	}

	private static @Nullable Instant instante(ResultSet l, String coluna) throws SQLException {
		var valor = l.getTimestamp(coluna);
		return valor == null ? null : valor.toInstant();
	}

	private static Instant instanteObrigatorio(ResultSet l, String coluna) throws SQLException {
		return l.getTimestamp(coluna).toInstant();
	}

	private static @Nullable LocalDate data(ResultSet l, String coluna) throws SQLException {
		return l.getObject(coluna, LocalDate.class);
	}

}
