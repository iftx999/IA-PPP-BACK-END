package dev.aurora.assistente.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.assistente.model.Conversa;
import dev.aurora.assistente.model.Fonte;
import dev.aurora.assistente.model.LinhaDeDados;
import dev.aurora.assistente.model.Mensagem;
import dev.aurora.assistente.model.Papel;
import dev.aurora.assistente.model.Uso;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Conversas e mensagens do assistente. O RLS só mostra as conversas da própria pessoa
 * ({@code usuario_id = app_usuario()}): nem administradores leem conversas alheias.
 */
@Repository
public class ConversaRepository {

	private static final TypeReference<List<Fonte>> FONTES = new TypeReference<>() {
	};

	private static final TypeReference<List<LinhaDeDados>> DADOS = new TypeReference<>() {
	};

	private static final TypeReference<List<String>> TEXTOS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	private final JsonMapper json;

	ConversaRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public void criar(UUID id, String titulo) {
		jdbc.sql("""
				INSERT INTO assistente_conversa (id, tenant_id, usuario_id, titulo)
				VALUES (:id, app_tenant(), app_usuario(), :titulo)""").param("id", id).param("titulo", titulo).update();
	}

	public Optional<Conversa> buscar(UUID id) {
		return jdbc.sql("SELECT id, titulo, criado_em, atualizado_em FROM assistente_conversa WHERE id = ?")
			.param(id)
			.query(ConversaRepository::conversa)
			.optional();
	}

	/**
	 * Conversas da pessoa, das mais recentes; com busca, as que têm o termo no título ou
	 * em alguma mensagem (busca textual em português, ou trecho literal).
	 */
	public List<Conversa> listar(@Nullable String busca, int limite) {
		return jdbc.sql("""
				SELECT c.id, c.titulo, c.criado_em, c.atualizado_em
				  FROM assistente_conversa c
				 WHERE CAST(:busca AS text) IS NULL
				    OR to_tsvector('portuguese', c.titulo) @@ plainto_tsquery('portuguese', :busca)
				    OR c.titulo ILIKE '%' || :literal || '%'
				    OR EXISTS (SELECT 1 FROM assistente_mensagem m
				                WHERE m.conversa_id = c.id
				                  AND (to_tsvector('portuguese', m.texto) @@ plainto_tsquery('portuguese', :busca)
				                       OR m.texto ILIKE '%' || :literal || '%'))
				 ORDER BY c.atualizado_em DESC
				 LIMIT :limite""")
			.param("busca", busca)
			.param("literal",
					busca == null ? null : busca.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))
			.param("limite", limite)
			.query(ConversaRepository::conversa)
			.list();
	}

	public List<Mensagem> listarMensagens(UUID conversa) {
		return jdbc.sql("""
				SELECT id, papel, texto, fontes::text, dados::text, numeros_sem_fonte::text, avaliacao, criado_em
				  FROM assistente_mensagem WHERE conversa_id = ? ORDER BY criado_em, id""")
			.param(conversa)
			.query(this::mensagem)
			.list();
	}

	public Optional<Mensagem> buscarMensagem(UUID id) {
		return jdbc.sql("""
				SELECT id, papel, texto, fontes::text, dados::text, numeros_sem_fonte::text, avaliacao, criado_em
				  FROM assistente_mensagem WHERE id = ?""").param(id).query(this::mensagem).optional();
	}

	public void inserirPergunta(UUID id, UUID conversa, String texto) {
		jdbc.sql("""
				INSERT INTO assistente_mensagem (id, tenant_id, conversa_id, papel, texto)
				VALUES (:id, app_tenant(), :conversa, 'USUARIO', :texto)""")
			.param("id", id)
			.param("conversa", conversa)
			.param("texto", texto)
			.update();
		tocar(conversa);
	}

	public void inserirResposta(UUID id, UUID conversa, String texto, List<Fonte> fontes, List<LinhaDeDados> dados,
			List<String> semFonte, @Nullable String modelo, Uso uso, BigDecimal custo) {
		jdbc.sql("""
				INSERT INTO assistente_mensagem (id, tenant_id, conversa_id, papel, texto, fontes, dados,
				                                 numeros_sem_fonte, modelo, tokens_entrada, tokens_saida,
				                                 tokens_cache_lido, tokens_cache_escrito, custo_usd)
				VALUES (:id, app_tenant(), :conversa, 'ASSISTENTE', :texto, CAST(:fontes AS jsonb),
				        CAST(:dados AS jsonb), CAST(:sem_fonte AS jsonb), :modelo, :entrada, :saida, :lido,
				        :escrito, :custo)""")
			.param("id", id)
			.param("conversa", conversa)
			.param("texto", texto)
			.param("fontes", json.writeValueAsString(fontes))
			.param("dados", json.writeValueAsString(dados))
			.param("sem_fonte", json.writeValueAsString(semFonte))
			.param("modelo", modelo)
			.param("entrada", (int) uso.entrada())
			.param("saida", (int) uso.saida())
			.param("lido", (int) uso.cacheLido())
			.param("escrito", (int) uso.cacheEscrito())
			.param("custo", custo)
			.update();
		tocar(conversa);
	}

	/** G4: só respostas do assistente recebem avaliação. */
	public boolean avaliar(UUID mensagem, int avaliacao, @Nullable String comentario) {
		return jdbc.sql("""
				UPDATE assistente_mensagem
				   SET avaliacao = :avaliacao, avaliacao_comentario = :comentario, avaliado_em = now()
				 WHERE id = :id AND papel = 'ASSISTENTE'""")
			.param("avaliacao", (short) avaliacao)
			.param("comentario", comentario)
			.param("id", mensagem)
			.update() == 1;
	}

	private void tocar(UUID conversa) {
		jdbc.sql("UPDATE assistente_conversa SET atualizado_em = now() WHERE id = ?").param(conversa).update();
	}

	private static Conversa conversa(ResultSet linha, int n) throws SQLException {
		return new Conversa(linha.getObject("id", UUID.class), linha.getString("titulo"),
				linha.getTimestamp("criado_em").toInstant(), linha.getTimestamp("atualizado_em").toInstant());
	}

	private Mensagem mensagem(ResultSet linha, int n) throws SQLException {
		var avaliacao = linha.getObject("avaliacao");
		return new Mensagem(linha.getObject("id", UUID.class), Papel.valueOf(linha.getString("papel")),
				linha.getString("texto"), json.readValue(linha.getString("fontes"), FONTES),
				json.readValue(linha.getString("dados"), DADOS),
				json.readValue(linha.getString("numeros_sem_fonte"), TEXTOS),
				avaliacao == null ? null : ((Number) avaliacao).intValue(),
				linha.getTimestamp("criado_em").toInstant());
	}

}
