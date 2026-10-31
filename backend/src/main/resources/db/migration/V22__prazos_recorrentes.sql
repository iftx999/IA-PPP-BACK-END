-- ADR 0026: agenda de prazos e obrigações recorrentes. A agenda é configuração operacional do item aprovado (o texto
-- aprovado do contrato não muda): primeiro vencimento (padrão: a data limite do item) e repetição.
ALTER TABLE item_contratual
    ADD COLUMN primeiro_vencimento date,
    ADD COLUMN recorrencia text CHECK (recorrencia IN ('MENSAL', 'BIMESTRAL', 'TRIMESTRAL', 'QUADRIMESTRAL',
                                                       'SEMESTRAL', 'ANUAL', 'QUADRIENAL', 'QUINQUENAL')),
    ADD CONSTRAINT item_agenda_so_prazo CHECK (
        (primeiro_vencimento IS NULL AND recorrencia IS NULL) OR tipo IN ('PRAZO', 'OBRIGACAO'));

-- Cumprimento por vencimento (antes, um único cumprido_em por item). Histórico de quem informou cada um.
CREATE TABLE item_contratual_cumprimento (
    tenant_id    uuid NOT NULL,
    item_id      uuid NOT NULL,
    vencimento   date NOT NULL,
    cumprido_em  timestamptz NOT NULL DEFAULT now(),
    cumprido_por uuid,
    PRIMARY KEY (tenant_id, item_id, vencimento),
    FOREIGN KEY (tenant_id, item_id) REFERENCES item_contratual (tenant_id, id),
    FOREIGN KEY (tenant_id, cumprido_por) REFERENCES usuario (tenant_id, id)
);

-- Cumprimentos da V20 viram o cumprimento do vencimento (a data limite) do item. A cópia atravessa todos os tenants:
-- o RLS forçado é suspenso só para o dono, só nesta transação, e volta logo abaixo.
ALTER TABLE item_contratual NO FORCE ROW LEVEL SECURITY;
INSERT INTO item_contratual_cumprimento (tenant_id, item_id, vencimento, cumprido_em, cumprido_por)
SELECT tenant_id, id, COALESCE(data_limite, cumprido_em::date), cumprido_em, cumprido_por
  FROM item_contratual
 WHERE cumprido_em IS NOT NULL;
ALTER TABLE item_contratual FORCE ROW LEVEL SECURITY;

ALTER TABLE item_contratual_cumprimento ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_contratual_cumprimento FORCE ROW LEVEL SECURITY;
CREATE POLICY cumprimento_pelo_item ON item_contratual_cumprimento USING (
    tenant_id = app_tenant() AND item_id IN (SELECT id FROM item_contratual));

GRANT SELECT, INSERT, DELETE ON item_contratual_cumprimento TO aurora_app;

ALTER TABLE item_contratual
    DROP CONSTRAINT item_cumprido_so_prazo,
    DROP COLUMN cumprido_em,
    DROP COLUMN cumprido_por;
