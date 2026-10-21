-- Usuários, perfis, permissões e escopo.
-- O perfil é concedido POR UNIDADE (usuario_perfil_escopo): a permissão vale na unidade e nas descendentes.

CREATE TABLE usuario (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES tenant (id),
    sub_externo   text,
    email         text NOT NULL,
    nome          text NOT NULL,
    ativo         boolean NOT NULL DEFAULT true,
    ultimo_acesso timestamptz,
    criado_em     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, sub_externo)
);

CREATE UNIQUE INDEX uq_usuario_email ON usuario (tenant_id, lower(email));

-- Catálogo global de permissões.
CREATE TABLE permissao (
    codigo    text PRIMARY KEY,
    descricao text NOT NULL
);

INSERT INTO permissao (codigo, descricao) VALUES
    ('assistente:usar',      'Usar o assistente de IA'),
    ('auditoria:ler',        'Consultar a trilha de auditoria'),
    ('contrato:gerenciar',   'Enviar documentos, revisar e aprovar itens contratuais'),
    ('contrato:ler',         'Consultar contratos, documentos e itens contratuais'),
    ('dados_pessoais:ler',   'Ver dados pessoais sem máscara'),
    ('indicador:gerenciar',  'Cadastrar indicadores e metas'),
    ('indicador:ler',        'Consultar indicadores, metas e medições'),
    ('integracao:gerenciar', 'Configurar fontes de dados'),
    ('mapa:marcar',          'Marcar locais no mapa'),
    ('relatorio:exportar',   'Exportar relatórios'),
    ('unidade:gerenciar',    'Cadastrar, mover e desativar unidades'),
    ('unidade:ler',          'Consultar unidades'),
    ('usuario:gerenciar',    'Gerenciar usuários, perfis e escopos');

CREATE TABLE perfil (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant (id),
    nome      text NOT NULL,
    sistema   boolean NOT NULL DEFAULT false,
    descricao text,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, nome)
);

CREATE TABLE perfil_permissao (
    tenant_id uuid NOT NULL,
    perfil_id uuid NOT NULL,
    permissao text NOT NULL REFERENCES permissao (codigo),
    PRIMARY KEY (perfil_id, permissao),
    FOREIGN KEY (tenant_id, perfil_id) REFERENCES perfil (tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE usuario_perfil_escopo (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    usuario_id    uuid NOT NULL,
    perfil_id     uuid NOT NULL,
    unidade_id    uuid NOT NULL,
    valido_ate    timestamptz,
    concedido_por uuid,
    criado_em     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, perfil_id, unidade_id),
    FOREIGN KEY (tenant_id, usuario_id)    REFERENCES usuario (tenant_id, id),
    FOREIGN KEY (tenant_id, perfil_id)     REFERENCES perfil (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id)    REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, concedido_por) REFERENCES usuario (tenant_id, id)
);

CREATE INDEX idx_upe_usuario ON usuario_perfil_escopo (usuario_id);

ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;
CREATE POLICY usuario_tenant ON usuario USING (tenant_id = app_tenant());

ALTER TABLE perfil ENABLE ROW LEVEL SECURITY;
ALTER TABLE perfil FORCE ROW LEVEL SECURITY;
CREATE POLICY perfil_tenant ON perfil USING (tenant_id = app_tenant());

ALTER TABLE perfil_permissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao FORCE ROW LEVEL SECURITY;
CREATE POLICY perfil_permissao_tenant ON perfil_permissao USING (tenant_id = app_tenant());

ALTER TABLE usuario_perfil_escopo ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario_perfil_escopo FORCE ROW LEVEL SECURITY;
CREATE POLICY upe_tenant ON usuario_perfil_escopo USING (tenant_id = app_tenant());

GRANT SELECT ON permissao TO aurora_app;
GRANT SELECT, INSERT, UPDATE ON usuario TO aurora_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON perfil, perfil_permissao, usuario_perfil_escopo TO aurora_app;
