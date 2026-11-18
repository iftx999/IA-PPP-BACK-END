package dev.aurora.servicos.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RN-21, P6: feriados nacionais calculados e dias úteis do prazo (ADR 0044). */
class CalendarioTest {

	@ParameterizedTest
	@CsvSource({ "1818-03-22", "1886-04-25", "1900-04-15", "1943-04-25", "1961-04-02", "1974-04-14", "1990-04-15",
			"2000-04-23", "2002-03-31", "2005-03-27", "2008-03-23", "2011-04-24", "2016-03-27", "2019-04-21",
			"2024-03-31", "2025-04-20", "2026-04-05", "2027-03-28", "2030-04-21", "2038-04-25", "2100-03-28",
			"2285-03-22" })
	void pascoa(LocalDate domingo) {
		assertThat(Calendario.pascoa(domingo.getYear())).isEqualTo(domingo);
	}

	@Test
	void pascoaSempreNumDomingoEntre22DeMarcoE25DeAbril() {
		for (int ano = 1583; ano <= 4099; ano++) {
			var p = Calendario.pascoa(ano);
			assertThat(p.getDayOfWeek()).as("%d", ano).isEqualTo(DayOfWeek.SUNDAY);
			assertThat(p).as("%d", ano).isBetween(LocalDate.of(ano, 3, 22), LocalDate.of(ano, 4, 25));
		}
	}

	@Test
	void nacionais() {
		assertThat(Calendario.nacionais(2026)).hasSize(10).containsKey(LocalDate.of(2026, 4, 3));
		assertThat(Calendario.feriadoNacional(LocalDate.of(2026, 4, 3))).as("Sexta-feira Santa").isTrue();
		assertThat(Calendario.feriadoNacional(LocalDate.of(2026, 11, 20))).isTrue();
		// Consciência Negra é nacional a partir de 2024 (Lei 14.759/2023).
		assertThat(Calendario.feriadoNacional(LocalDate.of(2023, 11, 20))).isFalse();
		assertThat(Calendario.nacionais(2023)).hasSize(9);
		assertThat(Calendario.nacionais(2024)).hasSize(10);
		assertThat(Calendario.feriadoNacional(LocalDate.of(2024, 11, 20))).as("primeiro ano").isTrue();
		assertThat(Calendario.feriadoNacional(LocalDate.of(2026, 11, 19))).isFalse();
		// Carnaval e Corpus Christi são pontos facultativos.
		assertThat(Calendario.feriadoNacional(LocalDate.of(2026, 2, 17))).isFalse();
		assertThat(Calendario.feriadoNacional(LocalDate.of(2026, 6, 4))).isFalse();
	}

	@Test
	void diasUteis() {
		var sexta = LocalDate.of(2026, 11, 13);
		// Sábado e domingo não contam; o dia de início também não.
		assertThat(Calendario.somarDiasUteis(sexta, 1, d -> false)).isEqualTo(LocalDate.of(2026, 11, 16));
		// Segunda 16 é feriado local; terça 17, quarta 18, quinta 19; sexta 20 nacional.
		var local = Set.of(LocalDate.of(2026, 11, 16));
		assertThat(Calendario.somarDiasUteis(sexta, 4, local::contains)).isEqualTo(LocalDate.of(2026, 11, 23));
		assertThat(Calendario.util(LocalDate.of(2026, 11, 14), d -> false)).isFalse();
		assertThatThrownBy(() -> Calendario.somarDiasUteis(sexta, 0, d -> false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ao menos 1");
		assertThatThrownBy(() -> Calendario.somarDiasUteis(sexta, 2, d -> true))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
