package dev.aurora.imoveis.repository;

import java.sql.SQLException;
import java.util.ArrayList;

import dev.aurora.BancoDeTeste;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Situação mensal particionada por ano da competência (ADR 0038). */
class ParticaoSituacaoMensalTest {

	@Test
	void consultaDeUmaCompetenciaLeSoAParticaoDoAno() throws SQLException {
		var plano = new ArrayList<String>();
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.createStatement();
				var linhas = consulta.executeQuery("""
						EXPLAIN SELECT count(*) FROM imovel_situacao_mensal
						 WHERE competencia = DATE '2026-08-01'""")) {
			while (linhas.next()) {
				plano.add(linhas.getString(1));
			}
		}
		assertThat(String.join("\n", plano)).contains("imovel_situacao_2026")
			.doesNotContain("imovel_situacao_2025")
			.doesNotContain("imovel_situacao_2027");
	}

	@Test
	void aplicacaoNaoLeAParticaoDiretamente() throws SQLException {
		try (var c = BancoDeTeste.comoAplicacao(); var consulta = c.createStatement()) {
			assertThatThrownBy(() -> consulta.executeQuery("SELECT 1 FROM imovel_situacao_2026"))
				.hasMessageContaining("permission denied");
		}
	}

	@Test
	void competenciaForaDasParticoesFalha() throws SQLException {
		try (var c = BancoDeTeste.comoSuperusuario(); var consulta = c.createStatement()) {
			assertThatThrownBy(() -> consulta.executeUpdate("""
					INSERT INTO imovel_situacao_mensal (tenant_id, competencia, codigo_origem, unidade_id, situacao,
					                                    fonte_dados_id, execucao_id)
					VALUES (gen_random_uuid(), DATE '2051-01-01', 'X', gen_random_uuid(), 'LIGADO',
					        gen_random_uuid(), gen_random_uuid())""")).hasMessageContaining("no partition");
		}
	}

}
