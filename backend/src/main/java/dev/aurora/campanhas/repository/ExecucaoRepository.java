package dev.aurora.campanhas.repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.campanhas.model.AcaoDeCampo;
import dev.aurora.campanhas.model.AlvoOrdem;
import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.EventoDeCampo;
import dev.aurora.campanhas.model.Posicao;
import dev.aurora.campanhas.model.ResultadoOrdem;
import dev.aurora.campanhas.model.SituacaoEvento;
import dev.aurora.campanhas.model.StatusOrdem;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Eventos do app de campo e o efeito deles na OS (ADR 0045; doc 10, 10.6 e 10.9).
 */
@Repository
public class ExecucaoRepository {

	private static final String PONTO = "ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4674)";

	private static final String EVENTO = """
			SELECT e.id, e.os_id, e.sequencia, e.tipo, e.situacao, e.mensagem, e.resultado, e.motivo_id, e.observacao,
			       e.quantidade, e.valores, e.ocorrido_em, ST_Y(e.posicao) AS latitude, ST_X(e.posicao) AS longitude,
			       e.precisao_m, e.avisos, e.equipe_id, e.usuario_id, e.dispositivo_id, e.versao_pacote
			  FROM os_evento e
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	ExecucaoRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	// ----- OS -----

	/** A OS travada para aplicar eventos do campo. */
	public record OrdemNoCampo(UUID id, UUID unidadeId, StatusOrdem status, @Nullable UUID equipeId,
			int ultimaSequencia, UUID servicoId, UUID servicoVersaoId, String servicoCodigo, boolean conferidoPelaCarga,
			BigDecimal quantidadePrevista, AlvoOrdem alvo, @Nullable String matricula,
			@Nullable LocalDate dataProgramada, @Nullable UUID campanhaId) {
	}

	public Optional<OrdemNoCampo> travar(UUID id) {
		return jdbc.sql("""
				SELECT o.id, o.unidade_id, o.status, o.equipe_id, o.ultima_sequencia, o.servico_id, o.servico_versao_id,
				       s.codigo, v.conferido_pela_carga, o.quantidade_prevista, o.alvo, o.codigo_origem,
				       o.data_programada, o.campanha_id
				  FROM ordem_servico o
				  JOIN servico s ON s.id = o.servico_id
				  JOIN servico_versao v ON v.id = o.servico_versao_id
				 WHERE o.id = :id FOR UPDATE OF o""").param("id", id).query((l, n) -> {
			var data = l.getDate("data_programada");
			return new OrdemNoCampo(l.getObject("id", UUID.class), l.getObject("unidade_id", UUID.class),
					StatusOrdem.valueOf(l.getString("status")), l.getObject("equipe_id", UUID.class),
					l.getInt("ultima_sequencia"), l.getObject("servico_id", UUID.class),
					l.getObject("servico_versao_id", UUID.class), l.getString("codigo"),
					l.getBoolean("conferido_pela_carga"), l.getBigDecimal("quantidade_prevista"),
					AlvoOrdem.valueOf(l.getString("alvo")), l.getString("codigo_origem"),
					data == null ? null : data.toLocalDate(), l.getObject("campanha_id", UUID.class));
		}).optional();
	}

	/** Distância em metros da posição até o alvo (ponto do imóvel ou linha do trecho). */
	public Optional<Double> distanciaAoAlvo(UUID os, Posicao posicao) {
		return jdbc.sql("""
				SELECT ST_Distance(CAST(COALESCE(l.geom, t.geom) AS geography), CAST(%s AS geography))
				  FROM ordem_servico o
				  LEFT JOIN imovel_localizacao l ON l.tenant_id = o.tenant_id AND l.codigo_origem = o.codigo_origem
				  LEFT JOIN trecho t ON t.id = o.trecho_id
				 WHERE o.id = :os AND COALESCE(l.geom, t.geom) IS NOT NULL""".formatted(PONTO))
			.param("os", os)
			.param("latitude", posicao.latitude())
			.param("longitude", posicao.longitude())
			.query(Double.class)
			.optional();
	}

	/**
	 * Unidade da OS, se a pessoa pode enviar fotos dela: é de uma equipe da pessoa ou a
	 * pessoa já registrou eventos nela.
	 */
	public Optional<UUID> unidadeParaAnexo(UUID os, UUID usuario) {
		return jdbc.sql("""
				SELECT o.unidade_id FROM ordem_servico o
				 WHERE o.id = :os
				   AND (o.equipe_id IN (SELECT m.equipe_id FROM equipe_membro m WHERE m.usuario_id = :usuario)
				        OR EXISTS (SELECT 1 FROM os_evento e WHERE e.os_id = o.id AND e.usuario_id = :usuario))""")
			.param("os", os)
			.param("usuario", usuario)
			.query(UUID.class)
			.optional();
	}

	/** Versão do serviço com que a OS nasceu (RN-20). */
	public Optional<UUID> versaoDoServico(UUID os) {
		return jdbc.sql("SELECT servico_versao_id FROM ordem_servico WHERE id = :os")
			.param("os", os)
			.query(UUID.class)
			.optional();
	}

	/** RN-22 (P8): raio do "início fora do local" do cliente. */
	public int raioDoInicio() {
		return jdbc.sql("SELECT campo_raio_inicio_m FROM tenant WHERE id = app_tenant()").query(Integer.class).single();
	}

	/** A equipe já tem outra OS em execução (doc 10, 10.6: uma por vez). */
	public boolean outraEmExecucao(UUID equipe, UUID os) {
		return jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM ordem_servico
				                WHERE equipe_id = :equipe AND status = 'EM_EXECUCAO' AND id <> :os)""")
			.param("equipe", equipe)
			.param("os", os)
			.query(Boolean.class)
			.single();
	}

	/** Outra OS aberta do mesmo alvo e serviço (a cancelada foi substituída). */
	public boolean substituida(UUID os) {
		return jdbc.sql("""
				SELECT EXISTS (
				       SELECT 1 FROM ordem_servico x JOIN ordem_servico o ON o.id = :os
				        WHERE x.id <> o.id AND x.servico_id = o.servico_id
				          AND (x.codigo_origem = o.codigo_origem OR x.trecho_id = o.trecho_id)
				          AND x.status IN ('PLANEJADA', 'PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA', 'DEVOLVIDA'))""")
			.param("os", os)
			.query(Boolean.class)
			.single();
	}

	/**
	 * Vale o campo (10.9.5): a OS fica com a equipe que trabalhou nela; cancelada,
	 * planejada ou devolvida volta a programada para receber o evento.
	 */
	public void assumir(UUID os, UUID equipe, LocalDate dia) {
		jdbc.sql(
				"""
						UPDATE ordem_servico
						   SET equipe_id = :equipe, data_programada = COALESCE(data_programada, :dia),
						       status = CASE WHEN status IN ('CANCELADA', 'PLANEJADA', 'DEVOLVIDA') THEN 'PROGRAMADA' ELSE status END,
						       motivo_id = CASE WHEN status IN ('CANCELADA', 'DEVOLVIDA') THEN NULL ELSE motivo_id END,
						       atualizado_em = now()
						 WHERE id = :os""")
			.param("os", os)
			.param("equipe", equipe)
			.param("dia", Date.valueOf(dia))
			.update();
	}

	/** Iniciar, paralisar, retomar ou devolver. */
	public void mover(UUID os, StatusOrdem status, @Nullable UUID motivo, @Nullable String observacao, Instant ocorrido,
			UUID usuario) {
		jdbc.sql(
				"""
						UPDATE ordem_servico
						   SET status = :status, motivo_id = :motivo, observacao = :observacao,
						       iniciada_em = CASE WHEN :status = 'EM_EXECUCAO' THEN COALESCE(iniciada_em, :ocorrido) ELSE iniciada_em END,
						       movimentada_em = :ocorrido, movimentada_por = :usuario, atualizado_em = now()
						 WHERE id = :os""")
			.param("os", os)
			.param("status", status.name())
			.param("motivo", motivo)
			.param("observacao", observacao)
			.param("ocorrido", Timestamp.from(ocorrido))
			.param("usuario", usuario)
			.update();
	}

	public void marcarForaDoLocal(UUID os) {
		jdbc.sql("UPDATE ordem_servico SET inicio_fora_do_local = true WHERE id = :os").param("os", os).update();
	}

	/** Encerramento pelo app, com os valores dos parâmetros. */
	public void encerrar(UUID os, ResultadoOrdem resultado, @Nullable UUID motivo, @Nullable String observacao,
			BigDecimal quantidade, @Nullable JsonNode valores, Instant ocorrido, UUID usuario,
			Conferencia conferencia) {
		jdbc.sql("""
				UPDATE ordem_servico
				   SET status = 'ENCERRADA', resultado = :resultado, motivo_id = :motivo, observacao = :observacao,
				       quantidade_executada = :quantidade, valores = CAST(:valores AS jsonb), resultado_em = :ocorrido,
				       resultado_por = :usuario, movimentada_em = :ocorrido, movimentada_por = :usuario,
				       conferencia = :conferencia, confirmada_em = NULL, atualizado_em = now()
				 WHERE id = :os""")
			.param("os", os)
			.param("resultado", resultado.name())
			.param("motivo", motivo)
			.param("observacao", observacao)
			.param("quantidade", quantidade)
			.param("valores", valores == null ? null : json.writeValueAsString(valores))
			.param("ocorrido", Timestamp.from(ocorrido))
			.param("usuario", usuario)
			.param("conferencia", conferencia.name())
			.update();
	}

	public void avancarSequencia(UUID os, int sequencia) {
		jdbc.sql(
				"UPDATE ordem_servico SET ultima_sequencia = :sequencia WHERE id = :os AND ultima_sequencia < :sequencia")
			.param("os", os)
			.param("sequencia", sequencia)
			.update();
	}

	/**
	 * J9: o GPS da execução passa a ser a localização do imóvel quando a atual é
	 * aproximada (pelo logradouro ou pelo bairro).
	 * @return true se atualizou
	 */
	public boolean localizarImovelPeloCampo(String matricula, Posicao posicao, UUID usuario) {
		return jdbc.sql("""
				UPDATE imovel_localizacao
				   SET geom = %s, origem = 'CAMPO', precisao = 'ENDERECO', atualizado_em = now(),
				       atualizado_por = :usuario
				 WHERE codigo_origem = :matricula AND precisao IN ('LOGRADOURO', 'BAIRRO')""".formatted(PONTO))
			.param("matricula", matricula)
			.param("latitude", posicao.latitude())
			.param("longitude", posicao.longitude())
			.param("usuario", usuario)
			.update() == 1;
	}

	// ----- Eventos -----

	/** Evento gravado, com quem o enviou. */
	public record EventoGravado(EventoDeCampo evento, SituacaoEvento situacao, @Nullable String mensagem,
			List<String> avisos, UUID equipeId, UUID usuarioId, UUID dispositivoId) {
	}

	public Optional<EventoGravado> evento(UUID id) {
		return jdbc.sql(EVENTO + " WHERE e.id = :id").param("id", id).query(this::mapearEvento).optional();
	}

	/** O próximo evento que esperava a sequência anterior. */
	public Optional<EventoGravado> aguardando(UUID os, int sequencia) {
		return jdbc.sql(EVENTO + " WHERE e.os_id = :os AND e.sequencia = :sequencia AND e.situacao = 'AGUARDANDO'")
			.param("os", os)
			.param("sequencia", sequencia)
			.query(this::mapearEvento)
			.optional();
	}

	/** Outro evento (não recusado) já ocupa a sequência. */
	public boolean sequenciaOcupada(UUID os, int sequencia) {
		return jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM os_evento WHERE os_id = :os AND sequencia = :sequencia
				                                         AND situacao <> 'REJEITADO')""")
			.param("os", os)
			.param("sequencia", sequencia)
			.query(Boolean.class)
			.single();
	}

	public void inserir(EventoDeCampo e, UUID unidade, SituacaoEvento situacao, @Nullable String mensagem,
			List<String> avisos, UUID equipe, UUID usuario, UUID dispositivo) {
		var p = e.posicao();
		jdbc.sql("""
				INSERT INTO os_evento (id, tenant_id, os_id, unidade_id, sequencia, tipo, situacao, mensagem, resultado,
				                       motivo_id, observacao, quantidade, valores, ocorrido_em, posicao, precisao_m,
				                       avisos, equipe_id, usuario_id, dispositivo_id, versao_pacote)
				VALUES (:id, app_tenant(), :os, :unidade, :sequencia, :tipo, :situacao, :mensagem, :resultado, :motivo,
				        :observacao, :quantidade, CAST(:valores AS jsonb), :ocorrido,
				        CASE WHEN CAST(:latitude AS double precision) IS NULL THEN NULL
				             ELSE ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4674) END,
				        :precisao, CAST(:avisos AS text[]), :equipe, :usuario, :dispositivo, :versaoPacote)""")
			.param("id", e.id())
			.param("os", e.osId())
			.param("unidade", unidade)
			.param("sequencia", e.sequencia())
			.param("tipo", e.tipo().name())
			.param("situacao", situacao.name())
			.param("mensagem", mensagem)
			.param("resultado", e.resultado() == null ? null : e.resultado().name())
			.param("motivo", e.motivoId())
			.param("observacao", e.observacao())
			.param("quantidade", e.quantidade())
			.param("valores", e.valores() == null || e.valores().isNull() ? null : json.writeValueAsString(e.valores()))
			.param("ocorrido", Timestamp.from(e.ocorridoEm()))
			.param("latitude", p == null ? null : p.latitude())
			.param("longitude", p == null ? null : p.longitude())
			.param("precisao", p == null ? null : p.precisaoM())
			.param("avisos", avisos.toArray(String[]::new))
			.param("equipe", equipe)
			.param("usuario", usuario)
			.param("dispositivo", dispositivo)
			.param("versaoPacote", e.versaoPacote())
			.update();
	}

	/** Resultado do processamento (o evento já gravado). */
	public void processado(UUID id, SituacaoEvento situacao, @Nullable String mensagem, List<String> avisos,
			@Nullable Double distancia, @Nullable UUID jornada, @Nullable JsonNode valores) {
		jdbc.sql(
				"""
						UPDATE os_evento
						   SET situacao = :situacao, mensagem = :mensagem, avisos = CAST(:avisos AS text[]), distancia_m = :distancia,
						       jornada_id = :jornada, valores = COALESCE(CAST(:valores AS jsonb), valores), processado_em = now()
						 WHERE id = :id""")
			.param("id", id)
			.param("situacao", situacao.name())
			.param("mensagem", mensagem)
			.param("avisos", avisos.toArray(String[]::new))
			.param("distancia", distancia)
			.param("jornada", jornada)
			.param("valores", valores == null ? null : json.writeValueAsString(valores))
			.update();
	}

	private EventoGravado mapearEvento(ResultSet l, int n) throws SQLException {
		var resultado = l.getString("resultado");
		var valores = l.getString("valores");
		var latitude = l.getDouble("latitude");
		var semPonto = l.wasNull();
		var precisao = l.getBigDecimal("precisao_m");
		var versao = l.getLong("versao_pacote");
		var semVersao = l.wasNull();
		var evento = new EventoDeCampo(l.getObject("id", UUID.class), l.getObject("os_id", UUID.class),
				l.getInt("sequencia"), AcaoDeCampo.valueOf(l.getString("tipo")), l.getObject("motivo_id", UUID.class),
				l.getString("observacao"), resultado == null ? null : ResultadoOrdem.valueOf(resultado),
				l.getBigDecimal("quantidade"), valores == null ? null : json.readTree(valores),
				l.getTimestamp("ocorrido_em").toInstant(), semPonto ? null : new Posicao(latitude,
						l.getDouble("longitude"), precisao == null ? null : precisao.doubleValue()),
				semVersao ? null : versao);
		return new EventoGravado(evento, SituacaoEvento.valueOf(l.getString("situacao")), l.getString("mensagem"),
				avisos(l.getArray("avisos")), l.getObject("equipe_id", UUID.class),
				l.getObject("usuario_id", UUID.class), l.getObject("dispositivo_id", UUID.class));
	}

	static List<String> avisos(@Nullable Array array) throws SQLException {
		return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
	}

}
