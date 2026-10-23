package dev.aurora.integracoes.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.integracoes.model.RegistroLocalizacao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorLocalizacaoTest {

	private final UUID setor = UUID.randomUUID();

	@Test
	void recusaComMotivoSemDerrubarOEnvio() {
		var resultado = ValidadorLocalizacao.validar(
				List.of(new RegistroLocalizacao(1, " 1001 ", "ca-centro", -47.5, -22.6, "endereco"),
						new RegistroLocalizacao(2, "1001", "CA-CENTRO", -47.5, -22.6, "ENDERECO"),
						new RegistroLocalizacao(3, "1002", "OUTRA", -47.5, -22.6, "ENDERECO"),
						new RegistroLocalizacao(4, "1003", "CA-CENTRO", null, -22.6, "ENDERECO"),
						new RegistroLocalizacao(5, "1004", "CA-CENTRO", -47.5, Double.NaN, "ENDERECO"),
						new RegistroLocalizacao(6, "1005", "CA-CENTRO", -47.5, -22.6, "MANUAL"),
						new RegistroLocalizacao(7, "10;06", "CA-CENTRO", -47.5, -22.6, "BAIRRO")),
				Map.of("CA-CENTRO", setor));

		assertThat(resultado.validas())
			.containsExactly(new ValidadorLocalizacao.Valida("1001", setor, -47.5, -22.6, "ENDERECO"));
		assertThat(resultado.rejeitados()).isEqualTo(6);
		assertThat(resultado.rejeicoes()).extracting(r -> r.linha()).containsExactly(2, 3, 4, 5, 6, 7);
		assertThat(resultado.rejeicoes().get(0).motivo()).isEqualTo("Matrícula repetida neste envio.");
		// Ponto "manual" é de quem corrige em campo, não do geocódigo do agente.
		assertThat(resultado.rejeicoes().get(4).motivo()).startsWith("Precisão inválida");
	}

}
