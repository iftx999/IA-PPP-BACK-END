-- Fontes de dados, credenciais do Agente Aurora, execuções de carga e trilha de auditoria encadeada por hash.

CREATE TABLE fonte_dados (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant (id),
    nome           text NOT NULL,
    tipo_adaptador text NOT NULL CHECK (tipo_adaptador IN ('AGENTE', 'ARQUIVO', 'CDC', 'API', 'ETL_JDBC')),
    ref_segredo    text,
    frequencia     text,
    atraso_maximo  interval NOT NULL DEFAULT '2 days',
    ativo          boolean NOT NULL DEFAULT true,
    sistema_origem text,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);

-- Chave do agente: o prefixo identifica a credencial antes de haver contexto de tenant (sem RLS).
CREATE TABLE credencial_conector (
    prefixo        text PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant (id),
    fonte_dados_id uuid NOT NULL,
    hash           bytea NOT NULL,
    ativo          boolean NOT NULL DEFAULT true,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, fonte_dados_id) REFERENCES fonte_dados (tenant_id, id)
);

CREATE TABLE execucao_sincronizacao (
    id                    uuid PRIMARY KEY,
    tenant_id             uuid NOT NULL,
    fonte_dados_id        uuid NOT NULL,
    inicio                timestamptz NOT NULL,
    fim                   timestamptz,
    status                text NOT NULL CHECK (status IN ('EM_ANDAMENTO', 'SUCESSO', 'FALHA')),
    registros_lidos       bigint,
    registros_gravados    bigint,
    marca_d_agua          text,
    erro                  text,
    competencia           date,
    origem                text CHECK (origem IN ('AGENTE', 'ARQUIVO')),
    registros_rejeitados  bigint NOT NULL DEFAULT 0,
    rejeicoes             jsonb NOT NULL DEFAULT '[]',
    usuario_id            uuid,
    registros_novos       bigint NOT NULL DEFAULT 0,
    registros_atualizados bigint NOT NULL DEFAULT 0,
    registros_inalterados bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, fonte_dados_id) REFERENCES fonte_dados (tenant_id, id)
);

CREATE INDEX idx_execucao_fonte ON execucao_sincronizacao (fonte_dados_id, inicio DESC);

-- Trilha de auditoria: somente inserção; cada evento guarda o hash do anterior (por tenant).
CREATE TABLE evento_auditoria (
    id            uuid NOT NULL,
    tenant_id     uuid NOT NULL,
    sequencia     bigint NOT NULL,
    ocorrido_em   timestamptz NOT NULL,
    usuario_id    uuid,
    tipo          text NOT NULL,
    recurso       text,
    detalhes      jsonb NOT NULL,
    ip_origem     inet,
    hash_anterior bytea NOT NULL,
    hash          bytea NOT NULL,
    PRIMARY KEY (id, ocorrido_em),
    UNIQUE (tenant_id, sequencia, ocorrido_em)
) PARTITION BY RANGE (ocorrido_em);

CREATE TABLE evento_auditoria_2026   PARTITION OF evento_auditoria
    FOR VALUES FROM ('2026-01-01 00:00:00-03') TO ('2027-01-01 00:00:00-03');
CREATE TABLE evento_auditoria_2027   PARTITION OF evento_auditoria
    FOR VALUES FROM ('2027-01-01 00:00:00-03') TO ('2028-01-01 00:00:00-03');
CREATE TABLE evento_auditoria_outros PARTITION OF evento_auditoria DEFAULT;

CREATE INDEX idx_evento_tenant_seq ON evento_auditoria (tenant_id, sequencia);

-- Último elo da cadeia de cada tenant (serializa a gravação de eventos).
CREATE TABLE cadeia_auditoria (
    tenant_id   uuid PRIMARY KEY REFERENCES tenant (id),
    sequencia   bigint NOT NULL,
    ultimo_hash bytea NOT NULL
);

ALTER TABLE fonte_dados ENABLE ROW LEVEL SECURITY;
ALTER TABLE fonte_dados FORCE ROW LEVEL SECURITY;
CREATE POLICY fonte_dados_tenant ON fonte_dados USING (tenant_id = app_tenant());

ALTER TABLE execucao_sincronizacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE execucao_sincronizacao FORCE ROW LEVEL SECURITY;
CREATE POLICY execucao_tenant ON execucao_sincronizacao USING (tenant_id = app_tenant());

ALTER TABLE evento_auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE evento_auditoria FORCE ROW LEVEL SECURITY;
CREATE POLICY evento_tenant ON evento_auditoria USING (tenant_id = app_tenant());

ALTER TABLE cadeia_auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE cadeia_auditoria FORCE ROW LEVEL SECURITY;
CREATE POLICY cadeia_tenant ON cadeia_auditoria USING (tenant_id = app_tenant());

GRANT SELECT, INSERT, UPDATE ON fonte_dados, credencial_conector, execucao_sincronizacao, cadeia_auditoria TO aurora_app;
GRANT SELECT, INSERT ON evento_auditoria TO aurora_app;
