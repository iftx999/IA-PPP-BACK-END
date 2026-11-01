package dev.aurora.plataforma.model;

/** Logo do cliente (PNG, JPEG ou WebP; até 256 KB). */
public record Logo(byte[] conteudo, String tipo) {
}
