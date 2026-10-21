package dev.aurora.acesso.repository;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.DadosDeTeste;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.ContextoAcesso;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Primeiro login: várias requisições simultâneas vinculam o mesmo sub sem que nenhuma
 * seja recusada.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VinculoPorEmailTest {

	@Autowired
	UsuarioRepository usuarios;

	@Test
	void vinculoSimultaneoEhIdempotente() throws Exception {
		var t = DadosDeTeste.criarTenant("Vínculo");
		var id = DadosDeTeste.criarUsuario(t, null, "convidado@vinculo.teste", t.cidadeA());
		var contexto = ContextoAcesso.somenteTenant(t.id());
		var sub = "sub-" + UUID.randomUUID();

		var largada = new CountDownLatch(1);
		var resultados = new ArrayList<Future<Optional<UUID>>>();
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < 8; i++) {
				Callable<Optional<UUID>> vincular = () -> {
					largada.await();
					return ContextoAcesso.executar(contexto,
							() -> usuarios.vincularSubPorEmail(sub, "Convidado@vinculo.teste"));
				};
				resultados.add(executor.submit(vincular));
			}
			largada.countDown();
			for (var resultado : resultados) {
				assertThat(resultado.get()).contains(id);
			}
		}
		// Outro sub não "rouba" um usuário já vinculado.
		assertThat(ContextoAcesso.executar(contexto,
				() -> usuarios.vincularSubPorEmail("sub-intruso", "convidado@vinculo.teste")))
			.isEmpty();
	}

}
