package dev.aurora.contratos.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Campos de um item contratual, como a IA propõe ou a pessoa revisa. Para META:
 * {@code valorReferencia} é a meta (%), {@code valorAlerta} o limite de alerta e
 * {@code dataLimite} o fim da vigência.
 */
public record DadosItem(TipoItem tipo, String titulo, String resumo, @Nullable String clausula, int pagina,
		String trecho, @Nullable BigDecimal valorReferencia, @Nullable String unidadeMedida,
		@Nullable LocalDate dataLimite, @Nullable String periodicidade, @Nullable UUID unidadeId,
		@Nullable BigDecimal valorAlerta, @Nullable LocalDate vigenciaInicio) {

	/**
	 * Sem cláusula identificável: não pode ser aprovado sem a pessoa completar (RN-10).
	 */
	public boolean semReferencia() {
		return clausula == null || clausula.isBlank();
	}

}
