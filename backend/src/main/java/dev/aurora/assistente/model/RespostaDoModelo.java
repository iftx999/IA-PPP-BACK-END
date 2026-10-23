package dev.aurora.assistente.model;

/**
 * @param recusada o modelo recusou responder (classificador de segurança)
 */
public record RespostaDoModelo(String texto, String modelo, Uso uso, boolean recusada) {
}
