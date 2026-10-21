package dev.aurora.comum;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EscritorCsvTest {

	@Test
	void geraCsvParaExcelComBomESeparadorPontoEVirgula() {
		var bytes = new ByteArrayOutputStream();
		try (var csv = new EscritorCsv(bytes)) {
			csv.linha("matricula", "unidade");
			csv.linha("2001763", "Cidade A; Centro");
		}
		assertThat(bytes.toString(StandardCharsets.UTF_8))
			.isEqualTo("﻿matricula;unidade\r\n2001763;\"Cidade A; Centro\"\r\n");
	}

	@Test
	void neutralizaFormulas() {
		assertThat(EscritorCsv.celula("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
		assertThat(EscritorCsv.celula("+5511")).isEqualTo("'+5511");
		assertThat(EscritorCsv.celula("@SUM(A1)")).isEqualTo("'@SUM(A1)");
		assertThat(EscritorCsv.celula(-3)).isEqualTo("-3");
		assertThat(EscritorCsv.celula(null)).isEmpty();
	}

}
