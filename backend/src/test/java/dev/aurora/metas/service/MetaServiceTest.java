package dev.aurora.metas.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.comum.ConflitoException;
import dev.aurora.metas.model.MetaDaUnidade;
import dev.aurora.metas.repository.MetaRepository;
import dev.aurora.unidades.Unidades;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetaServiceTest {

	private final MetaRepository repositorio = mock(MetaRepository.class);

	private final MetaService servico = new MetaService(repositorio, mock(Unidades.class), mock(Auditoria.class),
			Clock.systemDefaultZone());

	@Test
	void metaDeContratoSoMudaPorAditivo() {
		var id = UUID.randomUUID();
		when(repositorio.buscarParaAlterar(id))
			.thenReturn(Optional.of(new MetaDaUnidade(id, new BigDecimal("80"), new BigDecimal("75"),
					LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31), "Contrato 001/2025", true)));

		assertThatThrownBy(() -> servico.encerrar(id, LocalDate.of(2026, 10, 31)))
			.isInstanceOf(ConflitoException.class);
		verify(repositorio, never()).encerrar(any(), any());
	}

}
