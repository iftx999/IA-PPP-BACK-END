-- ADR 0044: OS genérica do catálogo (épico J, J4 e J5; doc 10, 10.6). A OS deixa de ter os quatro tipos fixos da V28 e
-- passa a guardar o serviço e a versão com que nasceu (RN-20); o alvo é um imóvel ou um trecho; pode nascer de uma
-- campanha, avulsa ou de um lote por logradouro. Novo ciclo: PLANEJADA → PROGRAMADA → EM_EXECUCAO ⇄ PARALISADA →
-- ENCERRADA | DEVOLVIDA | CANCELADA. Prazo com hora (emergencial em horas corridas; demais em dias úteis, RN-21) e
-- feriados municipais por unidade (P6). As OS existentes são migradas (doc 10, 10.6).

INSERT INTO permissao (codigo, descricao) VALUES
    ('os:programar', 'Criar OS avulsas, de trecho e lotes por logradouro; programar, reprogramar e cancelar OS');

-- Feriados municipais (ou de qualquer unidade: valem para ela e as de baixo). Os nacionais são calculados.
CREATE TABLE feriado (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenant (id),
    unidade_id uuid NOT NULL,
    data       date NOT NULL,
    nome       text NOT NULL CHECK (char_length(nome) BETWEEN 2 AND 80),
    criado_por uuid NOT NULL,
    criado_em  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, unidade_id, data),
    FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, criado_por) REFERENCES usuario (tenant_id, id)
);

CREATE INDEX idx_feriado_data ON feriado (tenant_id, data);

-- Número da OS único no cliente (as da V28 eram numeradas por campanha).
CREATE TABLE contador_os (
    tenant_id uuid PRIMARY KEY REFERENCES tenant (id),
    ultimo    bigint NOT NULL CHECK (ultimo >= 0)
);

-- Lote: uma OS por imóvel do logradouro (ou da faixa de numeração), gerado de uma vez (RN-24).
CREATE TABLE lote_os (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL REFERENCES tenant (id),
    -- Município do logradouro.
    unidade_id        uuid NOT NULL,
    logradouro_id     uuid NOT NULL,
    servico_id        uuid NOT NULL,
    servico_versao_id uuid NOT NULL,
    numero_inicial    integer CHECK (numero_inicial >= 0),
    numero_final      integer CHECK (numero_final >= 0),
    lado              text NOT NULL CHECK (lado IN ('PAR', 'IMPAR', 'AMBOS')),
    prioridade        text NOT NULL CHECK (prioridade IN ('EMERGENCIAL', 'ALTA', 'MEDIA', 'BAIXA')),
    criadas           integer NOT NULL CHECK (criadas >= 0),
    -- Imóveis que já tinham OS aberta do mesmo serviço.
    ignoradas         integer NOT NULL CHECK (ignoradas >= 0),
    criado_por        uuid NOT NULL,
    criado_em         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id)        REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, logradouro_id)     REFERENCES logradouro (tenant_id, id),
    FOREIGN KEY (tenant_id, servico_id)        REFERENCES servico (tenant_id, id),
    FOREIGN KEY (tenant_id, servico_versao_id) REFERENCES servico_versao (tenant_id, id),
    FOREIGN KEY (tenant_id, criado_por)        REFERENCES usuario (tenant_id, id),
    CONSTRAINT lote_faixa CHECK (numero_inicial IS NULL OR numero_final IS NULL OR numero_inicial <= numero_final)
);

-- ----- Ordem de serviço -----

ALTER TABLE ordem_servico
    ADD COLUMN servico_id           uuid,
    ADD COLUMN servico_versao_id    uuid,
    ADD COLUMN alvo                 text NOT NULL DEFAULT 'IMOVEL' CHECK (alvo IN ('IMOVEL', 'TRECHO')),
    ADD COLUMN trecho_id            uuid,
    ADD COLUMN lote_id              uuid,
    ADD COLUMN prazo_em             timestamptz,
    ADD COLUMN data_programada      date,
    ADD COLUMN resultado            text CHECK (resultado IN ('EXECUTADA', 'NAO_EXECUTADA')),
    ADD COLUMN motivo_id            uuid,
    ADD COLUMN quantidade_prevista  numeric(12, 2) CHECK (quantidade_prevista > 0),
    ADD COLUMN quantidade_executada numeric(12, 2) CHECK (quantidade_executada >= 0),
    -- Observação de quem criou a OS (a coluna "observacao" é a do último movimento: resultado, devolução, cancelamento).
    ADD COLUMN instrucoes           text CHECK (char_length(instrucoes) <= 500),
    ADD COLUMN criada_por           uuid,
    -- Último movimento (programar, devolver, cancelar, encerrar): quando e quem.
    ADD COLUMN movimentada_em       timestamptz,
    ADD COLUMN movimentada_por      uuid;

ALTER TABLE ordem_servico ALTER COLUMN alvo DROP DEFAULT;

ALTER TABLE ordem_servico
    DROP CONSTRAINT os_motivo_com_status,
    DROP CONSTRAINT os_reagendada_com_data,
    DROP CONSTRAINT os_em_campo_com_equipe,
    DROP CONSTRAINT ordem_servico_status_check,
    DROP CONSTRAINT ordem_servico_prioridade_check,
    -- OS avulsa guarda a situação que o imóvel tiver (inclusive ligado); lote e trecho, nenhuma.
    DROP CONSTRAINT ordem_servico_situacao_na_criacao_check,
    DROP CONSTRAINT ordem_servico_campanha_id_numero_key;

DROP INDEX uq_os_aberta_por_imovel;
DROP INDEX IF EXISTS idx_os_campanha;
DROP INDEX IF EXISTS idx_os_equipe;

-- Migração das OS existentes, em todos os clientes: o dono está sujeito ao RLS forçado dessas tabelas, suspenso só
-- nesta transação (como na V28 e na V31). Também vale para a validação das chaves estrangeiras novas, que consulta
-- as tabelas referenciadas como o dono.
ALTER TABLE ordem_servico NO FORCE ROW LEVEL SECURITY;
ALTER TABLE trecho NO FORCE ROW LEVEL SECURITY;
ALTER TABLE usuario NO FORCE ROW LEVEL SECURITY;
ALTER TABLE servico NO FORCE ROW LEVEL SECURITY;
ALTER TABLE servico_versao NO FORCE ROW LEVEL SECURITY;
ALTER TABLE motivo NO FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant NO FORCE ROW LEVEL SECURITY;

-- Serviço pelo código do tipo fixo (o catálogo inicial da V31 tem os quatro) e a versão atual dele.
UPDATE ordem_servico o
   SET servico_id = s.id, servico_versao_id = v.id
  FROM servico s
  JOIN servico_versao v ON v.servico_id = s.id AND v.versao = s.versao_atual
 WHERE s.tenant_id = o.tenant_id AND s.codigo = o.tipo;

-- Fim do dia do prazo antigo, no fuso da operação.
UPDATE ordem_servico
   SET prazo_em = (prazo::timestamp + time '23:59:59') AT TIME ZONE 'America/Sao_Paulo',
       quantidade_prevista = 1,
       data_programada = CASE status
                             WHEN 'REAGENDADA' THEN reagendada_para
                             WHEN 'EM_CAMPO' THEN (COALESCE(enviada_em, criada_em) AT TIME ZONE 'America/Sao_Paulo')::date
                         END,
       resultado = CASE WHEN status = 'EXECUTADA' THEN 'EXECUTADA'
                        WHEN status IN ('RECUSADA', 'INVIAVEL') THEN 'NAO_EXECUTADA' END,
       quantidade_executada = CASE WHEN status = 'EXECUTADA' THEN 1 WHEN status IN ('RECUSADA', 'INVIAVEL') THEN 0 END,
       motivo_id = (SELECT m.id FROM motivo m
                     WHERE m.tenant_id = ordem_servico.tenant_id AND m.tipo = 'NAO_EXECUCAO'
                       AND m.codigo = ordem_servico.motivo),
       movimentada_em = COALESCE(resultado_em, enviada_em),
       movimentada_por = resultado_por,
       status = CASE status
                    WHEN 'EM_CAMPO' THEN 'PROGRAMADA'
                    WHEN 'REAGENDADA' THEN 'PROGRAMADA'
                    WHEN 'EXECUTADA' THEN 'ENCERRADA'
                    WHEN 'RECUSADA' THEN 'ENCERRADA'
                    WHEN 'INVIAVEL' THEN 'ENCERRADA'
                    ELSE status
                END;

-- Numeração única no cliente, na ordem de criação.
UPDATE ordem_servico o SET numero = n.novo
  FROM (SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY criada_em, campanha_id, numero) AS novo
          FROM ordem_servico) n
 WHERE n.id = o.id;

INSERT INTO contador_os (tenant_id, ultimo)
SELECT t.id, COALESCE((SELECT max(numero) FROM ordem_servico o WHERE o.tenant_id = t.id), 0)
  FROM tenant t;

ALTER TABLE ordem_servico
    DROP COLUMN tipo,
    DROP COLUMN motivo,
    DROP COLUMN reagendada_para,
    DROP COLUMN prazo,
    ALTER COLUMN servico_id SET NOT NULL,
    ALTER COLUMN servico_versao_id SET NOT NULL,
    ALTER COLUMN prazo_em SET NOT NULL,
    ALTER COLUMN quantidade_prevista SET NOT NULL,
    ALTER COLUMN campanha_id DROP NOT NULL,
    ALTER COLUMN codigo_origem DROP NOT NULL,
    ALTER COLUMN situacao_na_criacao DROP NOT NULL,
    ALTER COLUMN numero TYPE bigint,
    ADD CONSTRAINT ordem_servico_status_check CHECK (status IN (
        'PLANEJADA', 'PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA', 'DEVOLVIDA', 'ENCERRADA', 'CANCELADA')),
    ADD CONSTRAINT ordem_servico_prioridade_check CHECK (prioridade IN ('EMERGENCIAL', 'ALTA', 'MEDIA', 'BAIXA')),
    ADD CONSTRAINT ordem_servico_numero_unico UNIQUE (tenant_id, numero),
    ADD FOREIGN KEY (tenant_id, servico_id)        REFERENCES servico (tenant_id, id),
    ADD FOREIGN KEY (tenant_id, servico_versao_id) REFERENCES servico_versao (tenant_id, id),
    ADD FOREIGN KEY (tenant_id, trecho_id)         REFERENCES trecho (tenant_id, id),
    ADD FOREIGN KEY (tenant_id, lote_id)           REFERENCES lote_os (tenant_id, id),
    ADD FOREIGN KEY (tenant_id, motivo_id)         REFERENCES motivo (tenant_id, id),
    ADD FOREIGN KEY (tenant_id, criada_por)        REFERENCES usuario (tenant_id, id),
    ADD FOREIGN KEY (tenant_id, movimentada_por)   REFERENCES usuario (tenant_id, id),
    ADD CONSTRAINT os_alvo CHECK (
        (alvo = 'IMOVEL') = (codigo_origem IS NOT NULL) AND (alvo = 'TRECHO') = (trecho_id IS NOT NULL)),
    ADD CONSTRAINT os_campanha_em_imovel CHECK (campanha_id IS NULL OR alvo = 'IMOVEL'),
    ADD CONSTRAINT os_programada_com_equipe CHECK (
        status NOT IN ('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA') OR (equipe_id IS NOT NULL AND data_programada IS NOT NULL)),
    ADD CONSTRAINT os_encerrada_com_resultado CHECK ((status = 'ENCERRADA') = (resultado IS NOT NULL)),
    ADD CONSTRAINT os_nao_executada_com_motivo CHECK (resultado IS DISTINCT FROM 'NAO_EXECUTADA' OR motivo_id IS NOT NULL),
    ADD CONSTRAINT os_devolvida_com_motivo CHECK (status <> 'DEVOLVIDA' OR motivo_id IS NOT NULL);

ALTER TABLE ordem_servico FORCE ROW LEVEL SECURITY;
ALTER TABLE servico FORCE ROW LEVEL SECURITY;
ALTER TABLE servico_versao FORCE ROW LEVEL SECURITY;
ALTER TABLE motivo FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant FORCE ROW LEVEL SECURITY;
ALTER TABLE trecho FORCE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;

-- RN-24 / J4: um imóvel (ou trecho) não tem duas OS abertas do mesmo serviço.
CREATE UNIQUE INDEX uq_os_aberta_imovel_servico ON ordem_servico (tenant_id, codigo_origem, servico_id)
    WHERE codigo_origem IS NOT NULL
      AND status IN ('PLANEJADA', 'PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA', 'DEVOLVIDA');
CREATE UNIQUE INDEX uq_os_aberta_trecho_servico ON ordem_servico (tenant_id, trecho_id, servico_id)
    WHERE trecho_id IS NOT NULL
      AND status IN ('PLANEJADA', 'PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA', 'DEVOLVIDA');
CREATE INDEX idx_os_campanha ON ordem_servico (tenant_id, campanha_id, status) WHERE campanha_id IS NOT NULL;
CREATE INDEX idx_os_equipe ON ordem_servico (tenant_id, equipe_id, data_programada, status);
CREATE INDEX idx_os_status ON ordem_servico (tenant_id, status, prazo_em);
CREATE INDEX idx_os_lote ON ordem_servico (tenant_id, lote_id) WHERE lote_id IS NOT NULL;
CREATE INDEX idx_os_imovel ON ordem_servico (tenant_id, codigo_origem) WHERE codigo_origem IS NOT NULL;

-- ----- Acesso -----

ALTER TABLE feriado ENABLE ROW LEVEL SECURITY;
ALTER TABLE feriado FORCE ROW LEVEL SECURITY;
CREATE POLICY feriado_tenant ON feriado USING (tenant_id = app_tenant());

ALTER TABLE contador_os ENABLE ROW LEVEL SECURITY;
ALTER TABLE contador_os FORCE ROW LEVEL SECURITY;
CREATE POLICY contador_os_tenant ON contador_os USING (tenant_id = app_tenant());

ALTER TABLE lote_os ENABLE ROW LEVEL SECURITY;
ALTER TABLE lote_os FORCE ROW LEVEL SECURITY;
CREATE POLICY lote_os_escopo ON lote_os USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

GRANT SELECT, INSERT, DELETE ON feriado TO aurora_app;
GRANT SELECT, INSERT, UPDATE ON contador_os TO aurora_app;
GRANT SELECT, INSERT, UPDATE ON lote_os TO aurora_app;

-- Perfis do sistema dos clientes existentes (os novos pelo provisionamento).
ALTER TABLE perfil NO FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao NO FORCE ROW LEVEL SECURITY;

INSERT INTO perfil_permissao (tenant_id, perfil_id, permissao)
SELECT p.tenant_id, p.id, 'os:programar'
  FROM perfil p
 WHERE p.sistema AND p.nome IN ('Gestor de serviços', 'Gestor comercial', 'Administrador do tenant')
ON CONFLICT DO NOTHING;

ALTER TABLE perfil FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao FORCE ROW LEVEL SECURITY;
