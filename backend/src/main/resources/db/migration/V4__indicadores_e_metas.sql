-- Indicadores (ex.: ADESAO_REDE), metas por unidade e vigência, e medições mensais.
-- A meta de uma unidade sem meta própria é herdada da unidade acima (resolvido na consulta).

CREATE TABLE indicador (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant (id),
    codigo         text NOT NULL,
    nome           text NOT NULL,
    descricao      text,
    unidade_medida text NOT NULL,
    sentido        text NOT NULL CHECK (sentido IN ('MAIOR_MELHOR', 'MENOR_MELHOR')),
    periodicidade  text NOT NULL CHECK (periodicidade IN ('DIARIA', 'MENSAL', 'TRIMESTRAL', 'ANUAL')),
    origem         text NOT NULL CHECK (origem IN ('CONTRATUAL', 'REGULATORIO', 'OPERACIONAL')),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, codigo)
);

CREATE TABLE meta (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid NOT NULL,
    indicador_id       uuid NOT NULL,
    unidade_id         uuid NOT NULL,
    vigencia_inicio    date NOT NULL,
    vigencia_fim       date,
    valor_meta         numeric(18, 6) NOT NULL,
    valor_alerta       numeric(18, 6),
    definido_por       uuid,
    criado_em          timestamptz NOT NULL DEFAULT now(),
    item_contratual_id uuid,
    -- Rótulo da origem exibido na tela (ex.: "Contrato 001/2025, cláusula 7.2, p. 2"); nulo = manual.
    origem             text,
    FOREIGN KEY (tenant_id, indicador_id) REFERENCES indicador (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id)   REFERENCES unidade (tenant_id, id),
    CONSTRAINT meta_check CHECK (vigencia_fim IS NULL OR vigencia_fim >= vigencia_inicio),
    CONSTRAINT meta_valores CHECK (valor_meta >= 0 AND (valor_alerta IS NULL OR valor_alerta >= 0))
);

CREATE INDEX idx_meta_indicador_unidade ON meta (indicador_id, unidade_id);

CREATE TABLE medicao (
    tenant_id      uuid NOT NULL,
    indicador_id   uuid NOT NULL,
    unidade_id     uuid NOT NULL,
    periodo_inicio date NOT NULL,
    valor          numeric(18, 6) NOT NULL,
    numerador      numeric(18, 6),
    denominador    numeric(18, 6),
    calculado_em   timestamptz NOT NULL DEFAULT now(),
    versao_regra   text NOT NULL,
    PRIMARY KEY (tenant_id, indicador_id, unidade_id, periodo_inicio),
    FOREIGN KEY (tenant_id, indicador_id) REFERENCES indicador (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id)   REFERENCES unidade (tenant_id, id)
) PARTITION BY RANGE (periodo_inicio);

CREATE TABLE medicao_ate_2025 PARTITION OF medicao FOR VALUES FROM (MINVALUE) TO ('2026-01-01');
CREATE TABLE medicao_2026     PARTITION OF medicao FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE medicao_2027     PARTITION OF medicao FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');
CREATE TABLE medicao_futuro   PARTITION OF medicao DEFAULT;

CREATE INDEX idx_medicao_unidade ON medicao (unidade_id, periodo_inicio);

ALTER TABLE indicador ENABLE ROW LEVEL SECURITY;
ALTER TABLE indicador FORCE ROW LEVEL SECURITY;
CREATE POLICY indicador_tenant ON indicador USING (tenant_id = app_tenant());

-- Leitura de metas inclui as unidades ACIMA do escopo, para resolver a herança ("Segue <unidade>").
ALTER TABLE meta ENABLE ROW LEVEL SECURITY;
ALTER TABLE meta FORCE ROW LEVEL SECURITY;
CREATE POLICY meta_leitura ON meta FOR SELECT USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u
         WHERE u.caminho <@ ANY (app_escopos()) OR u.caminho @> ANY (app_escopos())));
CREATE POLICY meta_escrita_insercao ON meta FOR INSERT WITH CHECK (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));
CREATE POLICY meta_escrita_alteracao ON meta FOR UPDATE USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));
CREATE POLICY meta_escrita_exclusao ON meta FOR DELETE USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE medicao ENABLE ROW LEVEL SECURITY;
ALTER TABLE medicao FORCE ROW LEVEL SECURITY;
CREATE POLICY medicao_escopo ON medicao USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

GRANT SELECT, INSERT, UPDATE ON indicador TO aurora_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON meta, medicao TO aurora_app;
