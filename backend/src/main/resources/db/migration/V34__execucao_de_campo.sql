-- ADR 0045: execução de campo na API (épico J, J8–J11 e J13; doc 10, 10.9). O app registra cada ação como um evento
-- com identificador gerado no celular e sequência por OS; o servidor aplica cada evento uma única vez e na ordem
-- (RN-26). Jornada da equipe, posições (RN-27), fotos enviadas em partes, conflitos com a programação (10.9.5) e
-- os celulares cadastrados, que podem ser revogados (J13).

INSERT INTO permissao (codigo, descricao) VALUES
    ('dispositivo:gerenciar', 'Ver e revogar os celulares do app de campo');

-- P8: "início fora do local" a mais desta distância do alvo (RN-22).
ALTER TABLE tenant ADD COLUMN campo_raio_inicio_m integer NOT NULL DEFAULT 300
    CHECK (campo_raio_inicio_m BETWEEN 50 AND 5000);

-- ----- Celulares (J13) -----

-- O id é gerado no celular no primeiro login e enviado em todas as chamadas do app.
CREATE TABLE dispositivo (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant (id),
    usuario_id     uuid NOT NULL,
    modelo         text NOT NULL CHECK (char_length(modelo) BETWEEN 1 AND 80),
    sistema        text NOT NULL CHECK (char_length(sistema) BETWEEN 1 AND 40),
    versao_app     text NOT NULL CHECK (char_length(versao_app) BETWEEN 1 AND 20),
    cadastrado_em  timestamptz NOT NULL DEFAULT now(),
    ultimo_contato timestamptz NOT NULL DEFAULT now(),
    revogado_em    timestamptz,
    revogado_por   uuid,
    -- O app confirmou que apagou os dados locais depois da revogação.
    apagado_em     timestamptz,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, usuario_id)   REFERENCES usuario (tenant_id, id),
    FOREIGN KEY (tenant_id, revogado_por) REFERENCES usuario (tenant_id, id),
    CONSTRAINT dispositivo_revogado_por CHECK ((revogado_em IS NULL) = (revogado_por IS NULL))
);

CREATE INDEX idx_dispositivo_usuario ON dispositivo (tenant_id, usuario_id);

-- ----- Jornada (J8) -----

CREATE TABLE jornada (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL REFERENCES tenant (id),
    equipe_id       uuid NOT NULL,
    usuario_id      uuid NOT NULL,
    dispositivo_id  uuid NOT NULL,
    inicio          timestamptz NOT NULL,
    fim             timestamptz,
    posicao_inicio  geometry(Point, 4674),
    precisao_inicio numeric(8, 1) CHECK (precisao_inicio >= 0),
    recebida_em     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, equipe_id)      REFERENCES equipe (tenant_id, id),
    FOREIGN KEY (tenant_id, usuario_id)     REFERENCES usuario (tenant_id, id),
    FOREIGN KEY (tenant_id, dispositivo_id) REFERENCES dispositivo (tenant_id, id),
    CONSTRAINT jornada_periodo CHECK (fim IS NULL OR fim >= inicio)
);

-- Uma jornada aberta por equipe.
CREATE UNIQUE INDEX uq_jornada_aberta ON jornada (tenant_id, equipe_id) WHERE fim IS NULL;
CREATE INDEX idx_jornada_equipe ON jornada (tenant_id, equipe_id, inicio);

-- RN-27: só com a jornada aberta; guarda de 90 dias (P4), depois apagada.
CREATE TABLE posicao_equipe (
    tenant_id   uuid NOT NULL,
    jornada_id  uuid NOT NULL,
    equipe_id   uuid NOT NULL,
    momento     timestamptz NOT NULL,
    geom        geometry(Point, 4674) NOT NULL,
    precisao_m  numeric(8, 1) CHECK (precisao_m >= 0),
    recebida_em timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (jornada_id, momento),
    FOREIGN KEY (tenant_id, jornada_id) REFERENCES jornada (tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_posicao_equipe ON posicao_equipe (tenant_id, equipe_id, momento DESC);
CREATE INDEX idx_posicao_momento ON posicao_equipe (momento);

-- ----- Execução da OS (J9, RN-22, RN-26) -----

ALTER TABLE ordem_servico
    -- Última sequência de evento do app processada (aplicada, em conflito ou recusada).
    ADD COLUMN ultima_sequencia     integer NOT NULL DEFAULT 0 CHECK (ultima_sequencia >= 0),
    -- Primeiro início (hora do celular) e se foi longe do alvo (RN-22).
    ADD COLUMN iniciada_em          timestamptz,
    ADD COLUMN inicio_fora_do_local boolean NOT NULL DEFAULT false,
    -- Valores dos parâmetros da versão do serviço no encerramento pelo app (chave → valor).
    ADD COLUMN valores              jsonb;

CREATE TABLE os_evento (
    -- Gerado no celular: a mesma ação reenviada é reconhecida (idempotência).
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL REFERENCES tenant (id),
    os_id               uuid NOT NULL,
    unidade_id          uuid NOT NULL,
    sequencia           integer NOT NULL CHECK (sequencia > 0),
    tipo                text NOT NULL CHECK (tipo IN ('INICIAR', 'PARALISAR', 'RETOMAR', 'DEVOLVER', 'ENCERRAR')),
    -- APLICADO; CONFLITO (registrado, com conflito para o programador); SUPERADO (vale a programação);
    -- AGUARDANDO (sequência anterior ainda não chegou); REJEITADO (dados inválidos).
    situacao            text NOT NULL CHECK (situacao IN ('APLICADO', 'CONFLITO', 'SUPERADO', 'AGUARDANDO', 'REJEITADO')),
    mensagem            text,
    resultado           text CHECK (resultado IN ('EXECUTADA', 'NAO_EXECUTADA')),
    motivo_id           uuid,
    observacao          text CHECK (char_length(observacao) <= 500),
    quantidade          numeric(12, 2) CHECK (quantidade >= 0),
    valores             jsonb,
    -- Relógio do celular (usado nos indicadores) e do servidor (auditoria).
    ocorrido_em         timestamptz NOT NULL,
    recebido_em         timestamptz NOT NULL DEFAULT now(),
    processado_em       timestamptz,
    posicao             geometry(Point, 4674),
    precisao_m          numeric(8, 1) CHECK (precisao_m >= 0),
    -- Distância até o alvo no início.
    distancia_m         numeric(10, 1),
    -- RELOGIO, FORA_DO_LOCAL, SEM_GPS, SEM_JORNADA, EXECUCAO_SIMULTANEA, APARELHO_REVOGADO.
    avisos              text[] NOT NULL DEFAULT '{}',
    equipe_id           uuid NOT NULL,
    usuario_id          uuid NOT NULL,
    dispositivo_id      uuid NOT NULL,
    jornada_id          uuid,
    versao_pacote       bigint,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, os_id)          REFERENCES ordem_servico (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id)     REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, motivo_id)      REFERENCES motivo (tenant_id, id),
    FOREIGN KEY (tenant_id, equipe_id)      REFERENCES equipe (tenant_id, id),
    FOREIGN KEY (tenant_id, usuario_id)     REFERENCES usuario (tenant_id, id),
    FOREIGN KEY (tenant_id, dispositivo_id) REFERENCES dispositivo (tenant_id, id),
    FOREIGN KEY (tenant_id, jornada_id)     REFERENCES jornada (tenant_id, id)
);

-- RN-26: uma sequência da OS é de um evento só (o recusado por sequência repetida não ocupa).
CREATE UNIQUE INDEX uq_os_evento_sequencia ON os_evento (os_id, sequencia) WHERE situacao <> 'REJEITADO';
CREATE INDEX idx_os_evento_os ON os_evento (tenant_id, os_id, sequencia);
CREATE INDEX idx_os_evento_aguardando ON os_evento (os_id, sequencia) WHERE situacao = 'AGUARDANDO';

-- Fotos (10.9.3): enviadas depois dos eventos, em partes, retomando de onde parou.
CREATE TABLE os_anexo (
    -- Gerado no celular; o evento de encerramento referencia a foto por ele.
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant (id),
    os_id          uuid NOT NULL,
    unidade_id     uuid NOT NULL,
    -- Parâmetro de foto do serviço; nulo: foto da devolução ou avulsa.
    parametro      text CHECK (parametro ~ '^[a-z][a-z0-9_]{0,39}$'),
    tipo_midia     text NOT NULL CHECK (tipo_midia IN ('image/jpeg', 'image/png', 'image/webp')),
    tamanho        integer NOT NULL CHECK (tamanho BETWEEN 1 AND 5242880),
    recebido       integer NOT NULL DEFAULT 0,
    -- SHA-256 informado pelo celular, conferido ao completar.
    sha256         text NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    conteudo       bytea NOT NULL DEFAULT '\x',
    situacao       text NOT NULL DEFAULT 'RECEBENDO' CHECK (situacao IN ('RECEBENDO', 'DISPONIVEL', 'RECUSADO')),
    recusa         text,
    usuario_id     uuid NOT NULL,
    dispositivo_id uuid NOT NULL,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    concluido_em   timestamptz,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, os_id)          REFERENCES ordem_servico (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id)     REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, usuario_id)     REFERENCES usuario (tenant_id, id),
    FOREIGN KEY (tenant_id, dispositivo_id) REFERENCES dispositivo (tenant_id, id),
    CONSTRAINT os_anexo_recebido CHECK (recebido BETWEEN 0 AND tamanho AND recebido = octet_length(conteudo))
);

CREATE INDEX idx_os_anexo_os ON os_anexo (tenant_id, os_id);

-- J11 / 10.9.5: o que o campo fez × o que a programação mudou, com a decisão registrada.
CREATE TABLE os_conflito (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL REFERENCES tenant (id),
    os_id        uuid NOT NULL,
    unidade_id   uuid NOT NULL,
    evento_id    uuid NOT NULL,
    tipo         text NOT NULL CHECK (tipo IN ('OS_CANCELADA', 'OUTRA_EQUIPE', 'JA_ENCERRADA')),
    descricao    text NOT NULL CHECK (char_length(descricao) <= 500),
    criado_em    timestamptz NOT NULL DEFAULT now(),
    decisao      text CHECK (char_length(decisao) BETWEEN 3 AND 500),
    decidido_por uuid,
    decidido_em  timestamptz,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, os_id)        REFERENCES ordem_servico (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id)   REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, evento_id)    REFERENCES os_evento (tenant_id, id),
    FOREIGN KEY (tenant_id, decidido_por) REFERENCES usuario (tenant_id, id),
    CONSTRAINT os_conflito_decisao CHECK ((decisao IS NULL) = (decidido_em IS NULL)
                                         AND (decisao IS NULL) = (decidido_por IS NULL))
);

CREATE INDEX idx_os_conflito_aberto ON os_conflito (tenant_id, criado_em) WHERE decidido_em IS NULL;
CREATE INDEX idx_os_conflito_os ON os_conflito (tenant_id, os_id);

-- ----- Acesso -----

ALTER TABLE dispositivo ENABLE ROW LEVEL SECURITY;
ALTER TABLE dispositivo FORCE ROW LEVEL SECURITY;
CREATE POLICY dispositivo_tenant ON dispositivo USING (tenant_id = app_tenant());

ALTER TABLE jornada ENABLE ROW LEVEL SECURITY;
ALTER TABLE jornada FORCE ROW LEVEL SECURITY;
CREATE POLICY jornada_pela_equipe ON jornada USING (
    tenant_id = app_tenant() AND equipe_id IN (SELECT id FROM equipe));

ALTER TABLE posicao_equipe ENABLE ROW LEVEL SECURITY;
ALTER TABLE posicao_equipe FORCE ROW LEVEL SECURITY;
CREATE POLICY posicao_equipe_pela_equipe ON posicao_equipe USING (
    tenant_id = app_tenant() AND equipe_id IN (SELECT id FROM equipe));

ALTER TABLE os_evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE os_evento FORCE ROW LEVEL SECURITY;
CREATE POLICY os_evento_escopo ON os_evento USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE os_anexo ENABLE ROW LEVEL SECURITY;
ALTER TABLE os_anexo FORCE ROW LEVEL SECURITY;
CREATE POLICY os_anexo_escopo ON os_anexo USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE os_conflito ENABLE ROW LEVEL SECURITY;
ALTER TABLE os_conflito FORCE ROW LEVEL SECURITY;
CREATE POLICY os_conflito_escopo ON os_conflito USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

GRANT SELECT, INSERT, UPDATE ON dispositivo, jornada, os_evento, os_anexo, os_conflito TO aurora_app;
GRANT SELECT, INSERT, DELETE ON posicao_equipe TO aurora_app;

INSERT INTO tarefa_agendada (nome) VALUES ('expurgar-posicoes');

-- Perfis do sistema dos clientes existentes (os novos pelo provisionamento).
ALTER TABLE perfil NO FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao NO FORCE ROW LEVEL SECURITY;

INSERT INTO perfil_permissao (tenant_id, perfil_id, permissao)
SELECT p.tenant_id, p.id, 'dispositivo:gerenciar'
  FROM perfil p
 WHERE p.sistema AND p.nome IN ('Gestor de serviços', 'Administrador do tenant')
ON CONFLICT DO NOTHING;

ALTER TABLE perfil FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao FORCE ROW LEVEL SECURITY;
