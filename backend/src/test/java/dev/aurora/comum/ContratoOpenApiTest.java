package dev.aurora.comum;

import dev.aurora.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O contrato publicado distingue campos obrigatórios de anuláveis (base dos clientes
 * gerados).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContratoOpenApiTest {

	@Autowired
	MockMvc mvc;

	@Test
	void recordsPublicamCamposObrigatoriosENulos() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.components.schemas.PainelResponse.required", hasItems("adesao", "meta", "situacao")))
			.andExpect(jsonPath("$.components.schemas.PainelResponse.properties.meta.anyOf[*].type", hasItem("null")))
			.andExpect(jsonPath("$.components.schemas.PainelResponse.properties.adesao.anyOf").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.AdesaoDto.properties.ligados.type", not(hasItem("null"))));
	}

}
