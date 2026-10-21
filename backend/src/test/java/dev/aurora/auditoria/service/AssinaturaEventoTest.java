package dev.aurora.auditoria.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssinaturaEventoTest {

	private static final byte[] CHAVE = "uma-chave-de-teste-com-mais-de-32-bytes".getBytes();

	private final AssinaturaEvento assinatura = new AssinaturaEvento(CHAVE, JsonMapper.builder().build());

	private final UUID tenant = UUID.randomUUID();

	private final Instant quando = Instant.parse("2026-10-21T12:00:00.123456Z");

	@Test
	void mesmaEntradaMesmoHashIndependenteDaOrdemDosDetalhes() {
		var a = new LinkedHashMap<String, Object>();
		a.put("linhas", 8);
		a.put("formato", "CSV");
		var b = new LinkedHashMap<String, Object>();
		b.put("formato", "CSV");
		b.put("linhas", 8);

		assertThat(assinar(a)).isEqualTo(assinar(b)).hasSize(32);
	}

	@Test
	void qualquerAlteracaoMudaOHash() {
		assertThat(assinar(Map.of("linhas", 8))).isNotEqualTo(assinar(Map.of("linhas", 9)));
	}

	@Test
	void outraChaveOutroHash() {
		var outra = new AssinaturaEvento("outra-chave-de-teste-com-mais-de-32-bytes".getBytes(),
				JsonMapper.builder().build());
		var detalhes = Map.of("linhas", 8);
		assertThat(outra.assinar(new byte[32], tenant, 1, quando, null, "LOGIN", null, detalhes, null))
			.isNotEqualTo(assinar(detalhes));
	}

	@Test
	void recusaChaveCurta() {
		assertThatThrownBy(() -> new AssinaturaEvento("curta".getBytes(), JsonMapper.builder().build()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private byte[] assinar(Map<String, ?> detalhes) {
		return assinatura.assinar(new byte[32], tenant, 1, quando, null, "LOGIN", null, detalhes, null);
	}

}
