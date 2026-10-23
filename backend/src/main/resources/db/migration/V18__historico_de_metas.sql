-- A5 / RN-06: aditivo contratual substitui metas sem perder o histórico (ADR 0020). A versão anterior de cada meta
-- alterada ou substituída por aditivo fica aqui; a tabela meta continua só com as vigências válidas (sem sobreposição).

CREATE TABLE meta_historico (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid NOT NULL,
    meta_id              uuid NOT NULL,
    indicador_id         uuid NOT NULL,
    unidade_id           uuid NOT NULL,
    vigencia_inicio      date NOT NULL,
    vigencia_fim         date,
    valor_meta           numeric(18, 6) NOT NULL,
    valor_alerta         numeric(18, 6),
    origem               text,
    item_contratual_id   uuid,
    motivo               text NOT NULL CHECK (motivo IN ('ENCERRADA_POR_ADITIVO', 'SUBSTITUIDA_POR_ADITIVO')),
    substituida_por_meta uuid,
    registrado_em        timestamptz NOT NULL DEFAULT now(),
    registrado_por       uuid,
    FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id)
);

CREATE INDEX idx_meta_historico_unidade ON meta_historico (tenant_id, unidade_id, vigencia_inicio);

ALTER TABLE meta_historico ENABLE ROW LEVEL SECURITY;
ALTER TABLE meta_historico FORCE ROW LEVEL SECURITY;
CREATE POLICY meta_historico_escopo ON meta_historico USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

-- Histórico só cresce: sem UPDATE de conteúdo além do vínculo com a meta nova, sem DELETE.
GRANT SELECT, INSERT ON meta_historico TO aurora_app;
GRANT UPDATE (substituida_por_meta) ON meta_historico TO aurora_app;
