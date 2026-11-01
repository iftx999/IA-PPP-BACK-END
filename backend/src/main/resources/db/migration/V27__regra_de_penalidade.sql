-- ADR 0035: regra calculável de uma penalidade aprovada, base do simulador de penalidades. É configuração do item
-- aprovado (o texto do contrato não muda): fórmula, valor e, quando a fórmula é percentual, a base em reais (ex.:
-- faturamento mensal), que não vem do contrato e é informada por quem gerencia contratos.
CREATE TABLE regra_penalidade (
    tenant_id      uuid NOT NULL,
    item_id        uuid NOT NULL,
    formula        text NOT NULL CHECK (formula IN ('PERCENTUAL_DA_BASE_POR_PONTO', 'VALOR_POR_PONTO', 'VALOR_FIXO')),
    valor          numeric(18, 6) NOT NULL CHECK (valor > 0),
    base_valor     numeric(18, 2) CHECK (base_valor > 0),
    base_descricao text CHECK (char_length(base_descricao) <= 120),
    contagem       text NOT NULL CHECK (contagem IN ('PROPORCIONAL', 'POR_PONTO_OU_FRACAO')),
    periodicidade  text NOT NULL CHECK (periodicidade IN ('MENSAL', 'ANUAL')),
    teto           numeric(18, 2) CHECK (teto > 0),
    atualizado_por uuid,
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, item_id),
    FOREIGN KEY (tenant_id, item_id) REFERENCES item_contratual (tenant_id, id),
    FOREIGN KEY (tenant_id, atualizado_por) REFERENCES usuario (tenant_id, id),
    CONSTRAINT regra_base_so_percentual CHECK (
        (formula = 'PERCENTUAL_DA_BASE_POR_PONTO') = (base_valor IS NOT NULL))
);

ALTER TABLE regra_penalidade ENABLE ROW LEVEL SECURITY;
ALTER TABLE regra_penalidade FORCE ROW LEVEL SECURITY;
CREATE POLICY regra_pelo_item ON regra_penalidade USING (
    tenant_id = app_tenant() AND item_id IN (SELECT id FROM item_contratual));

GRANT SELECT, INSERT, UPDATE, DELETE ON regra_penalidade TO aurora_app;
