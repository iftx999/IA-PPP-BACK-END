-- Formato do encadeamento de cada evento da trilha de auditoria.
--   NULL = legado (importado da versão anterior; o algoritmo original não é conhecido, então não é verificável).
--   2    = HMAC-SHA256(chave, hash_anterior || JSON canônico do evento); ver docs/adr/0009.
-- Os eventos legados continuam na cadeia: o primeiro evento novo encadeia no último hash legado.
ALTER TABLE evento_auditoria ADD COLUMN formato_hash smallint CHECK (formato_hash IN (2));
