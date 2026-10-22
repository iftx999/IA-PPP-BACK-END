package dev.aurora.integracoes.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.integracoes.model.RegistroCarga;
import dev.aurora.integracoes.model.Rejeicao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorCargaTest {

	private static final UUID SETOR = UUID.randomUUID();

	@Test
	void aceitaRotulosERecusaComMotivo() {
		var r = ValidadorCarga.validar(List.of(new RegistroCarga(2, " 1001 ", "ca-centro", "Factível"),
				new RegistroCarga(3, "1002", "CA-CENTRO", "sem rede"),
				new RegistroCarga(4, "1001", "CA-CENTRO", "LIGADO"), new RegistroCarga(5, "1003", "XX", "LIGADO"),
				new RegistroCarga(6, "1004", "CA-CENTRO", "talvez"), new RegistroCarga(7, "", "CA-CENTRO", "LIGADO"),
				new RegistroCarga(8, "a b", "CA-CENTRO", "LIGADO")), Map.of("CA-CENTRO", SETOR));

		assertThat(r.validos()).extracting(ValidadorCarga.Valido::situacao).containsExactly("FACTIVEL", "POTENCIAL");
		assertThat(r.validos().getFirst().matricula()).isEqualTo("1001");
		assertThat(r.rejeitados()).isEqualTo(5);
		assertThat(r.rejeicoes()).extracting(Rejeicao::linha).containsExactly(4, 5, 6, 7, 8);
		assertThat(r.rejeicoes().get(0).motivo()).isEqualTo("Matrícula repetida nesta carga.");
		assertThat(r.rejeicoes().get(1).motivo()).isEqualTo("Unidade desconhecida ou inativa: XX.");
		assertThat(r.rejeicoes().get(2).motivo()).startsWith("Situação inválida: talvez");
	}

	@Test
	void detalhaNoMaximoCemRecusasMasContaTodas() {
		var registros = new java.util.ArrayList<RegistroCarga>();
		for (int i = 0; i < 150; i++) {
			registros.add(new RegistroCarga(i + 2, "M" + i, "NAO-EXISTE", "LIGADO"));
		}
		var r = ValidadorCarga.validar(registros, Map.of());
		assertThat(r.rejeitados()).isEqualTo(150);
		assertThat(r.rejeicoes()).hasSize(ValidadorCarga.REJEICOES_DETALHADAS);
	}

	@Test
	void leCsvComBomPontoEVirgulaAspasEColunasEmOutraOrdem() throws Exception {
		var csv = "﻿situacao;Matricula;unidade\nLIGADO;\"10;01\";CA-CENTRO\n\nfactivel;1002;CA-CENTRO\n";
		var registros = LeitorCsvCarga.ler(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
		assertThat(registros).containsExactly(new RegistroCarga(2, "10;01", "CA-CENTRO", "LIGADO"),
				new RegistroCarga(4, "1002", "CA-CENTRO", "factivel"));

		var virgula = LeitorCsvCarga.ler(new ByteArrayInputStream("matricula,unidade,situacao\n1,X,LIGADO".getBytes()));
		assertThat(virgula.getFirst().unidade()).isEqualTo("X");

		assertThatThrownBy(() -> LeitorCsvCarga.ler(new ByteArrayInputStream("codigo;setor\n1;2".getBytes())))
			.isInstanceOf(RegraVioladaException.class)
			.hasMessageContaining("situacao");
	}

}
