package dev.aurora.contratos.service;

import java.util.List;

import dev.aurora.contratos.model.Pagina;
import dev.aurora.contratos.model.Trecho;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentadorTest {

	@Test
	void separaPorClausulaMantendoAPaginaDeInicio() {
		var trechos = Segmentador.segmentar(List.of(new Pagina(1, """
				CONTRATO DE CONCESSÃO Nº 001/2025
				Partes e objeto."""), new Pagina(2, """
				CLÁUSULA SÉTIMA - DAS METAS
				7.1 A concessionária manterá o cadastro.
				7.2 - A adesão mínima será de 80%
				até 31/12/2026."""), new Pagina(3, """
				continuação da 7.2
				8. PRAZOS""")));

		assertThat(trechos).extracting(Trecho::clausula, Trecho::pagina)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(null, 1),
					org.assertj.core.groups.Tuple.tuple("SÉTIMA", 2), org.assertj.core.groups.Tuple.tuple("7.1", 2),
					org.assertj.core.groups.Tuple.tuple("7.2", 2), org.assertj.core.groups.Tuple.tuple("8", 3));
		assertThat(trechos.get(3).texto()).contains("até 31/12/2026").contains("continuação da 7.2");
	}

	@Test
	void naoConfundeValoresComNumeracao() {
		assertThat(Segmentador.clausulaDaLinha("80% dos imóveis")).isNull();
		// Ano no início da linha não é cláusula (numeração tem até 3 dígitos por nível).
		assertThat(Segmentador.clausulaDaLinha("2026 será o ano")).isNull();
		assertThat(Segmentador.clausulaDaLinha("12.1.3) Multa")).isEqualTo("12.1.3");
		assertThat(Segmentador.clausulaDaLinha("Cláusula 9: Reajuste")).isEqualTo("9");
	}

	@Test
	void quebraTrechosMuitoLongos() {
		var linha = "texto ".repeat(20) + "\n";
		var trechos = Segmentador.segmentar(List.of(new Pagina(1, linha.repeat(200))));
		assertThat(trechos).hasSizeGreaterThan(1)
			.allMatch(t -> t.texto().length() <= Segmentador.TAMANHO_MAXIMO + linha.length());
	}

}
