package dev.aurora.metas.model;

/** "N metas cadastradas · N do contrato · N unidades seguem a unidade acima" (C1). */
public record ResumoMetas(long cadastradas, long doContrato, long seguemAcima) {
}
