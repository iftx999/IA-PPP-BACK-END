-- ADR 0043: logradouros e trechos da concessionária e o vínculo matrícula → logradouro + número (épico J, J3, doc 10).
-- Exceção à RN-09 aprovada em 17/11/2026 (doc 10, 10.11): o número do imóvel fica na Aurora com acesso restrito e
-- auditado, sem nome do morador. Logradouro e trecho não são dados pessoais.

INSERT INTO permissao (codigo, descricao) VALUES
    ('logradouro:gerenciar', 'Importar e corrigir logradouros, trechos e o vínculo dos imóveis com logradouro e número');

CREATE TABLE logradouro (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant (id),
    -- Município do logradouro.
    unidade_id     uuid NOT NULL,
    -- Código do logradouro no cadastro da concessionária (chave da reimportação, única no município).
    codigo         text NOT NULL CHECK (char_length(codigo) BETWEEN 1 AND 40),
    tipo           text CHECK (char_length(tipo) <= 30),
    nome           text NOT NULL CHECK (char_length(nome) BETWEEN 1 AND 120),
    bairro         text CHECK (char_length(bairro) <= 80),
    -- Nome sem acento, em minúsculas, para a busca.
    nome_busca     text NOT NULL,
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    atualizado_por uuid,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, unidade_id, codigo),
    FOREIGN KEY (tenant_id, unidade_id)     REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, atualizado_por) REFERENCES usuario (tenant_id, id)
);

CREATE INDEX idx_logradouro_codigo ON logradouro (tenant_id, codigo);
CREATE INDEX idx_logradouro_busca ON logradouro USING gin (nome_busca gin_trgm_ops);

CREATE TABLE trecho (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant (id),
    -- Município (o do logradouro): o código do trecho é único nele.
    unidade_id     uuid NOT NULL,
    logradouro_id  uuid NOT NULL,
    codigo         text NOT NULL CHECK (char_length(codigo) BETWEEN 1 AND 40),
    geom           geometry(LineString, 4674) NOT NULL CHECK (st_isvalid(geom) AND NOT st_isempty(geom)),
    extensao_m     numeric(10, 1) NOT NULL CHECK (extensao_m >= 0),
    numero_inicial integer CHECK (numero_inicial >= 0),
    numero_final   integer CHECK (numero_final >= 0),
    -- Lado da numeração coberto pelo trecho.
    lado           text NOT NULL CHECK (lado IN ('PAR', 'IMPAR', 'AMBOS')),
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, unidade_id, codigo),
    FOREIGN KEY (tenant_id, unidade_id)    REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, logradouro_id) REFERENCES logradouro (tenant_id, id),
    CONSTRAINT trecho_faixa CHECK (numero_inicial IS NULL OR numero_final IS NULL OR numero_inicial <= numero_final)
);

CREATE INDEX idx_trecho_logradouro ON trecho (tenant_id, logradouro_id);
CREATE INDEX idx_trecho_geom ON trecho USING gist (geom);

-- Dado pessoal quando ligado à matrícula: leitura completa só com dados_pessoais:ler, auditada (doc 09, 9.7).
CREATE TABLE imovel_endereco (
    tenant_id      uuid NOT NULL REFERENCES tenant (id),
    codigo_origem  text NOT NULL,
    -- Unidade do imóvel (a da situação mensal mais recente): base do escopo.
    unidade_id     uuid NOT NULL,
    logradouro_id  uuid NOT NULL,
    trecho_id      uuid,
    numero         text CHECK (char_length(numero) <= 10),
    -- Parte numérica do número (ordena o lote e escolhe o trecho pela faixa); nulo em "S/N".
    numero_inteiro integer CHECK (numero_inteiro >= 0),
    complemento    text CHECK (char_length(complemento) <= 60),
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    atualizado_por uuid,
    PRIMARY KEY (tenant_id, codigo_origem),
    FOREIGN KEY (tenant_id, unidade_id)     REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, logradouro_id)  REFERENCES logradouro (tenant_id, id),
    FOREIGN KEY (tenant_id, trecho_id)      REFERENCES trecho (tenant_id, id),
    FOREIGN KEY (tenant_id, atualizado_por) REFERENCES usuario (tenant_id, id)
);

CREATE INDEX idx_imovel_endereco_logradouro ON imovel_endereco (tenant_id, logradouro_id, numero_inteiro);
CREATE INDEX idx_imovel_endereco_trecho ON imovel_endereco (tenant_id, trecho_id);

-- Histórico das importações (o arquivo em si não é guardado; o hash identifica).
CREATE TABLE importacao_logradouro (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL REFERENCES tenant (id),
    tipo        text NOT NULL CHECK (tipo IN ('TRECHOS', 'ENDERECOS')),
    unidade_id  uuid,
    arquivo     text NOT NULL CHECK (char_length(arquivo) <= 255),
    sha256      text NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    lidos       integer NOT NULL CHECK (lidos >= 0),
    novos       integer NOT NULL CHECK (novos >= 0),
    atualizados integer NOT NULL CHECK (atualizados >= 0),
    rejeitados  integer NOT NULL CHECK (rejeitados >= 0),
    feita_por   uuid NOT NULL,
    feita_em    timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, feita_por)  REFERENCES usuario (tenant_id, id),
    CONSTRAINT importacao_trechos_com_unidade CHECK (tipo <> 'TRECHOS' OR unidade_id IS NOT NULL)
);

CREATE INDEX idx_importacao_logradouro ON importacao_logradouro (tenant_id, feita_em DESC);

ALTER TABLE logradouro ENABLE ROW LEVEL SECURITY;
ALTER TABLE logradouro FORCE ROW LEVEL SECURITY;
CREATE POLICY logradouro_escopo ON logradouro USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos()) OR u.caminho @> ANY (app_escopos())));

ALTER TABLE trecho ENABLE ROW LEVEL SECURITY;
ALTER TABLE trecho FORCE ROW LEVEL SECURITY;
CREATE POLICY trecho_pelo_logradouro ON trecho USING (
    tenant_id = app_tenant() AND logradouro_id IN (SELECT id FROM logradouro));

ALTER TABLE imovel_endereco ENABLE ROW LEVEL SECURITY;
ALTER TABLE imovel_endereco FORCE ROW LEVEL SECURITY;
CREATE POLICY imovel_endereco_escopo ON imovel_endereco USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE importacao_logradouro ENABLE ROW LEVEL SECURITY;
ALTER TABLE importacao_logradouro FORCE ROW LEVEL SECURITY;
CREATE POLICY importacao_logradouro_tenant ON importacao_logradouro USING (tenant_id = app_tenant());

GRANT SELECT, INSERT, UPDATE ON logradouro, trecho, imovel_endereco TO aurora_app;
GRANT SELECT, INSERT ON importacao_logradouro TO aurora_app;

-- Gestor de serviços e administrador importam e corrigem logradouros (clientes existentes; os novos pelo
-- provisionamento).
ALTER TABLE perfil NO FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao NO FORCE ROW LEVEL SECURITY;

INSERT INTO perfil_permissao (tenant_id, perfil_id, permissao)
SELECT p.tenant_id, p.id, 'logradouro:gerenciar'
  FROM perfil p
 WHERE p.sistema AND p.nome IN ('Gestor de serviços', 'Administrador do tenant')
ON CONFLICT DO NOTHING;

ALTER TABLE perfil FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao FORCE ROW LEVEL SECURITY;
