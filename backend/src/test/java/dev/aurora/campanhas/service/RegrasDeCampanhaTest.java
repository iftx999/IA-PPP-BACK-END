package dev.aurora.campanhas.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import dev.aurora.adesao.IndicadorDaUnidade;
import dev.aurora.adesao.Situacao;
import dev.aurora.campanhas.model.MotivoResultado;
import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.campanhas.model.RegistroResultado;
import dev.aurora.campanhas.model.ResultadoOrdem;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.unidades.Unidade;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RN-19: prioridade, impacto na meta e validação do resultado de campo. */
class RegrasDeCampanhaTest {

	private static final LocalDate HOJE = LocalDate.of(2026, 11, 2);

	private static final Unidade UNIDADE = new Unidade(UUID.randomUUID(), "MUNICIPIO", UUID.randomUUID(), "C", "Cidade",
			"3526902");

	@Test
	void prioridadePeloSemaforo() {
		assertThat(CampanhaService.prioridade(Situacao.ABAIXO_DO_ALERTA)).isEqualTo(Prioridade.ALTA);
		assertThat(CampanhaService.prioridade(Situacao.ATENCAO)).isEqualTo(Prioridade.MEDIA);
		assertThat(CampanhaService.prioridade(Situacao.META_ATINGIDA)).isEqualTo(Prioridade.BAIXA);
		assertThat(CampanhaService.prioridade(Situacao.SEM_META)).isEqualTo(Prioridade.BAIXA);
	}

	@Test
	void impactoNaMeta() {
		// 700 de 1.000 (70%); meta 75% = 750 → faltam 50; 80 novas ligações → 78%.
		var impacto = CampanhaService.impacto(UNIDADE, YearMonth.of(2026, 8), indicador(700, 1000, "75"), 80);
		assertThat(impacto.adesao()).isEqualByComparingTo("70.00");
		assertThat(impacto.adesaoSeTodasLigarem()).isEqualByComparingTo("78.00");
		assertThat(impacto.faltamParaMeta()).isEqualTo(50);
		// Meta fracionária arredonda para cima; nunca passa de 100%; meta atingida = 0.
		assertThat(CampanhaService.impacto(UNIDADE, null, indicador(700, 1000, "70.01"), 0).faltamParaMeta())
			.isEqualTo(1);
		assertThat(CampanhaService.impacto(UNIDADE, null, indicador(990, 1000, "75"), 50).adesaoSeTodasLigarem())
			.isEqualByComparingTo("100.00");
		assertThat(CampanhaService.impacto(UNIDADE, null, indicador(990, 1000, "75"), 0).faltamParaMeta()).isZero();
		// Sem meta ou sem medição.
		assertThat(CampanhaService.impacto(UNIDADE, null, indicador(700, 1000, null), 0).faltamParaMeta()).isNull();
		var semMedicao = CampanhaService.impacto(UNIDADE, null, null, 10);
		assertThat(semMedicao.adesao()).isNull();
		assertThat(semMedicao.comRede()).isZero();
	}

	@Test
	void resultadoExigeMotivoCompativel() {
		assertThatThrownBy(() -> validar(ResultadoOrdem.RECUSADA, null, null, null))
			.isInstanceOf(RegraVioladaException.class)
			.hasMessageContaining("recusa");
		assertThatThrownBy(() -> validar(ResultadoOrdem.INVIAVEL, MotivoResultado.SEM_INTERESSE, null, null))
			.hasMessageContaining("inviável");
		assertThatThrownBy(() -> validar(ResultadoOrdem.INVIAVEL, MotivoResultado.OUTRO, "  ", null))
			.hasMessageContaining("observação");
		var outro = validar(ResultadoOrdem.INVIAVEL, MotivoResultado.OUTRO, " rua sem acesso ", null);
		assertThat(outro.observacao()).isEqualTo("rua sem acesso");
		// Executada descarta motivo e data enviados por engano.
		var executada = validar(ResultadoOrdem.EXECUTADA, MotivoResultado.CUSTO_DA_LIGACAO, null, HOJE);
		assertThat(executada.motivo()).isNull();
		assertThat(executada.reagendadaPara()).isNull();
		assertThatThrownBy(() -> validar(ResultadoOrdem.EXECUTADA, null, "x".repeat(501), null))
			.hasMessageContaining("500");
	}

	@Test
	void reagendamentoEntreHojeE180Dias() {
		assertThatThrownBy(() -> validar(ResultadoOrdem.REAGENDADA, null, null, null))
			.isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> validar(ResultadoOrdem.REAGENDADA, null, null, HOJE.minusDays(1)))
			.isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> validar(ResultadoOrdem.REAGENDADA, null, null, HOJE.plusDays(181)))
			.isInstanceOf(RegraVioladaException.class);
		assertThat(validar(ResultadoOrdem.REAGENDADA, null, null, HOJE).reagendadaPara()).isEqualTo(HOJE);
		assertThat(validar(ResultadoOrdem.REAGENDADA, null, null, HOJE.plusDays(180)).reagendadaPara())
			.isEqualTo(HOJE.plusDays(180));
	}

	private static RegistroResultado validar(ResultadoOrdem resultado, MotivoResultado motivo, String observacao,
			LocalDate data) {
		return OrdemServicoService.validar(new RegistroResultado(resultado, motivo, observacao, data), HOJE);
	}

	private static IndicadorDaUnidade indicador(long ligados, long comRede, String meta) {
		return new IndicadorDaUnidade(UNIDADE.id(), YearMonth.of(2026, 8), ligados, comRede, null, null, null,
				meta == null ? null : new BigDecimal(meta), null, null, null, null, null, Situacao.SEM_META, 0, 0);
	}

}
