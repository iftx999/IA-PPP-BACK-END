-- ADR 0024: prazo contratual marcado como cumprido (o alerta de prazo deixa de valer) e preferência de cada
-- pessoa para receber alertas por e-mail.
ALTER TABLE item_contratual
    ADD COLUMN cumprido_em  timestamptz,
    ADD COLUMN cumprido_por uuid,
    ADD CONSTRAINT item_cumprido_so_prazo CHECK (cumprido_em IS NULL OR tipo = 'PRAZO');

CREATE TABLE alerta_preferencia (
    tenant_id     uuid NOT NULL REFERENCES tenant (id),
    usuario_id    uuid NOT NULL,
    email         text NOT NULL DEFAULT 'CRITICOS' CHECK (email IN ('NENHUM', 'CRITICOS', 'TODOS')),
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, usuario_id),
    FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id)
);

ALTER TABLE alerta_preferencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerta_preferencia FORCE ROW LEVEL SECURITY;
CREATE POLICY alerta_preferencia_tenant ON alerta_preferencia USING (tenant_id = app_tenant());

GRANT SELECT, INSERT, UPDATE ON alerta_preferencia TO aurora_app;

-- Fila de notificação: alertas abertos ainda não enviados.
CREATE INDEX idx_alerta_a_notificar ON alerta (tenant_id, criado_em)
    WHERE notificado_em IS NULL AND resolvido_em IS NULL;

INSERT INTO tarefa_agendada (nome) VALUES ('notificar-alertas');
