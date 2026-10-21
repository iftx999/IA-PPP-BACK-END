package dev.aurora.imoveis.model;

/**
 * Exportação concluída: quantidade de linhas de dados e SHA-256 do arquivo (registrados
 * na auditoria).
 */
public record ResultadoExportacao(long linhas, String sha256) {
}
