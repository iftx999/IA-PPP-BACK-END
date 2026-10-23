-- Assistente (G2, G4, G6): números da resposta sem fonte encontrada, comentário do "Tem erro", custo por resposta
-- e busca nas conversas (G3).

ALTER TABLE assistente_mensagem
    ADD COLUMN numeros_sem_fonte    jsonb NOT NULL DEFAULT '[]',
    ADD COLUMN avaliacao_comentario text CHECK (length(avaliacao_comentario) <= 500),
    ADD COLUMN avaliado_em          timestamptz,
    ADD COLUMN tokens_cache_lido    integer,
    ADD COLUMN tokens_cache_escrito integer,
    ADD COLUMN custo_usd            numeric(12, 6);

CREATE INDEX idx_mensagem_texto ON assistente_mensagem USING gin (to_tsvector('portuguese', texto));
CREATE INDEX idx_conversa_titulo ON assistente_conversa USING gin (to_tsvector('portuguese', titulo));
