package dev.aurora.servicos.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Feriado cadastrado para uma unidade (vale para ela e as de baixo; P6).
 *
 * @param nacional calculado (não cadastrado nem removível)
 */
public record Feriado(UUID id, UUID unidadeId, String unidadeNome, LocalDate data, String nome, boolean nacional) {
}
