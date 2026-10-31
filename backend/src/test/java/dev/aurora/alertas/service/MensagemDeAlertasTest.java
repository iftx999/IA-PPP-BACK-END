package dev.aurora.alertas.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.aurora.alertas.model.Alerta;
import dev.aurora.alertas.model.PreferenciaEmail;
import dev.aurora.alertas.model.Severidade;
import dev.aurora.alertas.model.TipoAlerta;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MensagemDeAlertasTest {

	@Test
	void resumoComContagemLinksEHtmlEscapado() {
		var critico = alerta(Severidade.CRITICO, "Adesão abaixo do limite de alerta em <Cidade B>",
				"/painel?unidade=1");
		var atencao = alerta(Severidade.ATENCAO, "Fonte de dados atrasada: Comercial", null);

		var m = MensagemDeAlertas.montar("Ana", List.of(critico, atencao), "https://aurora.empresa.com.br/");

		assertThat(m.assunto()).isEqualTo("[Aurora] 2 alertas novos (1 crítico)");
		assertThat(m.texto()).contains("[CRÍTICO] Adesão abaixo do limite de alerta em <Cidade B>")
			.contains("Ver: https://aurora.empresa.com.br/painel?unidade=1")
			.contains("Ver: https://aurora.empresa.com.br/alertas");
		assertThat(m.html()).contains("&lt;Cidade B&gt;").doesNotContain("<Cidade B>");
		assertThat(MensagemDeAlertas.montar("Ana", List.of(atencao), "http://localhost:4200").assunto())
			.isEqualTo("[Aurora] 1 alerta novo");
	}

	@Test
	void preferenciaPadraoRecebeSoCriticos() {
		assertThat(PreferenciaEmail.CRITICOS.recebe(Severidade.CRITICO)).isTrue();
		assertThat(PreferenciaEmail.CRITICOS.recebe(Severidade.ATENCAO)).isFalse();
		assertThat(PreferenciaEmail.TODOS.recebe(Severidade.ATENCAO)).isTrue();
		assertThat(PreferenciaEmail.NENHUM.recebe(Severidade.CRITICO)).isFalse();
	}

	@Test
	void cadaTipoTemAPermissaoDaSuaTela() {
		assertThat(NotificadorAlertas.permissaoDo(TipoAlerta.TRILHA_ALTERADA)).isEqualTo("auditoria:ler");
		assertThat(NotificadorAlertas.permissaoDo(TipoAlerta.PRAZO_CONTRATUAL)).isEqualTo("contrato:ler");
		assertThat(NotificadorAlertas.permissaoDo(TipoAlerta.META_FORA_DO_RITMO)).isEqualTo("indicador:ler");
	}

	private static Alerta alerta(Severidade severidade, String titulo, String link) {
		var agora = Instant.now();
		return new Alerta(UUID.randomUUID(), TipoAlerta.INDICADOR_ABAIXO_DA_META, severidade, null, null, "X", titulo,
				"Adesão de 68,0%.", link, agora, agora, null, null, null);
	}

}
