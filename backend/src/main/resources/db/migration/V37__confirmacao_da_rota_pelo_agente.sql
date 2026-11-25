-- ADR 0050: o agente confirma no app a nova ordem das OS publicada no meio da jornada (J7: "o agente vê a nova ordem
-- e confirma; sem sinal, segue a ordem que já tem"). Fica registrado na rota da equipe quando e quem confirmou.

-- O dono está sujeito ao RLS forçado também ao validar a chave nova: suspenso só nesta transação (como na V35).
ALTER TABLE rota NO FORCE ROW LEVEL SECURITY;
ALTER TABLE usuario NO FORCE ROW LEVEL SECURITY;

ALTER TABLE rota
    ADD COLUMN confirmada_em  timestamptz,
    ADD COLUMN confirmada_por uuid,
    ADD FOREIGN KEY (tenant_id, confirmada_por) REFERENCES usuario (tenant_id, id),
    ADD CONSTRAINT rota_confirmacao CHECK ((confirmada_em IS NULL) = (confirmada_por IS NULL));

ALTER TABLE rota FORCE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;

GRANT UPDATE (confirmada_em, confirmada_por) ON rota TO aurora_app;
