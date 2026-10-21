-- RN-06: as vigências das metas de uma mesma unidade e indicador não podem se sobrepor.
-- Garantido no banco (exclusion constraint) para valer em qualquer caminho de escrita.
-- A validação lê a tabela inteira; com FORCE ROW LEVEL SECURITY nem o dono vê linhas sem contexto,
-- então o FORCE é desligado só durante a criação (a migração é transacional).
ALTER TABLE meta NO FORCE ROW LEVEL SECURITY;
ALTER TABLE meta ADD CONSTRAINT meta_vigencias_sem_sobreposicao
    EXCLUDE USING gist (tenant_id WITH =, unidade_id WITH =, indicador_id WITH =,
                        daterange(vigencia_inicio, vigencia_fim, '[]') WITH &&);
ALTER TABLE meta FORCE ROW LEVEL SECURITY;
