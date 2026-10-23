package dev.aurora.contexto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextoAcessoTest {

	private final ContextoAcesso contexto = new ContextoAcesso(UUID.randomUUID(), UUID.randomUUID(),
			Map.of("indicador:ler", List.of("g.c"), "indicador:gerenciar", List.of("g.c.b")), List.of("g.c"));

	@Test
	void restringeOEscopoAsUnidadesDaPermissao() {
		assertThat(contexto.restritoA("indicador:gerenciar").escopos()).containsExactly("g.c.b");
		assertThat(contexto.restritoA("usuario:gerenciar").escopos()).isEmpty();
		assertThat(contexto.permissoes()).containsExactlyInAnyOrder("indicador:ler", "indicador:gerenciar");
	}

	@Test
	void podeEmConsideraDescendentesENaoPrefixosDeRotulo() {
		assertThat(contexto.podeEm("indicador:gerenciar", "g.c.b.s1")).isTrue();
		assertThat(contexto.podeEm("indicador:gerenciar", "g.c.b")).isTrue();
		assertThat(contexto.podeEm("indicador:gerenciar", "g.c.bx")).isFalse();
		assertThat(contexto.podeEm("indicador:gerenciar", "g.c")).isFalse();
		assertThat(contexto.podeEm("nao:tem", "g")).isFalse();
	}

	@Test
	void restritoATodasFicaComAIntersecaoDasSubarvores() {
		var varias = new ContextoAcesso(UUID.randomUUID(), UUID.randomUUID(),
				Map.of("assistente:usar", List.of("g.c", "g.x"), "indicador:ler", List.of("g.c.b", "g.y", "g.x")),
				List.of("g"));
		assertThat(varias.restritoATodas("assistente:usar", "indicador:ler").escopos())
			.containsExactlyInAnyOrder("g.c.b", "g.x");
		assertThat(varias.restritoATodas("assistente:usar", "nao:tem").escopos()).isEmpty();
		assertThat(varias.restritoATodas("indicador:ler").escopos()).containsExactly("g.c.b", "g.y", "g.x");
	}

	@Test
	void trocarDevolveOAnteriorERestaura() {
		var anterior = ContextoAcesso.trocar(contexto);
		assertThat(ContextoAcesso.exigir()).isSameAs(contexto);
		ContextoAcesso.trocar(anterior);
		assertThat(ContextoAcesso.atual()).isEqualTo(java.util.Optional.ofNullable(anterior));
	}

}
