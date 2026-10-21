-- Contratos, documentos (PDF original, anexos e aditivos), texto por página e itens contratuais.
-- A IA propõe itens (status PROPOSTO); só a aprovação humana os torna válidos (RN-10).

CREATE TABLE contrato (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL,
    unidade_id       uuid NOT NULL,
    numero           text NOT NULL,
    objeto           text,
    poder_concedente text,
    regulador        text,
    data_assinatura  date,
    vigencia_fim     date,
    criado_em        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, numero),
    FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id)
);

CREATE TABLE documento_contrato (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid NOT NULL,
    contrato_id        uuid NOT NULL,
    tipo               text NOT NULL CHECK (tipo IN ('ORIGINAL', 'ANEXO', 'ADITIVO')),
    titulo             text NOT NULL,
    nome_arquivo       text NOT NULL,
    sha256             text NOT NULL,
    tamanho_bytes      bigint NOT NULL,
    paginas            integer,
    data_assinatura    date,
    status             text NOT NULL CHECK (status IN
                           ('RECEBIDO', 'RECONHECENDO_TEXTO', 'PROCESSANDO', 'PRONTO', 'SEM_TEXTO', 'FALHA')),
    erro               text,
    modelo_ia          text,
    itens_propostos    integer NOT NULL DEFAULT 0,
    itens_descartados  integer NOT NULL DEFAULT 0,
    enviado_por        uuid,
    criado_em          timestamptz NOT NULL DEFAULT now(),
    texto_por_ocr      boolean NOT NULL DEFAULT false,
    UNIQUE (tenant_id, id),
    UNIQUE (contrato_id, sha256),
    FOREIGN KEY (tenant_id, contrato_id) REFERENCES contrato (tenant_id, id)
);

CREATE TABLE documento_arquivo (
    documento_id uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL,
    conteudo     bytea NOT NULL,
    FOREIGN KEY (tenant_id, documento_id) REFERENCES documento_contrato (tenant_id, id)
);

CREATE TABLE documento_pagina (
    tenant_id    uuid NOT NULL,
    documento_id uuid NOT NULL,
    pagina       integer NOT NULL,
    texto        text NOT NULL,
    PRIMARY KEY (documento_id, pagina),
    FOREIGN KEY (tenant_id, documento_id) REFERENCES documento_contrato (tenant_id, id)
);

CREATE TABLE item_contratual (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL,
    contrato_id      uuid NOT NULL,
    documento_id     uuid NOT NULL,
    tipo             text NOT NULL CHECK (tipo IN
                         ('OBRIGACAO', 'INDICADOR', 'META', 'PRAZO', 'PENALIDADE', 'REAJUSTE', 'RISCO', 'OUTRO')),
    titulo           text NOT NULL,
    resumo           text NOT NULL,
    clausula         text,
    pagina           integer NOT NULL,
    trecho           text NOT NULL,
    valor_referencia numeric(18, 6),
    unidade_medida   text,
    data_limite      date,
    periodicidade    text,
    status           text NOT NULL CHECK (status IN ('PROPOSTO', 'APROVADO', 'REJEITADO', 'SUBSTITUIDO')),
    origem           text NOT NULL CHECK (origem IN ('IA', 'MANUAL')),
    modelo_ia        text,
    proposto_em      timestamptz NOT NULL DEFAULT now(),
    revisado_por     uuid,
    revisado_em      timestamptz,
    motivo_revisao   text,
    substituido_por  uuid,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, contrato_id)  REFERENCES contrato (tenant_id, id),
    FOREIGN KEY (tenant_id, documento_id) REFERENCES documento_contrato (tenant_id, id)
);

CREATE INDEX idx_item_contrato_status ON item_contratual (contrato_id, status);

-- A validação da FK lê meta inteira; com FORCE ROW LEVEL SECURITY nem o dono do schema enxerga linhas sem
-- contexto de tenant. Desliga o FORCE só durante a criação (a migração é transacional).
ALTER TABLE meta NO FORCE ROW LEVEL SECURITY;
ALTER TABLE meta
    ADD CONSTRAINT meta_item_contratual_fkey
    FOREIGN KEY (tenant_id, item_contratual_id) REFERENCES item_contratual (tenant_id, id);
ALTER TABLE meta FORCE ROW LEVEL SECURITY;

-- Embeddings dos itens para busca semântica do assistente.
CREATE TABLE item_contratual_vetor (
    item_id     uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    contrato_id uuid NOT NULL,
    modelo      text NOT NULL,
    vetor       vector(384) NOT NULL,
    criado_em   timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, item_id)     REFERENCES item_contratual (tenant_id, id),
    FOREIGN KEY (tenant_id, contrato_id) REFERENCES contrato (tenant_id, id)
);

CREATE INDEX idx_item_vetor_cosseno ON item_contratual_vetor USING hnsw (vetor vector_cosine_ops);

-- Contrato: lê quem tem a unidade do contrato no escopo, acima ou abaixo dela; escreve só abaixo.
-- As tabelas filhas seguem o contrato visível.
ALTER TABLE contrato ENABLE ROW LEVEL SECURITY;
ALTER TABLE contrato FORCE ROW LEVEL SECURITY;
CREATE POLICY contrato_leitura ON contrato FOR SELECT USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u
         WHERE u.caminho <@ ANY (app_escopos()) OR u.caminho @> ANY (app_escopos())));
CREATE POLICY contrato_escrita ON contrato FOR INSERT WITH CHECK (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));
CREATE POLICY contrato_alteracao ON contrato FOR UPDATE USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE documento_contrato ENABLE ROW LEVEL SECURITY;
ALTER TABLE documento_contrato FORCE ROW LEVEL SECURITY;
CREATE POLICY documento_pelo_contrato ON documento_contrato USING (
    tenant_id = app_tenant() AND contrato_id IN (SELECT id FROM contrato));

ALTER TABLE documento_arquivo ENABLE ROW LEVEL SECURITY;
ALTER TABLE documento_arquivo FORCE ROW LEVEL SECURITY;
CREATE POLICY arquivo_pelo_documento ON documento_arquivo USING (
    tenant_id = app_tenant() AND documento_id IN (SELECT id FROM documento_contrato));

ALTER TABLE documento_pagina ENABLE ROW LEVEL SECURITY;
ALTER TABLE documento_pagina FORCE ROW LEVEL SECURITY;
CREATE POLICY pagina_pelo_documento ON documento_pagina USING (
    tenant_id = app_tenant() AND documento_id IN (SELECT id FROM documento_contrato));

ALTER TABLE item_contratual ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_contratual FORCE ROW LEVEL SECURITY;
CREATE POLICY item_pelo_contrato ON item_contratual USING (
    tenant_id = app_tenant() AND contrato_id IN (SELECT id FROM contrato));

ALTER TABLE item_contratual_vetor ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_contratual_vetor FORCE ROW LEVEL SECURITY;
CREATE POLICY vetor_pelo_contrato ON item_contratual_vetor USING (
    tenant_id = app_tenant() AND contrato_id IN (SELECT id FROM contrato));

GRANT SELECT, INSERT, UPDATE ON contrato, documento_contrato, documento_pagina,
                                 item_contratual, item_contratual_vetor TO aurora_app;
GRANT SELECT, INSERT ON documento_arquivo TO aurora_app;
