-- Conversas do assistente (cada uma pertence a um usuário), fontes citadas e controle de uso/custo.

CREATE TABLE assistente_conversa (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    usuario_id    uuid NOT NULL,
    titulo        text NOT NULL,
    criado_em     timestamptz NOT NULL DEFAULT now(),
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id)
);

CREATE INDEX idx_conversa_usuario ON assistente_conversa (usuario_id, atualizado_em DESC);

-- fontes: chips numerados; dados: linhas expansíveis; avaliacao: 1 = ajudou, -1 = tem erro.
CREATE TABLE assistente_mensagem (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL,
    conversa_id    uuid NOT NULL,
    papel          text NOT NULL CHECK (papel IN ('USUARIO', 'ASSISTENTE')),
    texto          text NOT NULL,
    fontes         jsonb NOT NULL DEFAULT '[]',
    dados          jsonb NOT NULL DEFAULT '[]',
    modelo         text,
    tokens_entrada integer,
    tokens_saida   integer,
    avaliacao      smallint CHECK (avaliacao IN (-1, 1)),
    criado_em      timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, conversa_id) REFERENCES assistente_conversa (tenant_id, id)
);

CREATE INDEX idx_mensagem_conversa ON assistente_mensagem (conversa_id, criado_em);

CREATE TABLE assistente_limite (
    tenant_id      uuid PRIMARY KEY REFERENCES tenant (id),
    tokens_por_mes bigint CHECK (tokens_por_mes > 0),
    usd_por_mes    numeric(12, 2) CHECK (usd_por_mes > 0)
);

CREATE TABLE assistente_uso_mensal (
    tenant_id            uuid NOT NULL REFERENCES tenant (id),
    mes                  date NOT NULL CHECK (extract(day FROM mes) = 1),
    perguntas            bigint NOT NULL DEFAULT 0,
    tokens_entrada       bigint NOT NULL DEFAULT 0,
    tokens_saida         bigint NOT NULL DEFAULT 0,
    tokens_cache_lido    bigint NOT NULL DEFAULT 0,
    tokens_cache_escrito bigint NOT NULL DEFAULT 0,
    custo_usd            numeric(14, 6) NOT NULL DEFAULT 0,
    perguntas_sem_custo  bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, mes)
);

ALTER TABLE assistente_conversa ENABLE ROW LEVEL SECURITY;
ALTER TABLE assistente_conversa FORCE ROW LEVEL SECURITY;
CREATE POLICY conversa_propria ON assistente_conversa USING (
    tenant_id = app_tenant() AND usuario_id = app_usuario());

ALTER TABLE assistente_mensagem ENABLE ROW LEVEL SECURITY;
ALTER TABLE assistente_mensagem FORCE ROW LEVEL SECURITY;
CREATE POLICY mensagem_da_conversa ON assistente_mensagem USING (
    tenant_id = app_tenant() AND conversa_id IN (SELECT id FROM assistente_conversa));

ALTER TABLE assistente_limite ENABLE ROW LEVEL SECURITY;
ALTER TABLE assistente_limite FORCE ROW LEVEL SECURITY;
CREATE POLICY limite_do_tenant ON assistente_limite USING (tenant_id = app_tenant());

ALTER TABLE assistente_uso_mensal ENABLE ROW LEVEL SECURITY;
ALTER TABLE assistente_uso_mensal FORCE ROW LEVEL SECURITY;
CREATE POLICY uso_do_tenant ON assistente_uso_mensal USING (tenant_id = app_tenant());

GRANT SELECT, INSERT, UPDATE ON assistente_conversa, assistente_mensagem, assistente_uso_mensal TO aurora_app;
GRANT SELECT ON assistente_limite TO aurora_app;
