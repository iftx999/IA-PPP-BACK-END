-- ADR 0038: imovel_situacao_mensal particionada por ano da competência. É a maior tabela do sistema (uma linha por
-- imóvel por mês, em todos os municípios do cliente): partições menores têm índices menores, estatísticas
-- recalculadas por partição depois de cada carga e permitem arquivar um ano inteiro com DETACH PARTITION.
--
-- Partições anuais criadas de antemão (2000 a 2050): nenhuma DDL em tempo de execução e a aplicação continua sem
-- privilégio de criar tabelas. Sem partição DEFAULT: competência fora da faixa falha na hora, em vez de cair numa
-- partição que depois impede criar a do ano. Antes de 2050, nova migração acrescenta os anos seguintes.

ALTER TABLE imovel_situacao_mensal RENAME TO imovel_situacao_mensal_antiga;
ALTER INDEX imovel_situacao_mensal_pkey   RENAME TO imovel_situacao_mensal_antiga_pkey;
ALTER INDEX idx_imovel_situacao_unidade   RENAME TO idx_imovel_situacao_antiga_unidade;
ALTER INDEX idx_imovel_situacao_matricula RENAME TO idx_imovel_situacao_antiga_matricula;

CREATE TABLE imovel_situacao_mensal (
    tenant_id      uuid NOT NULL,
    competencia    date NOT NULL CHECK (extract(day FROM competencia) = 1),
    codigo_origem  text NOT NULL,
    unidade_id     uuid NOT NULL,
    situacao       text NOT NULL CHECK (situacao IN ('LIGADO', 'FACTIVEL', 'CORTADO', 'POTENCIAL')),
    fonte_dados_id uuid NOT NULL,
    execucao_id    uuid NOT NULL,
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    motivo_corte   text CHECK (motivo_corte IN ('INADIMPLENCIA', 'A_PEDIDO')),
    CONSTRAINT imovel_motivo_so_para_cortado CHECK (motivo_corte IS NULL OR situacao = 'CORTADO'),
    PRIMARY KEY (tenant_id, competencia, codigo_origem),
    FOREIGN KEY (tenant_id, unidade_id)     REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, fonte_dados_id) REFERENCES fonte_dados (tenant_id, id),
    FOREIGN KEY (tenant_id, execucao_id)    REFERENCES execucao_sincronizacao (tenant_id, id)
) PARTITION BY RANGE (competencia);

DO $$
BEGIN
  FOR ano IN 2000..2050 LOOP
    EXECUTE format('CREATE TABLE %I PARTITION OF imovel_situacao_mensal FOR VALUES FROM (%L) TO (%L)',
                   'imovel_situacao_' || ano, make_date(ano, 1, 1), make_date(ano + 1, 1, 1));
  END LOOP;
END
$$;

CREATE INDEX idx_imovel_situacao_unidade   ON imovel_situacao_mensal (tenant_id, competencia, unidade_id);
CREATE INDEX idx_imovel_situacao_matricula ON imovel_situacao_mensal (tenant_id, codigo_origem, competencia);

-- O RLS forçado vale até para o dono: só a tabela antiga deixa de forçar, para a cópia ler todos os tenants.
ALTER TABLE imovel_situacao_mensal_antiga NO FORCE ROW LEVEL SECURITY;

INSERT INTO imovel_situacao_mensal (tenant_id, competencia, codigo_origem, unidade_id, situacao, fonte_dados_id,
                                    execucao_id, atualizado_em, motivo_corte)
SELECT tenant_id, competencia, codigo_origem, unidade_id, situacao, fonte_dados_id, execucao_id, atualizado_em,
       motivo_corte
  FROM imovel_situacao_mensal_antiga;

DROP TABLE imovel_situacao_mensal_antiga;

-- RLS na tabela particionada vale para todas as partições lidas por ela. A aplicação não tem permissão nas
-- partições: só chega aos dados pela tabela-mãe, com o RLS.
ALTER TABLE imovel_situacao_mensal ENABLE ROW LEVEL SECURITY;
ALTER TABLE imovel_situacao_mensal FORCE ROW LEVEL SECURITY;
CREATE POLICY imovel_situacao_escopo ON imovel_situacao_mensal USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

GRANT SELECT, INSERT, UPDATE ON imovel_situacao_mensal TO aurora_app;

ANALYZE imovel_situacao_mensal;
