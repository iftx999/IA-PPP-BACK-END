package dev.aurora.auditoria.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.ContextoAcesso;
import dev.aurora.auditoria.TipoEvento;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TrilhaDeAuditoriaTest {

	@Autowired
	AuditoriaService auditoria;

	@Test
	void cadeiaIntegraEAdulteracaoDetectada() throws Exception {
		var t = DadosDeTeste.criarTenant("Auditoria");
		var contexto = contexto(t);

		ContextoAcesso.executar(contexto, () -> {
			auditoria.registrar(TipoEvento.RELATORIO_EXPORTADO, "imoveis/sem-ligacao",
					Map.of("formato", "CSV", "linhas", 2639));
			auditoria.registrar(TipoEvento.ACESSO_DADO_PESSOAL, "imoveis/sem-ligacao", Map.of("pagina", 1));
			return null;
		});
		var integra = ContextoAcesso.executar(contexto, auditoria::verificar);
		assertThat(integra.eventos()).isEqualTo(2);
		assertThat(integra.verificados()).isEqualTo(2);
		assertThat(integra.integra()).isTrue();

		// Alguém com acesso direto ao banco muda a quantidade de linhas exportadas.
		try (var c = BancoDeTeste.comoSuperusuario(); var comando = c.prepareStatement("""
				UPDATE evento_auditoria SET detalhes = jsonb_set(detalhes, '{linhas}', '10')
				 WHERE tenant_id = ?::uuid AND sequencia = 1""")) {
			comando.setString(1, t.id().toString());
			comando.execute();
		}
		var adulterada = ContextoAcesso.executar(contexto, auditoria::verificar);
		assertThat(adulterada.integra()).isFalse();
		assertThat(adulterada.primeiraQuebra()).isEqualTo(1L);
	}

	@Test
	void aplicacaoNaoAlteraNemApagaEventos() throws Exception {
		var t = DadosDeTeste.criarTenant("Somente inserção");
		ContextoAcesso.executar(contexto(t), () -> {
			auditoria.registrar(TipoEvento.LOGIN, "usuario/x", Map.of());
			return null;
		});
		try (var c = BancoDeTeste.comoAplicacao(); var consulta = c.createStatement()) {
			consulta.execute("SELECT set_config('app.tenant_id', '" + t.id() + "', false)");
			assertThatThrownBy(() -> consulta.execute("UPDATE evento_auditoria SET tipo = 'X'"))
				.hasMessageContaining("permission denied");
			assertThatThrownBy(() -> consulta.execute("DELETE FROM evento_auditoria"))
				.hasMessageContaining("permission denied");
		}
	}

	private static ContextoAcesso contexto(Tenant t) {
		return new ContextoAcesso(t.id(), null, Set.of(), List.of(DadosDeTeste.rotulo(t.grupo())));
	}

}
