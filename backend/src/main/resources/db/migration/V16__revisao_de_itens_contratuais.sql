-- Revisão de itens contratuais (A2, A3, RN-10). O schema herdado não guardava o que a especificação exige:
--   unidade alvo, limite de alerta e início de vigência (para itens META virarem meta na aprovação),
--   a proposta original da IA (edições preservam o valor da IA), a confiança e a versão do prompt.
ALTER TABLE item_contratual
    ADD COLUMN unidade_id      uuid,
    ADD COLUMN valor_alerta    numeric(18, 6),
    ADD COLUMN vigencia_inicio date,
    ADD COLUMN confianca       numeric(4, 3) CHECK (confianca BETWEEN 0 AND 1),
    ADD COLUMN proposta_ia     jsonb,
    ADD COLUMN versao_prompt   text,
    -- NOT VALID: a coluna é nova (todas as linhas nulas), não há passado a validar; validar exigiria ler
    -- tabelas com FORCE ROW LEVEL SECURITY sem contexto de tenant. Linhas novas são conferidas normalmente.
    ADD CONSTRAINT item_unidade_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id) NOT VALID;

-- Quando o processamento começou (para reprocessar um documento parado no meio, ex.: queda da API).
ALTER TABLE documento_contrato ADD COLUMN processamento_iniciado_em timestamptz;

-- Reprocessar regrava o texto das páginas.
GRANT DELETE ON documento_pagina TO aurora_app;

CREATE INDEX idx_item_documento ON item_contratual (documento_id, status);
