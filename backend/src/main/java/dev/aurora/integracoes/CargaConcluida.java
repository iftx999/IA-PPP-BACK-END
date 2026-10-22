package dev.aurora.integracoes;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Evento publicado quando uma carga termina com sucesso (os dados e as medições já foram
 * gravados): outros módulos reagem sem que este dependa deles (ex.: reavaliar alertas).
 */
public record CargaConcluida(UUID tenantId, UUID fonteId, UUID execucaoId, YearMonth competencia) {
}
