package dev.aurora.alertas.controller;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.aurora.alertas.model.Alerta;
import dev.aurora.alertas.model.EstadoAlerta;
import dev.aurora.alertas.model.Severidade;
import dev.aurora.alertas.model.TipoAlerta;
import dev.aurora.alertas.service.AlertaService;
import dev.aurora.contexto.ExigePermissao;
import org.jspecify.annotations.Nullable;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alertas (épico F). Ver e reconhecer exigem a leitura de indicadores, nas unidades onde
 * ela vale; alertas sem unidade (fonte, trilha) são de todo o tenant.
 */
@RestController
@RequestMapping("/api/v1/alertas")
@ExigePermissao("indicador:ler")
class AlertaController {

	record AlertaResponse(UUID id, TipoAlerta tipo, Severidade severidade, @Nullable UUID unidadeId,
			@Nullable String unidadeNome, String titulo, String descricao, @Nullable String link, Instant criadoEm,
			Instant atualizadoEm, @Nullable String reconhecidoPor, @Nullable Instant reconhecidoEm,
			@Nullable Instant resolvidoEm) {

		static AlertaResponse de(Alerta a) {
			return new AlertaResponse(a.id(), a.tipo(), a.severidade(), a.unidadeId(), a.unidadeNome(), a.titulo(),
					a.descricao(), a.link(), a.criadoEm(), a.atualizadoEm(), a.reconhecidoPor(), a.reconhecidoEm(),
					a.resolvidoEm());
		}
	}

	record FonteAtrasadaDto(UUID id, String nome, @Nullable Instant ultimaCarga, long atrasoMaximoHoras) {
	}

	record ResumoAlertasResponse(long criticos, long atencao, long reconhecidos, @Nullable YearMonth competencia,
			List<FonteAtrasadaDto> fontesAtrasadas) {
	}

	record AvaliacaoResponse(int abertos, int atualizados, int resolvidos) {
	}

	private final AlertaService alertas;

	AlertaController(AlertaService alertas) {
		this.alertas = alertas;
	}

	@GetMapping
	List<AlertaResponse> listar(@RequestParam(defaultValue = "ABERTOS") EstadoAlerta estado,
			@RequestParam(defaultValue = "200") int limite) {
		return alertas.listar(estado, limite).stream().map(AlertaResponse::de).toList();
	}

	@GetMapping("/resumo")
	ResumoAlertasResponse resumo() {
		var r = alertas.resumo();
		return new ResumoAlertasResponse(r.criticos(), r.atencao(), r.reconhecidos(), r.competencia(),
				r.fontesAtrasadas()
					.stream()
					.map(f -> new FonteAtrasadaDto(f.id(), f.nome(), f.ultimaCarga(), f.atrasoMaximo().toHours()))
					.toList());
	}

	@PostMapping("/{id}/reconhecimento")
	ResponseEntity<Void> reconhecer(@PathVariable UUID id) {
		alertas.reconhecer(id);
		return ResponseEntity.noContent().build();
	}

	/** Reavalia já (RN-07 roda a cada 15 min); para quem gere metas. */
	@PostMapping("/avaliacao")
	@ExigePermissao("indicador:gerenciar")
	AvaliacaoResponse avaliar() throws Exception {
		var r = alertas.avaliarAgora();
		return new AvaliacaoResponse(r.abertos(), r.atualizados(), r.resolvidos());
	}

}
