-- ADR 0036: campanhas de ligação e ordens de serviço (OS) para equipes próprias ou terceirizadas, conferidas pela
-- carga mensal (RN-19). A OS guarda só a matrícula e a unidade do imóvel (sem nome nem endereço).

INSERT INTO permissao (codigo, descricao) VALUES
    ('campanha:gerenciar', 'Cadastrar equipes, criar campanhas de ligação e distribuir ordens de serviço'),
    ('os:executar',        'Registrar o resultado das ordens de serviço das equipes de que faz parte');

CREATE TABLE equipe (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenant (id),
    unidade_id uuid NOT NULL,
    nome       text NOT NULL CHECK (char_length(nome) BETWEEN 2 AND 80),
    tipo       text NOT NULL CHECK (tipo IN ('PROPRIA', 'TERCEIRIZADA')),
    empresa    text CHECK (char_length(empresa) BETWEEN 2 AND 120),
    ativa      boolean NOT NULL DEFAULT true,
    criada_em  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, nome),
    FOREIGN KEY (tenant_id, unidade_id) REFERENCES unidade (tenant_id, id),
    CONSTRAINT equipe_empresa_so_terceirizada CHECK ((tipo = 'TERCEIRIZADA') = (empresa IS NOT NULL))
);

CREATE TABLE equipe_membro (
    tenant_id  uuid NOT NULL,
    equipe_id  uuid NOT NULL,
    usuario_id uuid NOT NULL,
    PRIMARY KEY (equipe_id, usuario_id),
    FOREIGN KEY (tenant_id, equipe_id)  REFERENCES equipe (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id)
);

CREATE INDEX idx_equipe_membro_usuario ON equipe_membro (tenant_id, usuario_id);

CREATE TABLE campanha (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL REFERENCES tenant (id),
    unidade_id          uuid NOT NULL,
    nome                text NOT NULL CHECK (char_length(nome) BETWEEN 3 AND 120),
    origem              text NOT NULL CHECK (origem IN ('SEM_LIGACAO', 'AREA_DO_MAPA', 'SETOR')),
    -- Recorte usado na criação (situação, motivo do corte, área), para explicar de onde vieram as OS.
    criterios           jsonb NOT NULL,
    acao                text NOT NULL CHECK (acao IN ('LIGACAO', 'VISTORIA', 'NOTIFICACAO')),
    competencia         date NOT NULL CHECK (extract(day FROM competencia) = 1),
    prazo               date NOT NULL,
    status              text NOT NULL DEFAULT 'ATIVA' CHECK (status IN ('ATIVA', 'ENCERRADA', 'CANCELADA')),
    -- Adesão da unidade da campanha na criação: base do "efeito real" no painel.
    ligados_na_criacao  bigint NOT NULL CHECK (ligados_na_criacao >= 0),
    com_rede_na_criacao bigint NOT NULL CHECK (com_rede_na_criacao >= 0),
    criada_por          uuid NOT NULL,
    criada_em           timestamptz NOT NULL DEFAULT now(),
    finalizada_por      uuid,
    finalizada_em       timestamptz,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id)     REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, criada_por)     REFERENCES usuario (tenant_id, id),
    FOREIGN KEY (tenant_id, finalizada_por) REFERENCES usuario (tenant_id, id),
    CONSTRAINT campanha_finalizada CHECK ((status = 'ATIVA') = (finalizada_em IS NULL))
);

CREATE INDEX idx_campanha_status ON campanha (tenant_id, status, criada_em DESC);

CREATE TABLE ordem_servico (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL REFERENCES tenant (id),
    campanha_id         uuid NOT NULL,
    numero              integer NOT NULL CHECK (numero > 0),
    codigo_origem       text NOT NULL,
    unidade_id          uuid NOT NULL,
    tipo                text NOT NULL CHECK (tipo IN ('VISTORIA', 'LIGACAO_NOVA', 'RELIGACAO', 'NOTIFICACAO')),
    situacao_na_criacao text NOT NULL CHECK (situacao_na_criacao IN ('FACTIVEL', 'CORTADO')),
    prioridade          text NOT NULL CHECK (prioridade IN ('ALTA', 'MEDIA', 'BAIXA')),
    prazo               date NOT NULL,
    equipe_id           uuid,
    enviada_em          timestamptz,
    status              text NOT NULL DEFAULT 'PLANEJADA' CHECK (status IN (
                            'PLANEJADA', 'EM_CAMPO', 'REAGENDADA', 'EXECUTADA', 'RECUSADA', 'INVIAVEL', 'CANCELADA')),
    motivo              text CHECK (motivo IN (
                            'SEM_INTERESSE', 'CUSTO_DA_LIGACAO', 'SOLUCAO_PROPRIA', 'DEBITO_PENDENTE',
                            'SEM_REDE_NA_RUA', 'IMOVEL_ABAIXO_DA_REDE', 'IMOVEL_DESOCUPADO', 'OUTRO')),
    observacao          text CHECK (char_length(observacao) <= 500),
    reagendada_para     date,
    resultado_em        timestamptz,
    resultado_por       uuid,
    conferencia         text NOT NULL CHECK (conferencia IN ('NAO_SE_APLICA', 'PENDENTE', 'CONFIRMADA', 'DIVERGENTE')),
    -- Competência da carga que confirmou a ligação.
    confirmada_em       date,
    criada_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (campanha_id, numero),
    UNIQUE (campanha_id, codigo_origem),
    FOREIGN KEY (tenant_id, campanha_id)   REFERENCES campanha (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id)    REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, equipe_id)     REFERENCES equipe (tenant_id, id),
    FOREIGN KEY (tenant_id, resultado_por) REFERENCES usuario (tenant_id, id),
    CONSTRAINT os_motivo_com_status CHECK ((status IN ('RECUSADA', 'INVIAVEL')) = (motivo IS NOT NULL)),
    CONSTRAINT os_reagendada_com_data CHECK ((status = 'REAGENDADA') = (reagendada_para IS NOT NULL)),
    CONSTRAINT os_em_campo_com_equipe CHECK (status IN ('PLANEJADA', 'CANCELADA') OR equipe_id IS NOT NULL),
    CONSTRAINT os_confirmada_com_competencia CHECK ((conferencia = 'CONFIRMADA') = (confirmada_em IS NOT NULL))
);

-- Um imóvel tem no máximo uma OS em aberto, somadas todas as campanhas.
CREATE UNIQUE INDEX uq_os_aberta_por_imovel ON ordem_servico (tenant_id, codigo_origem)
    WHERE status IN ('PLANEJADA', 'EM_CAMPO', 'REAGENDADA');
CREATE INDEX idx_os_campanha ON ordem_servico (tenant_id, campanha_id, status);
CREATE INDEX idx_os_equipe ON ordem_servico (tenant_id, equipe_id, status);
CREATE INDEX idx_os_conferencia ON ordem_servico (tenant_id, conferencia) WHERE conferencia IN ('PENDENTE', 'DIVERGENTE');

ALTER TABLE equipe ENABLE ROW LEVEL SECURITY;
ALTER TABLE equipe FORCE ROW LEVEL SECURITY;
CREATE POLICY equipe_escopo ON equipe USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE equipe_membro ENABLE ROW LEVEL SECURITY;
ALTER TABLE equipe_membro FORCE ROW LEVEL SECURITY;
CREATE POLICY equipe_membro_pela_equipe ON equipe_membro USING (
    tenant_id = app_tenant() AND equipe_id IN (SELECT id FROM equipe));

ALTER TABLE campanha ENABLE ROW LEVEL SECURITY;
ALTER TABLE campanha FORCE ROW LEVEL SECURITY;
CREATE POLICY campanha_escopo ON campanha USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE ordem_servico ENABLE ROW LEVEL SECURITY;
ALTER TABLE ordem_servico FORCE ROW LEVEL SECURITY;
CREATE POLICY ordem_servico_escopo ON ordem_servico USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

GRANT SELECT, INSERT, UPDATE ON equipe, campanha, ordem_servico TO aurora_app;
GRANT SELECT, INSERT, DELETE ON equipe_membro TO aurora_app;

-- Alerta de OS executada que a carga não confirmou.
ALTER TABLE alerta DROP CONSTRAINT alerta_tipo_check;
ALTER TABLE alerta ADD CONSTRAINT alerta_tipo_check CHECK (tipo IN ('INDICADOR_ABAIXO_DA_META', 'META_FORA_DO_RITMO',
    'FONTE_ATRASADA', 'PRAZO_CONTRATUAL', 'TRILHA_ALTERADA', 'ORDEM_DIVERGENTE'));

-- Perfis do sistema dos clientes existentes (os novos recebem pelo provisionamento): o gestor comercial e o
-- administrador gerenciam campanhas; o administrador também executa OS; novo perfil "Equipe de campo".
-- O dono está sujeito ao RLS forçado: suspenso só nesta transação (como na V7 e na V23).
ALTER TABLE perfil NO FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao NO FORCE ROW LEVEL SECURITY;

INSERT INTO perfil_permissao (tenant_id, perfil_id, permissao)
SELECT p.tenant_id, p.id, n.permissao
  FROM perfil p
  JOIN (VALUES ('Administrador do tenant', 'campanha:gerenciar'),
               ('Administrador do tenant', 'os:executar'),
               ('Gestor comercial', 'campanha:gerenciar')) AS n (perfil, permissao) ON n.perfil = p.nome
 WHERE p.sistema
ON CONFLICT DO NOTHING;

INSERT INTO perfil (id, tenant_id, nome, sistema, descricao)
SELECT gen_random_uuid(), p.tenant_id, 'Equipe de campo', true, 'Ordens de serviço das equipes de que faz parte'
  FROM (SELECT DISTINCT tenant_id FROM perfil WHERE sistema) p
 WHERE NOT EXISTS (SELECT 1 FROM perfil x WHERE x.tenant_id = p.tenant_id AND x.nome = 'Equipe de campo');

INSERT INTO perfil_permissao (tenant_id, perfil_id, permissao)
SELECT p.tenant_id, p.id, n.permissao
  FROM perfil p
 CROSS JOIN (VALUES ('os:executar'), ('dados_pessoais:ler'), ('unidade:ler')) AS n (permissao)
 WHERE p.sistema AND p.nome = 'Equipe de campo'
ON CONFLICT DO NOTHING;

ALTER TABLE perfil FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao FORCE ROW LEVEL SECURITY;
