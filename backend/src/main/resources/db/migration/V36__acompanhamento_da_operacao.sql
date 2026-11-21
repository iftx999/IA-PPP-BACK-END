-- ADR 0049: acompanhamento da operação de campo (J12) e gatilhos automáticos de reotimização (J7; doc 10, 10.8).
-- O mapa do dia e os indicadores são consultas sobre o que já existe (OS, eventos do app, jornadas, posições e
-- rotas). A única tabela nova guarda as sugestões de reotimização que um detector agendado abre quando entra uma
-- emergencial, uma OS fica paralisada por muito tempo, uma OS é devolvida ou uma equipe se atrasa. Nada é
-- recalculado sozinho: o programador aceita (vira uma roteirização de origem REOTIMIZACAO, que ele confere e
-- publica) ou ignora.

CREATE TABLE sugestao_reotimizacao (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL REFERENCES tenant (id),
    unidade_id       uuid NOT NULL,
    data             date NOT NULL,
    gatilho          text NOT NULL CHECK (gatilho IN ('EMERGENCIAL', 'PARALISACAO_LONGA', 'DEVOLUCAO', 'ATRASO')),
    -- Equipe afetada (nula na emergencial ainda sem equipe: a reotimização escolhe entre as equipes em jornada).
    equipe_id        uuid,
    os_id            uuid,
    -- O mesmo fato não abre duas sugestões (ex.: o evento de devolução, a rota em que a equipe se atrasou).
    chave            text NOT NULL CHECK (char_length(chave) <= 200),
    descricao        text NOT NULL CHECK (char_length(descricao) <= 300),
    -- Atraso estimado em minutos (só no gatilho ATRASO).
    atraso_min       integer,
    situacao         text NOT NULL DEFAULT 'ABERTA' CHECK (situacao IN ('ABERTA', 'ACEITA', 'IGNORADA', 'EXPIRADA')),
    criada_em        timestamptz NOT NULL DEFAULT now(),
    decidida_em      timestamptz,
    decidida_por     uuid,
    roteirizacao_id  uuid,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, chave),
    FOREIGN KEY (tenant_id, unidade_id)      REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, equipe_id)       REFERENCES equipe (tenant_id, id),
    FOREIGN KEY (tenant_id, os_id)           REFERENCES ordem_servico (tenant_id, id),
    FOREIGN KEY (tenant_id, decidida_por)    REFERENCES usuario (tenant_id, id),
    FOREIGN KEY (tenant_id, roteirizacao_id) REFERENCES roteirizacao (tenant_id, id),
    CONSTRAINT sugestao_decisao CHECK ((situacao IN ('ACEITA', 'IGNORADA')) = (decidida_em IS NOT NULL)),
    CONSTRAINT sugestao_aceita_com_roteirizacao CHECK ((situacao = 'ACEITA') = (roteirizacao_id IS NOT NULL)),
    CONSTRAINT sugestao_equipe_ou_os CHECK (equipe_id IS NOT NULL OR os_id IS NOT NULL)
);

CREATE INDEX idx_sugestao_aberta ON sugestao_reotimizacao (tenant_id, data, unidade_id) WHERE situacao = 'ABERTA';

-- Indicadores por período e mapa do dia: OS pela data programada, sem passar pela equipe.
CREATE INDEX idx_os_data_programada ON ordem_servico (tenant_id, data_programada) WHERE data_programada IS NOT NULL;
-- Deslocamento: o último fim (encerrar, devolver, paralisar) da mesma jornada antes de cada início.
CREATE INDEX idx_os_evento_jornada ON os_evento (jornada_id, ocorrido_em) WHERE jornada_id IS NOT NULL;
-- Jornadas por período (produtividade).
CREATE INDEX idx_jornada_inicio ON jornada (tenant_id, inicio);

ALTER TABLE sugestao_reotimizacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE sugestao_reotimizacao FORCE ROW LEVEL SECURITY;
CREATE POLICY sugestao_reotimizacao_escopo ON sugestao_reotimizacao USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

GRANT SELECT, INSERT, UPDATE ON sugestao_reotimizacao TO aurora_app;

INSERT INTO tarefa_agendada (nome) VALUES ('detectar-reotimizacao');
