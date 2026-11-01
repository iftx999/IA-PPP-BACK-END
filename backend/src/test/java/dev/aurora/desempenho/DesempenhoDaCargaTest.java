package dev.aurora.desempenho;

import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.adesao.Medicoes;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.integracoes.model.RegistroCarga;
import dev.aurora.integracoes.service.CargaService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Metas de escrita do doc 09 (9.9; ADR 0034): carga mensal de ~1 milhão de imóveis em
 * menos de 10 min e recálculo da adesão em menos de 2 min. Fora do {@code verify} (tag
 * {@code desempenho}):
 * {@code ./mvnw test -Dtestes.excluidos= -Dgroups=desempenho -Dtest=DesempenhoDaCargaTest}.
 * Quantidade em {@code -Ddesempenho.imoveis} (padrão 1.000.000).
 */
@Tag("desempenho")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DesempenhoDaCargaTest {

	private static final Logger LOG = LoggerFactory.getLogger(DesempenhoDaCargaTest.class);

	private static final int IMOVEIS = Integer.getInteger("desempenho.imoveis", 1_000_000);

	private static final int SETORES = 400;

	/** Limite por chamada do agente (CargaService.MAXIMO_DE_REGISTROS). */
	private static final int LOTE = 500_000;

	private static final YearMonth COMPETENCIA = YearMonth.of(2026, 9);

	@Autowired
	CargaService cargas;

	@Autowired
	Medicoes medicoes;

	@Autowired
	ContextosDoSistema contextos;

	@Test
	void cargaMensalERecalculoDentroDasMetas() throws Exception {
		var t = DadosDeTeste.criarTenant("Desempenho");
		var codigos = criarSetores(t);
		var contexto = contextos.doTenant(t.id()).orElseThrow();

		var registros = new ArrayList<RegistroCarga>(IMOVEIS);
		for (int i = 0; i < IMOVEIS; i++) {
			// 80% ligados, 15% factíveis, 4% cortados, 1% sem rede.
			var faixa = i % 100;
			var situacao = faixa < 80 ? "LIGADO" : faixa < 95 ? "FACTIVEL" : faixa < 99 ? "CORTADO" : "POTENCIAL";
			registros.add(new RegistroCarga(i + 2, "M" + i, codigos.get(i % SETORES), situacao,
					"CORTADO".equals(situacao) ? "INADIMPLENCIA" : null));
		}

		var inicio = System.nanoTime();
		long gravados = 0;
		for (int i = 0; i < IMOVEIS; i += LOTE) {
			var lote = List.copyOf(registros.subList(i, Math.min(IMOVEIS, i + LOTE)));
			var resultado = ContextoAcesso.executar(contexto,
					() -> cargas.carregar(t.fonte(), COMPETENCIA, lote, CargaService.Origem.AGENTE, "desempenho"));
			gravados += resultado.gravados();
		}
		var carga = Duration.ofNanos(System.nanoTime() - inicio);

		inicio = System.nanoTime();
		var unidades = ContextoAcesso.executar(contexto, () -> medicoes.recalcular(COMPETENCIA));
		var recalculo = Duration.ofNanos(System.nanoTime() - inicio);

		LOG.info("DESEMPENHO carga de {} imóveis em {} setores: {} s ({} imóveis/s); recálculo de {} unidades: {} ms",
				IMOVEIS, SETORES, carga.toMillis() / 1000.0, Math.round(IMOVEIS / (carga.toMillis() / 1000.0)),
				unidades, recalculo.toMillis());
		assertThat(gravados).isEqualTo(IMOVEIS);
		assertThat(carga).isLessThan(Duration.ofMinutes(10));
		assertThat(recalculo).isLessThan(Duration.ofMinutes(2));
	}

	/** Setores comerciais divididos entre as duas cidades do tenant de teste. */
	private static List<String> criarSetores(Tenant t) throws Exception {
		var codigos = new ArrayList<String>();
		try (var c = BancoDeTeste.comoSuperusuario(); var inserir = c.prepareStatement("""
				INSERT INTO unidade (id, tenant_id, tipo, unidade_pai_id, codigo, nome, caminho)
				VALUES (?, ?, 'SETOR_COMERCIAL', ?, ?, ?, ?::ltree)""")) {
			for (int i = 0; i < SETORES; i++) {
				var id = UUID.randomUUID();
				var cidade = i % 2 == 0 ? t.cidadeA() : t.cidadeB();
				var codigo = "SETOR-" + i;
				inserir.setObject(1, id);
				inserir.setObject(2, t.id());
				inserir.setObject(3, cidade);
				inserir.setString(4, codigo);
				inserir.setString(5, "Setor " + i);
				inserir.setString(6, DadosDeTeste.rotulo(t.grupo(), t.concessao(), cidade, id));
				inserir.addBatch();
				codigos.add(codigo);
			}
			inserir.executeBatch();
		}
		return codigos;
	}

}
