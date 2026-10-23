package dev.aurora.contratos.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** PDF de um contrato e o resultado do processamento. */
public record Documento(UUID id, UUID contratoId, TipoDocumento tipo, String titulo, String nomeArquivo, String sha256,
		long tamanhoBytes, @Nullable Integer paginas, SituacaoDocumento situacao, @Nullable String erro,
		@Nullable String modeloIa, int itensPropostos, int itensDescartados, Instant enviadoEm,
		@Nullable Instant processamentoIniciadoEm, boolean textoPorOcr) {
}
