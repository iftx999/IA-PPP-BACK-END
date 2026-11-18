package dev.aurora.servicos.model;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RN-21, P6: feriados nacionais calculados e dias úteis do prazo (ADR 0044). */
class CalendarioTest {

	@Test
	void pascoa() {
		assertThat(Calendario.pascoa(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
		assertThat(Calendario.pascoa(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
		assertThat(Calendario.pascoa(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
		assertThat(Calendario.pascoa(2038)).isEqualTo(LocalDate.of(2038, 4, 25));
		assertThat(Calendario.pascoa(2008)).isEqualTo(LocalDate.of(2008, 3, 23));
	}

	@Test
	void nacionais() {
		assertThat(Calendario.nacionais(2026)).hasSize(10).containsKey(LocalDate.of(2026, 4, 3));
		assertThat(Calendario.feriadoNacional(LocalDate.of(2026, 4, 3))).as("Sexta-feira Santa").isTrue();
		assertThat(Calendario.feriadoNacional(LocalDate.of(2026, 11, 20))).isTrue();
		// Consciência Negra é nacional a partir de 2024 (Lei 14.759/2023).
		assertThat(Calendario.feriadoNacional(LocalDate.of(2023, 11, 20))).isFalse();
		assertThat(Calendario.nacionais(2023)).hasSize(9);
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
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Calendario.somarDiasUteis(sexta, 2, d -> true))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
