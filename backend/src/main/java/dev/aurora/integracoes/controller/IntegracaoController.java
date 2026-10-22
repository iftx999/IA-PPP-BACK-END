package dev.aurora.integracoes.controller;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.integracoes.model.Credencial;
import dev.aurora.integracoes.model.DadosFonte;
import dev.aurora.integracoes.model.Execucao;
import dev.aurora.integracoes.model.FonteDados;
import dev.aurora.integracoes.model.TipoAdaptador;
import dev.aurora.integracoes.service.IntegracaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Integrações e cargas (H3, S11). */
@RestController
@RequestMapping("/api/v1/integracoes")
@ExigePermissao("integracao:gerenciar")
class IntegracaoController {

	record FonteResponse(UUID id, String nome, TipoAdaptador tipo, @Nullable String sistemaOrigem,
			@Nullable String frequencia, long atrasoMaximoHoras, boolean ativo, Instant criadaEm,
			@Nullable Instant ultimaCarga, @Nullable String situacaoUltimaExecucao, @Nullable Instant ultimaExecucao,
			long credenciaisAtivas, boolean atrasada) {
	}

	record FonteRequest(@NotBlank @Size(max = 120) String nome, @NotNull TipoAdaptador tipo,
			@Nullable @Size(max = 120) String sistemaOrigem, @Nullable @Size(max = 60) String frequencia,
			@Min(1) @Max(2160) long atrasoMaximoHoras, boolean ativo) {

		DadosFonte dados() {
			return new DadosFonte(nome, tipo, sistemaOrigem, frequencia, Duration.ofHours(atrasoMaximoHoras), ativo);
		}
	}

	record ExecucaoResponse(UUID id, Instant inicio, @Nullable Instant fim, String situacao, @Nullable String origem,
			@Nullable LocalDate competencia, @Nullable Long lidos, @Nullable Long gravados, long rejeitados, long novos,
			long atualizados, long inalterados, @Nullable String erro) {

		static ExecucaoResponse de(Execucao e) {
			return new ExecucaoResponse(e.id(), e.inicio(), e.fim(), e.situacao(), e.origem(), e.competencia(),
					e.lidos(), e.gravados(), e.rejeitados(), e.novos(), e.atualizados(), e.inalterados(), e.erro());
		}
	}

	record CredencialResponse(String prefixo, Instant criadaEm, boolean ativa) {

		static CredencialResponse de(Credencial c) {
			return new CredencialResponse(c.prefixo(), c.criadaEm(), c.ativa());
		}
	}

	record CredencialGeradaResponse(String prefixo, String chave) {
	}

	record FonteCriada(UUID id) {
	}

	private final IntegracaoService integracoes;

	private final java.time.Clock relogio;

	IntegracaoController(IntegracaoService integracoes, java.time.Clock relogio) {
		this.integracoes = integracoes;
		this.relogio = relogio;
	}

	@GetMapping("/fontes")
	List<FonteResponse> fontes() {
		var agora = relogio.instant();
		return integracoes.listar().stream().map(f -> fonte(f, agora)).toList();
	}

	@PostMapping("/fontes")
	ResponseEntity<FonteCriada> criar(@Valid @RequestBody FonteRequest pedido) {
		var id = integracoes.criar(pedido.dados());
		return ResponseEntity.created(URI.create("/api/v1/integracoes/fontes/" + id)).body(new FonteCriada(id));
	}

	@PutMapping("/fontes/{id}")
	ResponseEntity<Void> alterar(@PathVariable UUID id, @Valid @RequestBody FonteRequest pedido) {
		integracoes.alterar(id, pedido.dados());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/fontes/{id}/execucoes")
	List<ExecucaoResponse> execucoes(@PathVariable UUID id, @RequestParam(defaultValue = "30") int limite) {
		return integracoes.execucoes(id, limite).stream().map(ExecucaoResponse::de).toList();
	}

	@GetMapping("/fontes/{id}/credenciais")
	List<CredencialResponse> credenciais(@PathVariable UUID id) {
		return integracoes.credenciais(id).stream().map(CredencialResponse::de).toList();
	}

	/** A chave completa só aparece nesta resposta. */
	@PostMapping("/fontes/{id}/credenciais")
	CredencialGeradaResponse gerarCredencial(@PathVariable UUID id) {
		var gerada = integracoes.gerarCredencial(id);
		return new CredencialGeradaResponse(gerada.prefixo(), gerada.chave());
	}

	@PostMapping("/credenciais/{prefixo}/revogacao")
	ResponseEntity<Void> revogar(@PathVariable @Pattern(regexp = "aur_[a-z0-9]{1,20}") String prefixo) {
		integracoes.revogarCredencial(prefixo);
		return ResponseEntity.noContent().build();
	}

	private static FonteResponse fonte(FonteDados f, Instant agora) {
		return new FonteResponse(f.id(), f.nome(), f.tipo(), f.sistemaOrigem(), f.frequencia(),
				f.atrasoMaximo().toHours(), f.ativo(), f.criadaEm(), f.ultimaCarga(), f.situacaoUltimaExecucao(),
				f.ultimaExecucao(), f.credenciaisAtivas(), f.atrasadaEm(agora));
	}

}
