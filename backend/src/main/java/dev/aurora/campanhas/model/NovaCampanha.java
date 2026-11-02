package dev.aurora.campanhas.model;

import java.time.LocalDate;

/** Criação de campanha (I2). */
public record NovaCampanha(String nome, Recorte recorte, AcaoCampanha acao, LocalDate prazo) {
}
