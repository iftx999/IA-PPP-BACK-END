package dev.aurora.servicos.repository;

import java.sql.DriverManager;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0042: a V31 roda num banco que já tem clientes (com o RLS forçado de "tenant") e
 * semeia o catálogo e o perfil "Gestor de serviços" de cada um. Os demais testes começam
 * de um banco vazio e não pegariam esse caso.
 */
class MigracaoDoCatalogoTest {

	@Test
	void semeiaClientesExistentes() throws Exception {
		var banco = "aurora_migracao_" + UUID.randomUUID().toString().substring(0, 8);
		var urlBase = BancoDeTeste.url();
		try (var c = BancoDeTeste.comoSuperusuario(); var s = c.createStatement()) {
			s.execute("CREATE DATABASE " + banco + " OWNER aurora_owner");
		}
		var url = urlBase.substring(0, urlBase.lastIndexOf('/') + 1) + banco;
		try (var c = DriverManager.getConnection(url, "postgres", BancoDeTeste.SENHA_SUPERUSUARIO);
				var s = c.createStatement()) {
			for (var extensao : new String[] { "postgis", "ltree", "vector", "pg_trgm", "btree_gist" }) {
				s.execute("CREATE EXTENSION IF NOT EXISTS " + extensao);
			}
			s.execute("ALTER SCHEMA public OWNER TO aurora_owner");
		}
		var flyway = Flyway.configure().dataSource(url, "aurora_owner", BancoDeTeste.SENHA_OWNER);
		flyway.target("28").load().migrate();

		var cliente = UUID.randomUUID();
		try (var c = DriverManager.getConnection(url, "postgres", BancoDeTeste.SENHA_SUPERUSUARIO);
				var s = c.createStatement()) {
			s.execute("INSERT INTO tenant (id, nome, codigo) VALUES ('%s', 'Cliente antigo', 'cliente-antigo')"
				.formatted(cliente));
			s.execute("""
					INSERT INTO perfil (id, tenant_id, nome, sistema)
					VALUES (gen_random_uuid(), '%s', 'Administrador do tenant', true)""".formatted(cliente));
		}

		flyway.target("31").load().migrate();

		try (var c = DriverManager.getConnection(url, "postgres", BancoDeTeste.SENHA_SUPERUSUARIO);
				var s = c.createStatement();
				var linhas = s.executeQuery("""
						SELECT (SELECT count(*) FROM servico WHERE tenant_id = '%1$s'),
						       (SELECT count(*) FROM parametro_servico WHERE tenant_id = '%1$s'),
						       (SELECT count(*) FROM perfil WHERE tenant_id = '%1$s' AND nome = 'Gestor de serviços'),
						       (SELECT count(*) FROM perfil_permissao pp JOIN perfil p ON p.id = pp.perfil_id
						         WHERE p.tenant_id = '%1$s' AND p.nome = 'Administrador do tenant'
						           AND pp.permissao = 'servico:gerenciar'),
						       (SELECT relforcerowsecurity FROM pg_class WHERE relname = 'tenant')"""
					.formatted(cliente))) {
			linhas.next();
			assertThat(linhas.getLong(1)).isEqualTo(12);
			assertThat(linhas.getLong(2)).isEqualTo(39);
			assertThat(linhas.getLong(3)).isEqualTo(1);
			assertThat(linhas.getLong(4)).isEqualTo(1);
			assertThat(linhas.getBoolean(5)).as("RLS forçado de tenant religado").isTrue();
		}
	}

}
