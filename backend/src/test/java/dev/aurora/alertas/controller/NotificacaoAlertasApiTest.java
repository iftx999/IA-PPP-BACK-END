package dev.aurora.alertas.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.alertas.service.AvaliadorAlertas;
import dev.aurora.alertas.service.EnvioDeEmail;
import dev.aurora.alertas.service.NotificadorAlertas;
import dev.aurora.contexto.ContextoAcesso;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR 0024: alertas novos viram um e-mail por pessoa, conforme a preferência dela e o que
 * ela pode ver; nada é enviado duas vezes e falha de envio volta para a fila.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, NotificacaoAlertasApiTest.CaixaDeSaida.class })
class NotificacaoAlertasApiTest {

	record Email(String para, String assunto, String texto) {
	}

	@TestConfiguration
	static class CaixaDeSaida {

		static final List<Email> ENVIADOS = new ArrayList<>();

		static final Set<String> RECUSAR = ConcurrentHashMap.newKeySet();

		@Bean
		@Primary
		EnvioDeEmail envioDeTeste() {
			return new EnvioDeEmail() {
				@Override
				public boolean ligado() {
					return true;
				}

				@Override
				public void enviar(String para, String assunto, String texto, String html) {
					if (RECUSAR.contains(para)) {
						throw new IllegalStateException("caixa cheia");
					}
					synchronized (ENVIADOS) {
						ENVIADOS.add(new Email(para, assunto, texto));
					}
				}
			};
		}

	}

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	AvaliadorAlertas avaliador;

	@Autowired
	NotificadorAlertas notificador;

	@Autowired
	ContextosDoSistema contextos;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Notificações");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@notificacoes.teste", t.grupo());
		var leitura = DadosDeTeste.criarPerfil(t, "Leitura", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-gestor", "gestor@notificacoes.teste", t.concessao(), leitura);
		DadosDeTeste.criarUsuario(t, "sub-cidade-a", "cidade-a@notificacoes.teste", t.cidadeA(), leitura);
	}

	@Test
	void umResumoPorPessoaConformePreferenciaEEscopoSemRepetir() throws Exception {
		mvc.perform(get("/api/v1/alertas/preferencias").with(token("sub-gestor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email", is("CRITICOS")))
			.andExpect(jsonPath("$.envioLigado", is(true)));
		mvc.perform(put("/api/v1/alertas/preferencias").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"TODOS\"}")
			.with(token("sub-gestor"))).andExpect(status().isOk()).andExpect(jsonPath("$.email", is("TODOS")));
		mvc.perform(put("/api/v1/alertas/preferencias").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"QUALQUER\"}")
			.with(token("sub-gestor"))).andExpect(status().isBadRequest());

		ContextoAcesso.executar(sistema(), avaliador::avaliarIndicadores);
		var resultado = ContextoAcesso.executar(sistema(), notificador::notificar);

		// Admin (padrão: críticos) recebe só a Cidade B; o gestor (todos) recebe também a
		// concessão abaixo da meta e fora do ritmo; quem atua na Cidade A não vê nenhum
		// destes alertas.
		var admin = emailsPara("admin@notificacoes.teste");
		assertThat(admin).hasSize(1);
		assertThat(admin.getFirst().assunto()).isEqualTo("[Aurora] 1 alerta novo (1 crítico)");
		var gestor = emailsPara("gestor@notificacoes.teste");
		assertThat(gestor).hasSize(1);
		assertThat(gestor.getFirst().assunto()).isEqualTo("[Aurora] 3 alertas novos (1 crítico)");
		assertThat(gestor.getFirst().texto()).contains("[ATENÇÃO] Meta fora do ritmo em")
			.contains("http://localhost:4200/painel?unidade=" + t.concessao());
		assertThat(emailsPara("cidade-a@notificacoes.teste")).isEmpty();
		assertThat(resultado.emails()).isEqualTo(2);

		// Já notificados: a próxima execução não envia de novo.
		assertThat(ContextoAcesso.executar(sistema(), notificador::notificar).emails()).isZero();
		assertThat(emailsPara("admin@notificacoes.teste")).hasSize(1);
	}

	@Test
	void falhaDeEnvioMantemOAlertaNaFila() throws Exception {
		var outro = DadosDeTeste.criarTenant("Notificações com falha");
		DadosDeTeste.criarAdesao(outro);
		DadosDeTeste.criarUsuario(outro, "sub-falha", "falha@notificacoes.teste", outro.grupo());
		var contexto = contextos.doTenant(outro.id()).orElseThrow();
		ContextoAcesso.executar(contexto, avaliador::avaliarIndicadores);

		CaixaDeSaida.RECUSAR.add("falha@notificacoes.teste");
		var comFalha = ContextoAcesso.executar(contexto, notificador::notificar);
		assertThat(comFalha.falhas()).isEqualTo(1);
		// Os dois de atenção não têm destinatário (preferência padrão) e saem da fila; o
		// crítico fica.
		assertThat(comFalha.alertas()).isEqualTo(2);

		CaixaDeSaida.RECUSAR.clear();
		var depois = ContextoAcesso.executar(contexto, notificador::notificar);
		assertThat(depois.emails()).isEqualTo(1);
		assertThat(depois.alertas()).isEqualTo(1);
		assertThat(emailsPara("falha@notificacoes.teste")).hasSize(1);
	}

	private ContextoAcesso sistema() {
		return contextos.doTenant(t.id()).orElseThrow();
	}

	private static List<Email> emailsPara(String para) {
		synchronized (CaixaDeSaida.ENVIADOS) {
			return CaixaDeSaida.ENVIADOS.stream().filter(e -> e.para().equals(para)).toList();
		}
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
