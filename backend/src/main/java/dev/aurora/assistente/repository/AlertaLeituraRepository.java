package dev.aurora.assistente.repository;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Alertas abertos para a ferramenta do assistente (só leitura; RLS por escopo). Alertas
 * sem unidade (fonte atrasada, trilha alterada) só para quem atua no cliente inteiro.
 */
@Repository
public class AlertaLeituraRepository {

	public record AlertaAberto(String titulo, String descricao, String severidade, @Nullable String unidade,
			Instant desde) {
	}

	private final JdbcClient jdbc;

	AlertaLeituraRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * @param caminho limita à subárvore da unidade; nulo = todo o escopo
	 */
	public List<AlertaAberto> listarAbertos(@Nullable String caminho, boolean incluirSemUnidade, int limite) {
		return jdbc.sql("""
				SELECT a.titulo, a.descricao, a.severidade, u.nome AS unidade, a.criado_em AS desde
				  FROM alerta a
				  LEFT JOIN unidade u ON u.id = a.unidade_id
				 WHERE a.resolvido_em IS NULL
				   AND ((a.unidade_id IS NULL AND :sem_unidade AND CAST(:caminho AS text) IS NULL)
				        OR (u.caminho <@ ANY (app_escopos())
				            AND (CAST(:caminho AS text) IS NULL OR u.caminho <@ CAST(:caminho AS ltree))))
				 ORDER BY a.severidade = 'CRITICO' DESC, a.criado_em DESC
				 LIMIT :limite""")
			.param("caminho", caminho)
			.param("sem_unidade", incluirSemUnidade)
			.param("limite", limite)
			.query(AlertaAberto.class)
			.list();
	}

}
