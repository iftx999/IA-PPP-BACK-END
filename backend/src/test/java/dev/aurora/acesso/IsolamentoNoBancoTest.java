package dev.aurora.acesso;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RLS como última barreira: conectado como aurora_app, sem passar pela API. */
class IsolamentoNoBancoTest {

	/** Tabelas com tenant_id lidas antes de existir contexto; não contêm dado de negócio. */
	private static final Set<String> SEM_RLS_PERMITIDO = Set.of("tenant_emissor", "credencial_conector",
			"tarefa_em_fila");

	private static Tenant a;

	private static Tenant b;

	@BeforeAll
	static void criarTenants() throws SQLException {
		a = DadosDeTeste.criarTenant("Tenant A");
		b = DadosDeTeste.criarTenant("Tenant B");
	}

	@Test
	void todaTabelaComTenantTemRlsForcadoOuNaoEstaAcessivelParaAAplicacao() throws SQLException {
		var falhas = new ArrayList<String>();
		try (var c = BancoDeTeste.comoSuperusuario(); var consulta = c.createStatement()) {
			var linhas = consulta.executeQuery("""
					SELECT c.relname,
					       c.relrowsecurity AND c.relforcerowsecurity AS rls,
					       EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid) AS politica,
					       has_table_privilege('aurora_app', c.oid, 'SELECT,INSERT,UPDATE,DELETE') AS acessivel
					  FROM pg_class c
					  JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
					  JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
					 WHERE c.relkind IN ('r', 'p')""");
			while (linhas.next()) {
				var tabela = linhas.getString("relname");
				var protegida = linhas.getBoolean("rls") && linhas.getBoolean("politica");
				if (!protegida && linhas.getBoolean("acessivel") && !SEM_RLS_PERMITIDO.contains(tabela)) {
					falhas.add(tabela);
				}
			}
		}
		assertThat(falhas).as("tabelas com tenant_id acessíveis sem RLS").isEmpty();
	}

	@Test
	void semContextoAConsultaFalha() throws SQLException {
		try (var c = BancoDeTeste.comoAplicacao()) {
			assertThatThrownBy(() -> ids(c, "SELECT id FROM unidade"))
				.hasMessageContaining("contexto de tenant não definido");
		}
	}

	@Test
	void tenantSoEnxergaAsProprias() throws SQLException {
		try (var c = comContexto(a, DadosDeTeste.rotulo(a.grupo()))) {
			assertThat(ids(c, "SELECT id FROM unidade")).hasSize(5).doesNotContain(b.grupo(), b.cidadeA());
			assertThat(ids(c, "SELECT tenant_id AS id FROM imovel_situacao_mensal")).containsOnly(a.id());
			assertThat(ids(c, "SELECT id FROM tenant")).containsExactly(a.id());
			assertThat(ids(c, "SELECT id FROM perfil")).containsExactly(a.perfilAdmin());
		}
	}

	@Test
	void naoGravaEmOutroTenant() throws SQLException {
		try (var c = comContexto(a, DadosDeTeste.rotulo(a.grupo())); var comando = c.prepareStatement("""
				INSERT INTO fonte_dados (id, tenant_id, nome, tipo_adaptador)
				VALUES (gen_random_uuid(), ?::uuid, 'Intrusa', 'ARQUIVO')""")) {
			comando.setString(1, b.id().toString());
			assertThatThrownBy(comando::execute).hasMessageContaining("row-level security");
		}
	}

	@Test
	void escopoLimitaOsDadosAUnidadeEDescendentes() throws SQLException {
		var cidadeA = DadosDeTeste.rotulo(a.grupo(), a.concessao(), a.cidadeA());
		try (var c = comContexto(a, cidadeA); var consulta = c.createStatement()) {
			var linhas = consulta.executeQuery("SELECT codigo_origem FROM imovel_situacao_mensal");
			var matriculas = new ArrayList<String>();
			while (linhas.next()) {
				matriculas.add(linhas.getString(1));
			}
			assertThat(matriculas).containsExactly("A-001");
		}
	}

	private static Connection comContexto(Tenant t, String escopos) throws SQLException {
		var c = BancoDeTeste.comoAplicacao();
		try (var comando = c.prepareStatement(
				"SELECT set_config('app.tenant_id', ?, false), set_config('app.escopos', ?, false)")) {
			comando.setString(1, t.id().toString());
			comando.setString(2, escopos);
			comando.execute();
		}
		return c;
	}

	private static List<UUID> ids(Connection c, String sql) throws SQLException {
		var ids = new ArrayList<UUID>();
		try (var consulta = c.createStatement(); var linhas = consulta.executeQuery(sql)) {
			while (linhas.next()) {
				ids.add(linhas.getObject("id", UUID.class));
			}
		}
		return ids;
	}

}
