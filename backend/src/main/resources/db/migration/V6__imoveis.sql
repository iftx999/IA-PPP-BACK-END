-- Situação mensal dos imóveis (base da adesão) e localização (matrícula + ponto; sem nome nem endereço).
-- codigo_origem = matrícula no sistema comercial.

CREATE TABLE imovel_situacao_mensal (
    tenant_id      uuid NOT NULL,
    competencia    date NOT NULL CHECK (extract(day FROM competencia) = 1),
    codigo_origem  text NOT NULL,
    unidade_id     uuid NOT NULL,
    situacao       text NOT NULL CHECK (situacao IN ('LIGADO', 'FACTIVEL', 'CORTADO', 'POTENCIAL')),
    fonte_dados_id uuid NOT NULL,
    execucao_id    uuid NOT NULL,
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, competencia, codigo_origem),
    FOREIGN KEY (tenant_id, unidade_id)     REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, fonte_dados_id) REFERENCES fonte_dados (tenant_id, id),
    FOREIGN KEY (tenant_id, execucao_id)    REFERENCES execucao_sincronizacao (tenant_id, id)
);

CREATE INDEX idx_imovel_situacao_unidade   ON imovel_situacao_mensal (tenant_id, competencia, unidade_id);
CREATE INDEX idx_imovel_situacao_matricula ON imovel_situacao_mensal (tenant_id, codigo_origem, competencia);

-- SRID 4674 = SIRGAS 2000 geográfico (padrão do IBGE).
CREATE TABLE imovel_localizacao (
    tenant_id      uuid NOT NULL,
    codigo_origem  text NOT NULL,
    unidade_id     uuid NOT NULL,
    geom           geometry(Point, 4674) NOT NULL,
    origem         text NOT NULL CHECK (origem IN ('GEOCODIFICADA', 'CAMPO', 'DESENHADA', 'IMPORTADA')),
    precisao       text NOT NULL CHECK (precisao IN ('ENDERECO', 'LOGRADOURO', 'BAIRRO', 'MANUAL')),
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    atualizado_por uuid,
    PRIMARY KEY (tenant_id, codigo_origem),
    FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id)
);

CREATE INDEX idx_imovel_localizacao_geom ON imovel_localizacao USING gist (geom);

ALTER TABLE imovel_situacao_mensal ENABLE ROW LEVEL SECURITY;
ALTER TABLE imovel_situacao_mensal FORCE ROW LEVEL SECURITY;
CREATE POLICY imovel_situacao_escopo ON imovel_situacao_mensal USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE imovel_localizacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE imovel_localizacao FORCE ROW LEVEL SECURITY;
CREATE POLICY imovel_localizacao_escopo ON imovel_localizacao USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

GRANT SELECT, INSERT, UPDATE ON imovel_situacao_mensal TO aurora_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON imovel_localizacao TO aurora_app;
