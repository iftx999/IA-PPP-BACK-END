-- Camadas do mapa: municípios (malha do IBGE, dado público), setores, rede de esgoto e marcações de visita.

-- Dado público e compartilhado entre tenants (sem RLS).
CREATE TABLE municipio_geometria (
    codigo_ibge   char(7) PRIMARY KEY,
    geojson       jsonb NOT NULL,
    fonte         text NOT NULL,
    atualizado_em timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE setor_geometria (
    tenant_id      uuid NOT NULL,
    unidade_id     uuid NOT NULL,
    geom           geometry(MultiPolygon, 4674) NOT NULL CHECK (st_isvalid(geom)),
    origem         text NOT NULL CHECK (origem IN ('GERADO', 'DESENHADO', 'IMPORTADO')),
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    atualizado_por uuid,
    PRIMARY KEY (tenant_id, unidade_id),
    FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id)
);

CREATE INDEX idx_setor_geometria_geom ON setor_geometria USING gist (geom);

-- PRESUMIDA = a conferir (linha tracejada); CONFIRMADA = linha contínua.
CREATE TABLE rede_esgoto (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL,
    unidade_id     uuid NOT NULL,
    geom           geometry(LineString, 4674) NOT NULL CHECK (st_isvalid(geom) AND NOT st_isempty(geom)),
    situacao       text NOT NULL CHECK (situacao IN ('PRESUMIDA', 'CONFIRMADA')),
    origem         text NOT NULL CHECK (origem IN ('DEDUZIDA', 'DESENHADA', 'IMPORTADA', 'CAMPO')),
    diametro_mm    integer CHECK (diametro_mm > 0),
    material       text,
    implantada_em  date,
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    atualizado_por uuid,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id)
);

CREATE INDEX idx_rede_esgoto_geom ON rede_esgoto USING gist (geom);
CREATE INDEX idx_rede_esgoto_unidade ON rede_esgoto (tenant_id, unidade_id);

CREATE TABLE marcacao_mapa (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL,
    unidade_id uuid NOT NULL,
    geom       geometry(Point, 4674) NOT NULL,
    titulo     text NOT NULL CHECK (length(titulo) BETWEEN 1 AND 80),
    observacao text CHECK (length(observacao) <= 500),
    criada_por uuid NOT NULL,
    criada_em  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id)
);

CREATE INDEX idx_marcacao_mapa_geom ON marcacao_mapa USING gist (geom);

ALTER TABLE setor_geometria ENABLE ROW LEVEL SECURITY;
ALTER TABLE setor_geometria FORCE ROW LEVEL SECURITY;
CREATE POLICY setor_geometria_escopo ON setor_geometria USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE rede_esgoto ENABLE ROW LEVEL SECURITY;
ALTER TABLE rede_esgoto FORCE ROW LEVEL SECURITY;
CREATE POLICY rede_esgoto_escopo ON rede_esgoto USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE marcacao_mapa ENABLE ROW LEVEL SECURITY;
ALTER TABLE marcacao_mapa FORCE ROW LEVEL SECURITY;
CREATE POLICY marcacao_mapa_escopo ON marcacao_mapa USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

GRANT SELECT, INSERT, UPDATE ON municipio_geometria TO aurora_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON setor_geometria, rede_esgoto TO aurora_app;
GRANT SELECT, INSERT, DELETE ON marcacao_mapa TO aurora_app;
