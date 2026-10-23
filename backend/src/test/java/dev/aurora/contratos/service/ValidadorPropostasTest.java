package dev.aurora.contratos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.contratos.model.Pagina;
import dev.aurora.contratos.model.Proposta;
import dev.aurora.contratos.model.TipoItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorPropostasTest {

	private static final List<Pagina> PAGINAS = List.of(new Pagina(1, "Preâmbulo"),
			new Pagina(2, "7.2 A adesão mínima da Concessão A\nserá de 80% até 31/12/2026, com alerta em 75%."),
			new Pagina(3, "8.1 Concluir a ETE Norte até 30/06/2027."));

	private static final UUID CONCESSAO = UUID.randomUUID();

	@Test
	void aceitaTrechoLiteralComQuebrasEAcentosDiferentes() {
		var r = validar(meta("A ADESAO minima da Concessão A será de 80%", 2, "80", "75", "Concessão A"));
		assertThat(r.descartados()).isZero();
		var dados = r.validos().getFirst().dados();
		assertThat(dados.tipo()).isEqualTo(TipoItem.META);
		assertThat(dados.unidadeId()).isEqualTo(CONCESSAO);
		assertThat(dados.valorReferencia()).isEqualByComparingTo("80");
		assertThat(dados.valorAlerta()).isEqualByComparingTo("75");
	}

	@Test
	void descartaTrechoInventadoPaginaInexistenteETipoDesconhecido() {
		var r = validar(meta("A adesão mínima será de 95% em 2025", 2, "95", null, null),
				meta("Concluir a ETE Norte até 30/06/2027", 9, null, null, null), new Proposta("BONUS", "x", "x", "8.1",
						3, "Concluir a ETE Norte até 30/06/2027", null, null, null, null, null, null, null, null));
		assertThat(r.validos()).isEmpty();
		assertThat(r.descartados()).isEqualTo(3);
	}

	@Test
	void trechoPodeContinuarNaPaginaSeguinte() {
		assertThat(ValidadorPropostas.trechoNoTexto("alerta em 75%. 8.1 Concluir a ETE", 2, PAGINAS)).isTrue();
	}

	@Test
	void limpaPercentuaisForaDeFaixaEUnidadeDesconhecida() {
		var r = validar(meta("será de 80% até 31/12/2026", 2, "180", "90", "Cidade Z"));
		var dados = r.validos().getFirst().dados();
		assertThat(dados.valorReferencia()).isNull();
		assertThat(dados.valorAlerta()).isEqualByComparingTo("90");
		assertThat(dados.unidadeId()).isNull();

		var alertaMaior = validar(meta("será de 80% até 31/12/2026", 2, "80", "85", null)).validos().getFirst().dados();
		assertThat(alertaMaior.valorAlerta()).isNull();
	}

	private static ValidadorPropostas.Resultado validar(Proposta... propostas) {
		return ValidadorPropostas.validar(List.of(propostas), PAGINAS,
				Map.of(ValidadorPropostas.normalizar("Concessão A"), CONCESSAO));
	}

	@Test
	void comOcrAceitaPoucosErrosDeReconhecimentoESemOcrNao() {
		var paginas = List
			.of(new Pagina(1, "7.2 A adesão mínima ao sistema de esgoto no Setor Aí\nserá de 85% até 2030."));
		var trecho = "A adesao minima ao sistema de esgoto no Setor A1 sera de 85%";
		assertThat(ValidadorPropostas.trechoNoTexto(trecho, 1, paginas, false)).isFalse();
		assertThat(ValidadorPropostas.trechoNoTexto(trecho, 1, paginas, true)).isTrue();
		// Trecho inventado continua recusado, mesmo com tolerância.
		assertThat(
				ValidadorPropostas.trechoNoTexto("A adesao minima no Setor B2 sera de 99% ate 2040", 1, paginas, true))
			.isFalse();
		assertThat(ValidadorPropostas.menorDistancia("setor a1", "no setor ai sera")).isEqualTo(1);
	}

	private static Proposta meta(String trecho, int pagina, String valor, String alerta, String unidade) {
		return new Proposta("meta", "Adesão mínima", "Meta de adesão", "7.2", pagina, trecho,
				valor == null ? null : new BigDecimal(valor), "%", LocalDate.of(2026, 12, 31), null, unidade,
				alerta == null ? null : new BigDecimal(alerta), LocalDate.of(2025, 1, 1), new BigDecimal("1.4"));
	}

}
