package dev.aurora.servicos.repository;

import java.sql.DriverManager;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0042: a V31 roda num banco que já tem clientes (com o RLS forçado de "tenant") e
 * semeia o catálogo e o perfil "Gestor de serviços" de cada um; ADR 0043: a V32 dá a
 * permissão dos logradouros aos perfis desses clientes; ADR 0044: a V33 leva as OS da V28
 * para o catálogo e o novo ciclo. Os demais testes começam de um banco vazio e não
 * pegariam esse caso.
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
			s.execute(ordensDaV28(cliente));
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

		// ADR 0043: a V32 dá "logradouro:gerenciar" ao gestor de serviços e ao
		// administrador
		// dos clientes que já existiam.
		flyway.target("32").load().migrate();

		try (var c = DriverManager.getConnection(url, "postgres", BancoDeTeste.SENHA_SUPERUSUARIO);
				var s = c.createStatement();
				var linhas = s.executeQuery("""
						SELECT (SELECT count(*) FROM perfil_permissao pp JOIN perfil p ON p.id = pp.perfil_id
						         WHERE p.tenant_id = '%1$s' AND pp.permissao = 'logradouro:gerenciar'),
						       (SELECT relforcerowsecurity FROM pg_class WHERE relname = 'perfil_permissao')"""
					.formatted(cliente))) {
			linhas.next();
			assertThat(linhas.getLong(1)).as("gestor de serviços e administrador").isEqualTo(2);
			assertThat(linhas.getBoolean(2)).as("RLS forçado de perfil_permissao religado").isTrue();
		}

		// ADR 0044: as OS da V28 vão para o serviço do catálogo e para o novo ciclo.
		flyway.target("33").load().migrate();

		try (var c = DriverManager.getConnection(url, "postgres", BancoDeTeste.SENHA_SUPERUSUARIO);
				var s = c.createStatement();
				var linhas = s.executeQuery("""
						SELECT o.codigo_origem, o.numero, s.codigo, o.status, o.resultado, m.codigo AS motivo,
						       o.data_programada, o.quantidade_executada, o.alvo,
						       to_char(o.prazo_em AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM-DD HH24:MI') AS prazo
						  FROM ordem_servico o
						  JOIN servico s ON s.id = o.servico_id
						  JOIN servico_versao v ON v.id = o.servico_versao_id AND v.servico_id = s.id
						  LEFT JOIN motivo m ON m.id = o.motivo_id
						 WHERE o.tenant_id = '%s' ORDER BY o.codigo_origem""".formatted(cliente))) {
			var esperado = new String[][] {
					// matrícula, serviço, status, resultado, motivo, data programada,
					// executada
					{ "M-1", "LIGACAO_NOVA", "PROGRAMADA", null, null, "2026-10-05", null },
					{ "M-2", "RELIGACAO", "PROGRAMADA", null, null, "2026-12-01", null },
					{ "M-3", "LIGACAO_NOVA", "ENCERRADA", "NAO_EXECUTADA", "SEM_INTERESSE", null, "0.00" },
					{ "M-4", "LIGACAO_NOVA", "ENCERRADA", "EXECUTADA", null, null, "1.00" },
					{ "M-5", "VISTORIA", "PLANEJADA", null, null, null, null } };
			for (int i = 0; i < esperado.length; i++) {
				assertThat(linhas.next()).isTrue();
				var e = esperado[i];
				assertThat(linhas.getString("codigo_origem")).isEqualTo(e[0]);
				assertThat(linhas.getLong("numero")).as("numeração no cliente").isEqualTo(i + 1);
				assertThat(linhas.getString("codigo")).isEqualTo(e[1]);
				assertThat(linhas.getString("status")).isEqualTo(e[2]);
				assertThat(linhas.getString("resultado")).isEqualTo(e[3]);
				assertThat(linhas.getString("motivo")).isEqualTo(e[4]);
				assertThat(linhas.getString("data_programada")).isEqualTo(e[5]);
				assertThat(linhas.getString("quantidade_executada")).isEqualTo(e[6]);
				assertThat(linhas.getString("alvo")).isEqualTo("IMOVEL");
				assertThat(linhas.getString("prazo")).as("fim do dia do prazo antigo").isEqualTo("2026-12-31 23:59");
			}
			assertThat(linhas.next()).isFalse();
		}
		try (var c = DriverManager.getConnection(url, "postgres", BancoDeTeste.SENHA_SUPERUSUARIO);
				var s = c.createStatement();
				var linhas = s.executeQuery(
						"""
								SELECT (SELECT ultimo FROM contador_os WHERE tenant_id = '%1$s'),
								       (SELECT count(*) FROM perfil_permissao pp JOIN perfil p ON p.id = pp.perfil_id
								         WHERE p.tenant_id = '%1$s' AND pp.permissao = 'os:programar'),
								       (SELECT bool_and(relforcerowsecurity) FROM pg_class
								         WHERE relname IN ('ordem_servico', 'servico', 'motivo', 'tenant', 'perfil', 'trecho', 'usuario'))"""
							.formatted(cliente))) {
			linhas.next();
			assertThat(linhas.getLong(1)).as("contador na última OS").isEqualTo(5);
			assertThat(linhas.getLong(2)).as("gestor de serviços e administrador").isEqualTo(2);
			assertThat(linhas.getBoolean(3)).as("RLS forçado religado").isTrue();
		}

		// ADR 0045: celulares do app geridos pelo gestor de serviços e pelo
		// administrador;
		// as OS migradas começam sem eventos do app.
		flyway.target("34").load().migrate();

		try (var c = DriverManager.getConnection(url, "postgres", BancoDeTeste.SENHA_SUPERUSUARIO);
				var s = c.createStatement();
				var linhas = s.executeQuery(
						"""
								SELECT (SELECT count(*) FROM perfil_permissao pp JOIN perfil p ON p.id = pp.perfil_id
								         WHERE p.tenant_id = '%1$s' AND pp.permissao = 'dispositivo:gerenciar'),
								       (SELECT count(*) FROM ordem_servico WHERE tenant_id = '%1$s' AND ultima_sequencia = 0),
								       (SELECT campo_raio_inicio_m FROM tenant WHERE id = '%1$s'),
								       (SELECT bool_and(relforcerowsecurity) FROM pg_class
								         WHERE relname IN ('perfil', 'perfil_permissao', 'os_evento', 'jornada', 'dispositivo'))"""
							.formatted(cliente))) {
			linhas.next();
			assertThat(linhas.getLong(1)).as("gestor de serviços e administrador").isEqualTo(2);
			assertThat(linhas.getLong(2)).as("OS migradas sem eventos").isEqualTo(5);
			assertThat(linhas.getInt(3)).as("raio padrão do início fora do local").isEqualTo(300);
			assertThat(linhas.getBoolean(4)).as("RLS forçado").isTrue();
		}
	}

	/**
	 * Uma campanha com OS em cada situação da V28 (em campo, reagendada, recusada...).
	 */
	private static String ordensDaV28(UUID cliente) {
		return """
				INSERT INTO unidade (id, tenant_id, tipo, codigo, nome, caminho, codigo_ibge)
				VALUES ('%2$s', '%1$s', 'MUNICIPIO', 'm', 'Município', 'm', '3526902');
				INSERT INTO usuario (id, tenant_id, sub_externo, email, nome)
				VALUES ('%3$s', '%1$s', 'sub-antigo', 'antigo@cliente.teste', 'Antigo');
				INSERT INTO equipe (id, tenant_id, unidade_id, nome, tipo)
				VALUES ('%4$s', '%1$s', '%2$s', 'Equipe antiga', 'PROPRIA');
				INSERT INTO campanha (id, tenant_id, unidade_id, nome, origem, criterios, acao, competencia, prazo,
				                      ligados_na_criacao, com_rede_na_criacao, criada_por)
				VALUES ('%5$s', '%1$s', '%2$s', 'Campanha antiga', 'SEM_LIGACAO', '{}', 'LIGACAO', DATE '2026-09-01',
				        DATE '2026-12-31', 0, 0, '%3$s');
				INSERT INTO ordem_servico (id, tenant_id, campanha_id, numero, codigo_origem, unidade_id, tipo,
				                           situacao_na_criacao, prioridade, prazo, equipe_id, enviada_em, status, motivo,
				                           reagendada_para, resultado_em, resultado_por, conferencia, criada_em)
				VALUES
				  (gen_random_uuid(), '%1$s', '%5$s', 1, 'M-1', '%2$s', 'LIGACAO_NOVA', 'FACTIVEL', 'ALTA',
				   DATE '2026-12-31', '%4$s', TIMESTAMPTZ '2026-10-05 09:00-03', 'EM_CAMPO', NULL, NULL, NULL, NULL,
				   'NAO_SE_APLICA', TIMESTAMPTZ '2026-10-01 08:00-03'),
				  (gen_random_uuid(), '%1$s', '%5$s', 2, 'M-2', '%2$s', 'RELIGACAO', 'CORTADO', 'ALTA',
				   DATE '2026-12-31', '%4$s', TIMESTAMPTZ '2026-10-05 09:00-03', 'REAGENDADA', NULL, DATE '2026-12-01',
				   NULL, '%3$s', 'NAO_SE_APLICA', TIMESTAMPTZ '2026-10-01 08:01-03'),
				  (gen_random_uuid(), '%1$s', '%5$s', 3, 'M-3', '%2$s', 'LIGACAO_NOVA', 'FACTIVEL', 'MEDIA',
				   DATE '2026-12-31', '%4$s', TIMESTAMPTZ '2026-10-05 09:00-03', 'RECUSADA', 'SEM_INTERESSE', NULL,
				   TIMESTAMPTZ '2026-10-06 10:00-03', '%3$s', 'NAO_SE_APLICA', TIMESTAMPTZ '2026-10-01 08:02-03'),
				  (gen_random_uuid(), '%1$s', '%5$s', 4, 'M-4', '%2$s', 'LIGACAO_NOVA', 'FACTIVEL', 'MEDIA',
				   DATE '2026-12-31', '%4$s', TIMESTAMPTZ '2026-10-05 09:00-03', 'EXECUTADA', NULL, NULL,
				   TIMESTAMPTZ '2026-10-06 11:00-03', '%3$s', 'PENDENTE', TIMESTAMPTZ '2026-10-01 08:03-03'),
				  (gen_random_uuid(), '%1$s', '%5$s', 5, 'M-5', '%2$s', 'VISTORIA', 'FACTIVEL', 'BAIXA',
				   DATE '2026-12-31', NULL, NULL, 'PLANEJADA', NULL, NULL, NULL, NULL, 'NAO_SE_APLICA',
				   TIMESTAMPTZ '2026-10-01 08:04-03');
				"""
			.formatted(cliente, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
	}

}
