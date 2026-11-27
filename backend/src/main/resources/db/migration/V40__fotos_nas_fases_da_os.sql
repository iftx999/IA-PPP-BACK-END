-- ADR 0053: foto nas fases da OS. Além das fotos do encerramento (parâmetros do serviço), o agente fotografa ao
-- iniciar, paralisar, retomar e devolver. Cada versão do serviço diz em quais fases a foto é exigida (o gestor de
-- serviços tira a exigência onde não faz sentido); por padrão, em todas.

-- O dono está sujeito ao RLS forçado também ao validar as restrições novas: suspenso só nesta transação (como na V37).
ALTER TABLE servico_versao NO FORCE ROW LEVEL SECURITY;
ALTER TABLE os_evento NO FORCE ROW LEVEL SECURITY;

ALTER TABLE servico_versao
    ADD COLUMN fases_com_foto text[] NOT NULL DEFAULT '{INICIAR,PARALISAR,RETOMAR,DEVOLVER}',
    ADD CONSTRAINT servico_versao_fases_com_foto
        CHECK (fases_com_foto <@ ARRAY['INICIAR', 'PARALISAR', 'RETOMAR', 'DEVOLVER']::text[]);

-- Ids das fotos (os_anexo, enviadas em partes depois do evento, sem parâmetro) tiradas na ação. No encerramento, as
-- fotos seguem nos valores dos parâmetros.
ALTER TABLE os_evento
    ADD COLUMN fotos uuid[] NOT NULL DEFAULT '{}',
    ADD CONSTRAINT os_evento_fotos CHECK (cardinality(fotos) <= 10);

ALTER TABLE servico_versao FORCE ROW LEVEL SECURITY;
ALTER TABLE os_evento FORCE ROW LEVEL SECURITY;
