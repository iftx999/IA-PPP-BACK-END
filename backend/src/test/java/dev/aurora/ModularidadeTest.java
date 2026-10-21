package dev.aurora;

import org.junit.jupiter.api.Test;

import org.springframework.modulith.core.ApplicationModules;

/**
 * Fronteiras entre módulos (pacotes diretos de dev.aurora) verificadas pelo Spring
 * Modulith.
 */
class ModularidadeTest {

	@Test
	void modulosRespeitamAsFronteiras() {
		ApplicationModules.of(AuroraApiApplication.class).verify();
	}

}
