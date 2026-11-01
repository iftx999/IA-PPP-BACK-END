package dev.aurora.acesso.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.contexto.ContextoPlataforma;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Aplica o {@link ContextoAcesso} (ou o {@link ContextoPlataforma}) atual em cada conexão
 * retirada do pool, definindo as variáveis que as políticas de RLS leem. Sem contexto, as
 * variáveis ficam vazias e {@code app_tenant()} falha: fechado por padrão.
 */
class ContextoDataSource extends DelegatingDataSource {

	private static final String DEFINIR = """
			SELECT set_config('app.tenant_id', ?, false),
			       set_config('app.usuario_id', ?, false),
			       set_config('app.escopos', ?, false),
			       set_config('app.plataforma', ?, false)""";

	ContextoDataSource(DataSource alvo) {
		super(alvo);
	}

	@Override
	public Connection getConnection() throws SQLException {
		return aplicarContexto(super.getConnection());
	}

	@Override
	public Connection getConnection(String usuario, String senha) throws SQLException {
		return aplicarContexto(super.getConnection(usuario, senha));
	}

	private static Connection aplicarContexto(Connection conexao) throws SQLException {
		var contexto = ContextoAcesso.atual().orElse(null);
		try (var comando = conexao.prepareStatement(DEFINIR)) {
			comando.setString(1, contexto == null ? "" : contexto.tenantId().toString());
			comando.setString(2,
					contexto == null || contexto.usuarioId() == null ? "" : contexto.usuarioId().toString());
			comando.setString(3, contexto == null ? "" : contexto.escopos().stream().collect(Collectors.joining(",")));
			// Plataforma só sem cliente no contexto: dentro de um cliente valem as regras
			// dele.
			comando.setString(4, contexto == null && ContextoPlataforma.atual().isPresent() ? "on" : "");
			comando.execute();
			return conexao;
		}
		catch (SQLException erro) {
			conexao.close();
			throw erro;
		}
	}

}
