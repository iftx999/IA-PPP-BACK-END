package dev.aurora.assistente.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.assistente.model.Conversa;
import dev.aurora.assistente.model.Fonte;
import dev.aurora.assistente.model.LinhaDeDados;
import dev.aurora.assistente.model.Mensagem;
import dev.aurora.assistente.model.Papel;
import dev.aurora.assistente.service.AssistenteService;
import dev.aurora.contexto.ExigePermissao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

/**
 * Assistente com fontes (épico G). Cada pessoa só vê as próprias conversas (RLS); as
 * respostas trazem chips numerados e linhas de dados (G2).
 */
@RestController
@RequestMapping("/api/v1/assistente")
@ExigePermissao("assistente:usar")
class AssistenteController {

	record SituacaoAssistenteResponse(boolean disponivel, long perguntasNoMes, @Nullable BigDecimal custoNoMesUsd,
			@Nullable BigDecimal limiteMensalUsd) {
	}

	record ConversaAssistenteResponse(UUID id, String titulo, Instant criadaEm, Instant atualizadaEm) {
		static ConversaAssistenteResponse de(Conversa c) {
			return new ConversaAssistenteResponse(c.id(), c.titulo(), c.criadaEm(), c.atualizadaEm());
		}
	}

	record FonteCitadaDto(int numero, String titulo, String detalhe) {
		static FonteCitadaDto de(Fonte f) {
			return new FonteCitadaDto(f.numero(), f.titulo(), f.detalhe());
		}
	}

	record LinhaDeDadosDto(int fonte, String titulo, Map<String, String> campos) {
		static LinhaDeDadosDto de(LinhaDeDados l) {
			return new LinhaDeDadosDto(l.fonte(), l.titulo(), l.campos());
		}
	}

	record MensagemAssistenteResponse(UUID id, Papel papel, String texto, List<FonteCitadaDto> fontes,
			List<LinhaDeDadosDto> dados, List<String> numerosSemFonte, @Nullable Integer avaliacao, Instant criadaEm) {
		static MensagemAssistenteResponse de(Mensagem m) {
			return new MensagemAssistenteResponse(m.id(), m.papel(), m.texto(),
					m.fontes().stream().map(FonteCitadaDto::de).toList(),
					m.dados().stream().map(LinhaDeDadosDto::de).toList(), m.numerosSemFonte(), m.avaliacao(),
					m.criadaEm());
		}
	}

	record ConversaAssistenteComMensagens(ConversaAssistenteResponse conversa,
			List<MensagemAssistenteResponse> mensagens) {
	}

	/**
	 * @param conversaId nula para abrir conversa nova
	 * @param unidadeId G5: unidade de onde a pessoa perguntou
	 */
	record PerguntaRequest(@Nullable UUID conversaId, @NotBlank @Size(max = 2000) String pergunta,
			@Nullable UUID unidadeId) {
	}

	record AvaliacaoRequest(@Min(-1) @Max(1) int avaliacao, @Size(max = 500) @Nullable String comentario) {
	}

	private final AssistenteService assistente;

	AssistenteController(AssistenteService assistente) {
		this.assistente = assistente;
	}

	@GetMapping("/situacao")
	SituacaoAssistenteResponse situacao() {
		var s = assistente.situacao();
		return new SituacaoAssistenteResponse(s.disponivel(), s.perguntasNoMes(), s.custoNoMesUsd(),
				s.limiteMensalUsd());
	}

	/** G1: pergunta; responde com a conversa e as duas mensagens novas. */
	@PostMapping("/perguntas")
	ConversaAssistenteComMensagens perguntar(@Valid @RequestBody PerguntaRequest pedido) {
		var troca = assistente.perguntar(pedido.conversaId(), pedido.pergunta(), pedido.unidadeId());
		return new ConversaAssistenteComMensagens(ConversaAssistenteResponse.de(troca.conversa()), List
			.of(MensagemAssistenteResponse.de(troca.pergunta()), MensagemAssistenteResponse.de(troca.resposta())));
	}

	/** G3: conversas anteriores, com busca no título e no texto. */
	@GetMapping("/conversas")
	List<ConversaAssistenteResponse> conversas(@RequestParam Optional<@Size(max = 100) String> busca) {
		return assistente.listar(busca).stream().map(ConversaAssistenteResponse::de).toList();
	}

	@GetMapping("/conversas/{id}")
	ConversaAssistenteComMensagens conversa(@PathVariable UUID id) {
		return new ConversaAssistenteComMensagens(ConversaAssistenteResponse.de(assistente.conversa(id)),
				assistente.mensagens(id).stream().map(MensagemAssistenteResponse::de).toList());
	}

	/** G4: 1 = ajudou, -1 = tem erro (com comentário opcional). */
	@PutMapping("/mensagens/{id}/avaliacao")
	ResponseEntity<Void> avaliar(@PathVariable UUID id, @Valid @RequestBody AvaliacaoRequest pedido) {
		if (pedido.avaliacao() == 0) {
			return ResponseEntity.badRequest().build();
		}
		assistente.avaliar(id, pedido.avaliacao(), pedido.comentario());
		return ResponseEntity.noContent().build();
	}

}
