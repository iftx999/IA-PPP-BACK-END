package dev.aurora.mapa.model;

/** Resumo da emissão (vai para a auditoria). */
public record ArquivoExportado(long imoveis, long marcacoes, String sha256) {
}
