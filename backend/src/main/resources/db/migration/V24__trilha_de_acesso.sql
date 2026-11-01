-- ADR 0028: eventos de acesso (login, falha, bloqueio, logout) vêm do Keycloak para a trilha de auditoria do tenant.
-- Esta tabela só evita importar o mesmo evento duas vezes; o registro que vale é o da trilha (evento_auditoria).
-- Linhas antigas são apagadas pela própria importação (o Keycloak guarda os eventos por 30 dias).
CREATE TABLE evento_acesso_importado (
    tenant_id   uuid NOT NULL REFERENCES tenant (id),
    id_externo  text NOT NULL,
    ocorrido_em timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, id_externo)
);

CREATE INDEX idx_evento_acesso_importado_quando ON evento_acesso_importado (tenant_id, ocorrido_em);

ALTER TABLE evento_acesso_importado ENABLE ROW LEVEL SECURITY;
ALTER TABLE evento_acesso_importado FORCE ROW LEVEL SECURITY;
CREATE POLICY evento_acesso_importado_tenant ON evento_acesso_importado USING (tenant_id = app_tenant());

GRANT SELECT, INSERT, DELETE ON evento_acesso_importado TO aurora_app;

INSERT INTO tarefa_agendada (nome) VALUES ('importar-eventos-de-acesso');
