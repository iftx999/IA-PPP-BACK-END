-- Alertas de indicador, ritmo, fonte atrasada, prazo contratual e trilha alterada (RN-07).
-- "chave" identifica a condição; só pode haver um alerta aberto por chave.

CREATE TABLE alerta (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL REFERENCES tenant (id),
    tipo            text NOT NULL CHECK (tipo IN ('INDICADOR_ABAIXO_DA_META', 'META_FORA_DO_RITMO',
                                                  'FONTE_ATRASADA', 'PRAZO_CONTRATUAL', 'TRILHA_ALTERADA')),
    severidade      text NOT NULL CHECK (severidade IN ('ATENCAO', 'CRITICO')),
    unidade_id      uuid,
    chave           text NOT NULL,
    titulo          text NOT NULL,
    descricao       text NOT NULL,
    link            text,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    atualizado_em   timestamptz NOT NULL DEFAULT now(),
    notificado_em   timestamptz,
    reconhecido_por uuid,
    reconhecido_em  timestamptz,
    resolvido_em    timestamptz,
    FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id)
);

CREATE INDEX idx_alerta_abertos ON alerta (tenant_id, resolvido_em, severidade);
CREATE UNIQUE INDEX uq_alerta_aberto ON alerta (tenant_id, chave) WHERE resolvido_em IS NULL;

-- Alertas sem unidade (ex.: fonte atrasada) são visíveis a todo o tenant.
ALTER TABLE alerta ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerta FORCE ROW LEVEL SECURITY;
CREATE POLICY alerta_escopo ON alerta USING (
    tenant_id = app_tenant() AND (unidade_id IS NULL OR unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos()))));

GRANT SELECT, INSERT, UPDATE ON alerta TO aurora_app;
