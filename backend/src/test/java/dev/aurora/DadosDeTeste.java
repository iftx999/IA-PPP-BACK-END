package dev.aurora;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Cria, como superusuário (ignora RLS), um tenant com a hierarquia
 * Grupo > Concessão > {Cidade A > Setor A1, Cidade B} e um usuário com o perfil informado.
 */
public final class DadosDeTeste {

	public record Tenant(UUID id, String emissor, UUID grupo, UUID concessao, UUID cidadeA, UUID setorA1,
			UUID cidadeB, UUID perfilAdmin, UUID fonte, UUID execucao) {
	}

	private DadosDeTeste() {
	}

	public static Tenant criarTenant(String nome) throws SQLException {
		var t = new Tenant(UUID.randomUUID(), "https://idp.teste/realms/" + UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID());
		try (var c = BancoDeTeste.comoSuperusuario()) {
			executar(c, "INSERT INTO tenant (id, nome) VALUES (?::uuid, ?)", t.id(), nome);
			executar(c, "INSERT INTO tenant_emissor (emissor, tenant_id) VALUES (?, ?::uuid)", t.emissor(), t.id());
			unidade(c, t, t.grupo(), null, "GRUPO", null, rotulo(t.grupo()));
			unidade(c, t, t.concessao(), t.grupo(), "CONCESSAO", null, rotulo(t.grupo(), t.concessao()));
			unidade(c, t, t.cidadeA(), t.concessao(), "MUNICIPIO", "3526902",
					rotulo(t.grupo(), t.concessao(), t.cidadeA()));
			unidade(c, t, t.setorA1(), t.cidadeA(), "SETOR_COMERCIAL", null,
					rotulo(t.grupo(), t.concessao(), t.cidadeA(), t.setorA1()));
			unidade(c, t, t.cidadeB(), t.concessao(), "MUNICIPIO", "3538709",
					rotulo(t.grupo(), t.concessao(), t.cidadeB()));

			executar(c, "INSERT INTO perfil (id, tenant_id, nome, sistema) VALUES (?::uuid, ?::uuid, 'Administrador', true)",
					t.perfilAdmin(), t.id());
			executar(c, """
					INSERT INTO perfil_permissao (tenant_id, perfil_id, permissao)
					SELECT ?::uuid, ?::uuid, codigo FROM permissao""", t.id(), t.perfilAdmin());

			executar(c, "INSERT INTO fonte_dados (id, tenant_id, nome, tipo_adaptador) VALUES (?::uuid, ?::uuid, 'Comercial', 'ARQUIVO')",
					t.fonte(), t.id());
			executar(c, """
					INSERT INTO execucao_sincronizacao (id, tenant_id, fonte_dados_id, inicio, status)
					VALUES (?::uuid, ?::uuid, ?::uuid, now(), 'SUCESSO')""", t.execucao(), t.id(), t.fonte());
			imovel(c, t, "A-001", t.setorA1(), "LIGADO");
			imovel(c, t, "B-001", t.cidadeB(), "FACTIVEL");
		}
		return t;
	}

	/** Cria um usuário com o perfil de administrador na unidade informada (escopo). */
	public static UUID criarUsuario(Tenant t, String sub, String email, UUID unidadeEscopo) throws SQLException {
		var id = UUID.randomUUID();
		try (var c = BancoDeTeste.comoSuperusuario()) {
			executar(c, "INSERT INTO usuario (id, tenant_id, sub_externo, email, nome) VALUES (?::uuid, ?::uuid, ?, ?, ?)",
					id, t.id(), sub, email, email);
			executar(c, """
					INSERT INTO usuario_perfil_escopo (id, tenant_id, usuario_id, perfil_id, unidade_id)
					VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid)""",
					UUID.randomUUID(), t.id(), id, t.perfilAdmin(), unidadeEscopo);
		}
		return id;
	}

	/**
	 * Medições de adesão de jul e ago/2026 (números do demo) e metas: concessão 80/75 até dez/2026 (do contrato),
	 * Cidade A 85/80 e Cidade B 75/70 sem fim. O setor A1 não tem meta própria (herda da Cidade A).
	 */
	public static void criarAdesao(Tenant t) throws SQLException {
		var indicador = UUID.randomUUID();
		try (var c = BancoDeTeste.comoSuperusuario()) {
			executar(c, """
					INSERT INTO indicador (id, tenant_id, codigo, nome, unidade_medida, sentido, periodicidade, origem)
					VALUES (?::uuid, ?::uuid, 'ADESAO_REDE', 'Adesão à rede de esgoto', '%', 'MAIOR_MELHOR',
					        'MENSAL', 'CONTRATUAL')""", indicador, t.id());
			medicao(c, t, indicador, t.concessao(), "2026-07-01", 8_680, 11_400);
			medicao(c, t, indicador, t.concessao(), "2026-08-01", 8_761, 11_400);
			medicao(c, t, indicador, t.cidadeA(), "2026-07-01", 4_900, 5_800);
			medicao(c, t, indicador, t.cidadeA(), "2026-08-01", 4_954, 5_800);
			medicao(c, t, indicador, t.setorA1(), "2026-08-01", 2_966, 3_200);
			medicao(c, t, indicador, t.cidadeB(), "2026-07-01", 3_780, 5_600);
			medicao(c, t, indicador, t.cidadeB(), "2026-08-01", 3_807, 5_600);
			meta(c, t, indicador, t.concessao(), "80", "75", "2026-12-31", "Contrato 001/2025, cláusula 7.2, p. 2");
			meta(c, t, indicador, t.cidadeA(), "85", "80", null, null);
			meta(c, t, indicador, t.cidadeB(), "75", "70", null, null);
		}
	}

	private static void medicao(Connection c, Tenant t, UUID indicador, UUID unidade, String periodo, long ligados,
			long comRede) throws SQLException {
		executar(c, """
				INSERT INTO medicao (tenant_id, indicador_id, unidade_id, periodo_inicio, valor, numerador, denominador,
				                     versao_regra)
				VALUES (?::uuid, ?::uuid, ?::uuid, ?::date, ?::numeric * 100 / ?::numeric, ?::numeric, ?::numeric,
				        'ADESAO_V1')""",
				t.id(), indicador, unidade, periodo, ligados, comRede, ligados, comRede);
	}

	private static void meta(Connection c, Tenant t, UUID indicador, UUID unidade, String valor, String alerta,
			String fim, String origem) throws SQLException {
		executar(c, """
				INSERT INTO meta (id, tenant_id, indicador_id, unidade_id, vigencia_inicio, vigencia_fim, valor_meta,
				                  valor_alerta, origem)
				VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, DATE '2025-01-01', ?::date, ?::numeric, ?::numeric, ?)""",
				UUID.randomUUID(), t.id(), indicador, unidade, fim, valor, alerta, origem);
	}

	public static String rotulo(UUID... ids) {
		var caminho = new StringBuilder();
		for (var id : ids) {
			caminho.append(caminho.isEmpty() ? "" : ".").append('u').append(id.toString().replace("-", ""));
		}
		return caminho.toString();
	}

	private static void unidade(Connection c, Tenant t, UUID id, UUID pai, String tipo, String ibge, String caminho)
			throws SQLException {
		executar(c, """
				INSERT INTO unidade (id, tenant_id, tipo, unidade_pai_id, codigo, nome, caminho, codigo_ibge)
				VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?, ?::ltree, ?)""",
				id, t.id(), tipo, pai, id.toString(), tipo + " " + id, caminho, ibge);
	}

	private static void imovel(Connection c, Tenant t, String matricula, UUID unidade, String situacao)
			throws SQLException {
		executar(c, """
				INSERT INTO imovel_situacao_mensal
				    (tenant_id, competencia, codigo_origem, unidade_id, situacao, fonte_dados_id, execucao_id)
				VALUES (?::uuid, DATE '2026-08-01', ?, ?::uuid, ?, ?::uuid, ?::uuid)""",
				t.id(), matricula, unidade, situacao, t.fonte(), t.execucao());
	}

	private static void executar(Connection c, String sql, Object... parametros) throws SQLException {
		try (var comando = c.prepareStatement(sql)) {
			for (int i = 0; i < parametros.length; i++) {
				comando.setString(i + 1, parametros[i] == null ? null : parametros[i].toString());
			}
			comando.execute();
		}
	}

}
