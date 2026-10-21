package dev.aurora;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Banco de teste compartilhado: a mesma imagem de infra/postgres (PostGIS, pgvector, papéis aurora_owner e aurora_app),
 * com as migrações aplicadas uma vez.
 */
public final class BancoDeTeste {

	public static final String SENHA_SUPERUSUARIO = "postgres-teste";

	public static final String SENHA_OWNER = "owner-teste";

	public static final String SENHA_APP = "app-teste";

	private static GenericContainer<?> container;

	private BancoDeTeste() {
	}

	public static synchronized String url() {
		if (container == null) {
			container = new GenericContainer<>(new ImageFromDockerfile("aurora-postgres-teste", false)
				.withFileFromPath(".", Path.of("..", "infra", "postgres").toAbsolutePath().normalize()))
				.withEnv("POSTGRES_PASSWORD", SENHA_SUPERUSUARIO)
				.withEnv("AURORA_OWNER_PASSWORD", SENHA_OWNER)
				.withEnv("AURORA_APP_PASSWORD", SENHA_APP)
				.withExposedPorts(5432)
				.waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2)
					.withStartupTimeout(Duration.ofMinutes(3)));
			container.start();
			Flyway.configure()
				.dataSource(jdbcUrl(), "aurora_owner", SENHA_OWNER)
				.load()
				.migrate();
		}
		return jdbcUrl();
	}

	public static Connection comoSuperusuario() throws SQLException {
		return DriverManager.getConnection(url(), "postgres", SENHA_SUPERUSUARIO);
	}

	public static Connection comoAplicacao() throws SQLException {
		return DriverManager.getConnection(url(), "aurora_app", SENHA_APP);
	}

	private static String jdbcUrl() {
		return "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/aurora";
	}

}
