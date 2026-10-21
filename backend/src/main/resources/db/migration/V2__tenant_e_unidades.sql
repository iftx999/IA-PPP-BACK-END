-- Tenant (cliente isolado) e hierarquia de unidades:
--   Grupo > Concessão/SPE > Município > Setor comercial | ETE | EEE
-- O caminho ltree de cada unidade é 'u' + id sem hífens, concatenado ao caminho do pai.

CREATE TABLE tenant (
    id        uuid PRIMARY KEY,
    nome      text NOT NULL,
    ativo     boolean NOT NULL DEFAULT true,
    criado_em timestamptz NOT NULL DEFAULT now()
);

-- Emissor OIDC (iss do token) -> tenant. Lido antes de existir contexto, por isso sem RLS.
CREATE TABLE tenant_emissor (
    emissor   text PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant (id)
);

CREATE TABLE tipo_unidade (
    codigo    text PRIMARY KEY,
    descricao text NOT NULL
);

INSERT INTO tipo_unidade (codigo, descricao) VALUES
    ('GRUPO',           'Grupo econômico'),
    ('CONCESSAO',       'Concessão / SPE'),
    ('MUNICIPIO',       'Município'),
    ('SETOR_COMERCIAL', 'Setor comercial'),
    ('ETE',             'Estação de tratamento de esgoto'),
    ('EEE',             'Estação elevatória de esgoto');

CREATE TABLE unidade (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant (id),
    tipo           text NOT NULL REFERENCES tipo_unidade (codigo),
    unidade_pai_id uuid,
    codigo         text NOT NULL,
    nome           text NOT NULL,
    caminho        ltree NOT NULL,
    codigo_ibge    char(7),
    ativo          boolean NOT NULL DEFAULT true,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    latitude       numeric(9, 6),
    longitude      numeric(9, 6),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, codigo),
    UNIQUE (tenant_id, caminho),
    FOREIGN KEY (tenant_id, unidade_pai_id) REFERENCES unidade (tenant_id, id),
    CONSTRAINT unidade_check CHECK ((tipo = 'MUNICIPIO') = (codigo_ibge IS NOT NULL)),
    CONSTRAINT unidade_check1 CHECK ((unidade_pai_id IS NULL) = (nlevel(caminho) = 1)),
    CONSTRAINT unidade_coordenada_completa CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT unidade_latitude_check CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT unidade_longitude_check CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_unidade_caminho ON unidade USING gist (caminho);
CREATE INDEX idx_unidade_pai ON unidade (unidade_pai_id);

ALTER TABLE tenant ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_proprio ON tenant USING (id = app_tenant());

-- A árvore inteira do tenant é visível (para navegação e herança de metas);
-- os dados de cada unidade é que são filtrados pelo escopo nas demais tabelas.
ALTER TABLE unidade ENABLE ROW LEVEL SECURITY;
ALTER TABLE unidade FORCE ROW LEVEL SECURITY;
CREATE POLICY unidade_tenant ON unidade USING (tenant_id = app_tenant());

GRANT SELECT ON tenant, tenant_emissor, tipo_unidade TO aurora_app;
GRANT SELECT, INSERT, UPDATE ON unidade TO aurora_app;
