package dev.aurora.acesso.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Perfil a conceder em uma unidade; sem validade, vale até ser retirado. */
public record NovaConcessao(UUID perfilId, UUID unidadeId, @Nullable OffsetDateTime validoAte) {
}
