package dev.aurora.assistente.model;

/** Pergunta e resposta já trocadas na conversa (só o texto vai de volta ao modelo). */
public record TurnoAnterior(Papel papel, String texto) {
}
