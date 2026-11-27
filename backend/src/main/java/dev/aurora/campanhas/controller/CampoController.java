package dev.aurora.campanhas.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.campanhas.model.AcaoDeCampo;
import dev.aurora.campanhas.model.AvisoDeChegada;
import dev.aurora.campanhas.model.AmostraDePosicao;
import dev.aurora.campanhas.model.Anexo;
import dev.aurora.campanhas.model.DadosDispositivo;
import dev.aurora.campanhas.model.Dispositivo;
import dev.aurora.campanhas.model.EquipeDoDia;
import dev.aurora.campanhas.model.EventoDeCampo;
import dev.aurora.campanhas.model.Jornada;
import dev.aurora.campanhas.model.PacoteDoDia;
import dev.aurora.campanhas.model.Posicao;
import dev.aurora.campanhas.model.RespostaDoEvento;
import dev.aurora.campanhas.model.ResultadoOrdem;
import dev.aurora.campanhas.service.AnexoService;
import dev.aurora.campanhas.service.AvisoDeChegadaService;
import dev.aurora.campanhas.service.DispositivoService;
import dev.aurora.campanhas.service.ExecucaoService;
import dev.aurora.campanhas.service.JornadaService;
import dev.aurora.contexto.ExigePermissao;
import dev.aurora.servicos.FaseDaOrdem;
import dev.aurora.servicos.ParametroDaOrdem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API do app de campo (doc 10, 10.9.4; ADR 0045). Toda chamada identifica o celular pelo
 * cabeçalho {@value #DISPOSITIVO}; celular revogado recebe 403 com o código
 * {@code DISPOSITIVO_REVOGADO} e só consegue enviar a fila pendente.
 */
@RestController
@RequestMapping("/api/v1/campo")
@ExigePermissao("os:executar")
class CampoController {

	static final String DISPOSITIVO = "X-Aurora-Dispositivo";

	private final DispositivoService dispositivos;

	private final JornadaService jornadas;

	private final ExecucaoService execucao;

	private final AnexoService anexos;

	private final AvisoDeChegadaService avisos;

	CampoController(DispositivoService dispositivos, JornadaService jornadas, ExecucaoService execucao,
			AnexoService anexos, AvisoDeChegadaService avisos) {
		this.avisos = avisos;
		this.dispositivos = dispositivos;
		this.jornadas = jornadas;
		this.execucao = execucao;
		this.anexos = anexos;
	}

	// ----- Celular (J13) -----

	record DispositivoRequest(@NotNull String modelo, @NotNull String sistema, @NotNull String versaoApp) {
	}

	@PostMapping("/dispositivo")
	DispositivoResponse cadastrar(@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo,
			@Valid @RequestBody DispositivoRequest pedido) {
		return DispositivoResponse.de(dispositivos.cadastrar(dispositivo,
				new DadosDispositivo(pedido.modelo(), pedido.sistema(), pedido.versaoApp())));
	}

	/** O app apagou os dados locais depois da revogação. */
	@PostMapping("/dispositivo/apagado")
	void apagado(@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo) {
		dispositivos.confirmarApagado(dispositivo);
	}

	// ----- Jornada e pacote do dia (J8) -----

	record PosicaoRequest(double latitude, double longitude, @Nullable Double precisaoM) {

		Posicao posicao() {
			return new Posicao(latitude, longitude, precisaoM);
		}
	}

	/**
	 * @param id gerado no celular (o mesmo início reenviado não abre outra jornada)
	 * @param equipeId obrigatório para quem está em mais de uma equipe
	 */
	record InicioJornadaRequest(@NotNull UUID id, @Nullable UUID equipeId, @NotNull Instant ocorridoEm,
			@NotNull @Valid PosicaoRequest posicao) {
	}

	record FimJornadaRequest(@NotNull Instant ocorridoEm) {
	}

	record JornadaResponse(UUID id, UUID equipeId, String equipeNome, Instant inicio, @Nullable Instant fim) {

		static @Nullable JornadaResponse de(@Nullable Jornada j) {
			return j == null ? null : new JornadaResponse(j.id(), j.equipeId(), j.equipeNome(), j.inicio(), j.fim());
		}
	}

	/** Parâmetro de encerramento da versão do serviço (J2). */
	record ParametroDoPacoteResponse(String chave, String rotulo, @Nullable String ajuda, String tipo,
			boolean obrigatorio, @Nullable BigDecimal minimo, @Nullable BigDecimal maximo, @Nullable String unidade,
			List<String> opcoes, @Nullable String condicaoChave, @Nullable String condicaoValor) {

		static ParametroDoPacoteResponse de(ParametroDaOrdem p) {
			return new ParametroDoPacoteResponse(p.chave(), p.rotulo(), p.ajuda(), p.tipo(), p.obrigatorio(),
					p.minimo(), p.maximo(), p.unidade(), p.opcoes(), p.condicaoChave(), p.condicaoValor());
		}
	}

	/**
	 * @param ultimaSequencia o próximo evento do celular nesta OS é este + 1
	 */
	record OrdemDoPacoteResponse(OrdemServicoResponse ordem, UUID servicoVersaoId, int ultimaSequencia,
			boolean avisoDeChegada, @Nullable Instant avisadoEm, List<FaseDaOrdem> fasesComFoto,
			List<FaseDaOrdem> fasesComObservacao) {
	}

	/** "Estou chegando" (ADR 0052). */
	record AvisoDeChegadaRequest(@NotNull UUID id, @Min(1) @Max(120) int minutos, @NotNull Instant ocorridoEm) {
	}

	/**
	 * @param situacao ENVIADO, REGISTRADO (sem provedor de SMS) ou FALHOU (tente de novo)
	 */
	record AvisoDeChegadaResponse(UUID id, UUID osId, int minutos, String situacao, Instant ocorridoEm) {

		static AvisoDeChegadaResponse de(AvisoDeChegada a) {
			return new AvisoDeChegadaResponse(a.id(), a.osId(), a.minutos(), a.situacao(), a.ocorridoEm());
		}

	}

	/**
	 * Pacote do dia (10.9.2).
	 *
	 * @param ids todas as OS do pacote (o celular remove as que não estão aqui)
	 * @param ordens todas, ou só as alteradas quando {@code diferenca}
	 * @param parametros por versão do serviço
	 */
	record PacoteResponse(long versao, boolean diferenca, UUID equipeId, String equipeNome,
			@Nullable JornadaResponse jornada, List<UUID> ids, List<OrdemDoPacoteResponse> ordens,
			Map<UUID, List<ParametroDoPacoteResponse>> parametros, List<MotivoDaOrdemResponse> motivos, int raioInicioM,
			Instant geradoEm) {

		static PacoteResponse de(PacoteDoDia p) {
			var parametros = new java.util.LinkedHashMap<UUID, List<ParametroDoPacoteResponse>>();
			p.parametros()
				.forEach((versao, lista) -> parametros.put(versao,
						lista.stream().map(ParametroDoPacoteResponse::de).toList()));
			return new PacoteResponse(p.versao(), p.diferenca(), p.equipeId(), p.equipeNome(),
					JornadaResponse.de(p.jornada()), p.ids(),
					p.ordens()
						.stream()
						.map(o -> new OrdemDoPacoteResponse(OrdemServicoResponse.de(o.ordem()), o.servicoVersaoId(),
								o.ultimaSequencia(), o.avisoDeChegada(), o.avisadoEm(), o.fasesComFoto(),
								o.fasesComObservacao()))
						.toList(),
					parametros, p.motivos().stream().map(MotivoDaOrdemResponse::de).toList(), p.raioInicioM(),
					p.geradoEm());
		}
	}

	/**
	 * @param ordensHoje OS programadas para hoje ainda abertas
	 * @param jornadaAberta alguém da equipe já iniciou a jornada
	 */
	record EquipeDoDiaResponse(UUID id, String nome, long ordensHoje, long emergenciais, @Nullable String veiculoNome,
			boolean jornadaAberta) {

		static EquipeDoDiaResponse de(EquipeDoDia e) {
			return new EquipeDoDiaResponse(e.id(), e.nome(), e.ordensHoje(), e.emergenciais(), e.veiculoNome(),
					e.jornadaAberta());
		}
	}

	/** Equipes da pessoa com o resumo de hoje (tela "Iniciar jornada"). */
	@GetMapping("/equipes")
	List<EquipeDoDiaResponse> equipes(@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo) {
		return jornadas.equipesDoDia(dispositivo).stream().map(EquipeDoDiaResponse::de).toList();
	}

	/**
	 * @param desde versão do pacote que o celular já tem (só as diferenças)
	 * @param equipe para quem está em mais de uma equipe
	 */
	@GetMapping("/jornada")
	PacoteResponse pacote(@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo,
			@RequestParam(required = false) @Nullable Long desde,
			@RequestParam(required = false) @Nullable UUID equipe) {
		return PacoteResponse.de(jornadas.pacote(dispositivo, desde, equipe));
	}

	@PostMapping("/jornada/inicio")
	PacoteResponse iniciarJornada(@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo,
			@Valid @RequestBody InicioJornadaRequest pedido) {
		return PacoteResponse.de(jornadas.iniciar(dispositivo, pedido.id(), pedido.equipeId(), pedido.ocorridoEm(),
				pedido.posicao().posicao()));
	}

	@PostMapping("/jornada/fim")
	@Nullable JornadaResponse encerrarJornada(@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo,
			@Valid @RequestBody FimJornadaRequest pedido) {
		return JornadaResponse.de(jornadas.encerrar(dispositivo, pedido.ocorridoEm()));
	}

	// ----- Eventos (J9, J10; RN-26) -----

	/**
	 * Ação registrada no celular (10.9.3).
	 *
	 * @param valores no encerramento executado: chave do parâmetro → valor; fotos são as
	 * listas de ids dos anexos
	 * @param fotos nas outras ações, ids das fotos tiradas nela (ADR 0053)
	 */
	record EventoRequest(@NotNull UUID id, @NotNull UUID osId, int sequencia, @NotNull AcaoDeCampo tipo,
			@Nullable UUID motivoId, @Nullable @Size(max = 2000) String observacao, @Nullable ResultadoOrdem resultado,
			@Nullable BigDecimal quantidade, @Nullable JsonNode valores, @NotNull Instant ocorridoEm,
			@Nullable @Valid PosicaoRequest posicao, @Nullable Long versaoPacote,
			@Nullable @Size(max = 10) List<@NotNull UUID> fotos) {

		EventoDeCampo evento() {
			return new EventoDeCampo(id, osId, sequencia, tipo, motivoId, observacao, resultado, quantidade, valores,
					ocorridoEm, posicao == null ? null : posicao.posicao(), versaoPacote,
					fotos == null ? List.of() : fotos);
		}
	}

	/**
	 * @param enviadoEm relógio do celular no envio (RN-26: aviso de relógio)
	 */
	record LoteDeEventosRequest(@Nullable Instant enviadoEm,
			@NotNull @Size(min = 1, max = 200) List<@NotNull @Valid EventoRequest> eventos) {
	}

	/**
	 * @param situacao APLICADO, REPETIDO, CONFLITO, SUPERADO, AGUARDANDO ou REJEITADO
	 * @param original no REPETIDO, a situação da primeira vez
	 */
	record RespostaDoEventoResponse(UUID id, String situacao, @Nullable String original, @Nullable String mensagem,
			List<String> avisos) {

		static RespostaDoEventoResponse de(RespostaDoEvento r) {
			return new RespostaDoEventoResponse(r.id(), r.situacao().name(),
					r.original() == null ? null : r.original().name(), r.mensagem(), r.avisos());
		}
	}

	/**
	 * @param revogado o celular foi bloqueado: apague os dados locais
	 */
	record RecebimentoResponse(List<RespostaDoEventoResponse> eventos, boolean revogado) {
	}

	@PostMapping("/eventos")
	RecebimentoResponse eventos(@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo,
			@Valid @RequestBody LoteDeEventosRequest pedido) {
		var r = execucao.receber(dispositivo, pedido.enviadoEm(),
				pedido.eventos().stream().map(EventoRequest::evento).toList());
		return new RecebimentoResponse(r.eventos().stream().map(RespostaDoEventoResponse::de).toList(), r.revogado());
	}

	// ----- Fotos (10.9.3) -----

	/**
	 * @param situacao RECEBENDO, DISPONIVEL ou RECUSADO
	 */
	record AnexoResponse(UUID id, UUID osId, @Nullable String parametro, String tipoMidia, int tamanho, int recebido,
			String situacao, @Nullable String recusa) {

		static AnexoResponse de(Anexo a) {
			return new AnexoResponse(a.id(), a.osId(), a.parametro(), a.tipoMidia(), a.tamanho(), a.recebido(),
					a.situacao(), a.recusa());
		}
	}

	/**
	 * Parte de uma foto, a partir do byte já recebido ({@code Content-Range: bytes
	 * inicio-fim/total}; até 1 MB por parte, 5 MB por foto). O SHA-256 do arquivo inteiro
	 * vai no cabeçalho {@code X-Conteudo-Sha256}.
	 */
	@PutMapping(path = "/anexos/{id}",
			consumes = { MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp" })
	AnexoResponse enviarParte(@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo,
			@PathVariable UUID id, @RequestParam UUID os, @RequestParam(required = false) @Nullable String parametro,
			@RequestHeader(name = "Content-Type", required = false) @Nullable String tipo,
			@RequestHeader(name = "Content-Range", required = false) @Nullable String intervalo,
			@RequestHeader(name = "X-Conteudo-Sha256", required = false) @Nullable String sha256,
			@RequestBody byte[] conteudo) {
		return AnexoResponse.de(anexos.receber(dispositivo, id,
				new AnexoService.Parte(os, parametro, tipo, intervalo, sha256, conteudo)));
	}

	/** Quanto já chegou (para retomar o envio). */
	@GetMapping("/anexos/{id}")
	AnexoResponse anexo(@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo,
			@PathVariable UUID id) {
		return AnexoResponse.de(anexos.estado(dispositivo, id));
	}

	// ----- Posições (RN-27) -----

	record AmostraRequest(@NotNull Instant momento, double latitude, double longitude, @Nullable Double precisaoM) {
	}

	record PosicoesRequest(@NotNull @Size(max = 500) List<@NotNull AmostraRequest> posicoes) {
	}

	record PosicoesResponse(int gravadas) {
	}

	@PostMapping("/posicoes")
	PosicoesResponse posicoes(@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo,
			@Valid @RequestBody PosicoesRequest pedido) {
		var amostras = pedido.posicoes()
			.stream()
			.map(a -> new AmostraDePosicao(a.momento(), new Posicao(a.latitude(), a.longitude(), a.precisaoM())))
			.toList();
		return new PosicoesResponse(jornadas.registrarPosicoes(dispositivo, amostras));
	}

	/**
	 * "Estou chegando" (ADR 0052): SMS ao morador de que a equipe está a caminho. Repetir
	 * o mesmo id devolve o mesmo aviso.
	 */
	@PostMapping("/ordens/{os}/aviso-de-chegada")
	AvisoDeChegadaResponse avisarChegada(
			@RequestHeader(name = DISPOSITIVO, required = false) @Nullable UUID dispositivo, @PathVariable UUID os,
			@Valid @RequestBody AvisoDeChegadaRequest pedido) {
		return AvisoDeChegadaResponse
			.de(avisos.avisar(dispositivo, pedido.id(), os, pedido.minutos(), pedido.ocorridoEm()));
	}

}
