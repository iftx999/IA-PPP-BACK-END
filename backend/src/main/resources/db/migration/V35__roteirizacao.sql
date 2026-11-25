-- ADR 0046: roteirização das OS (J6, J7, RN-23, RN-25; doc 10, 10.8). A equipe ganha o que a rota precisa (base,
-- horário de jornada, tipo de veículo e habilidades) e a RN-23 passa a valer por inteiro na programação. Cada pedido
-- de otimização vira uma "roteirização" de um dia: uma rota por equipe, com as paradas em ordem, e a lista das OS
-- que não couberam (com o motivo). Só a roteirização publicada mexe nas OS. Resultado sempre é estimativa (RN-25).

-- ----- Equipe -----

-- O dono está sujeito ao RLS forçado (inclusive ao validar as restrições novas): suspenso só nesta transação,
-- como na V28.
ALTER TABLE equipe NO FORCE ROW LEVEL SECURITY;
ALTER TABLE habilidade NO FORCE ROW LEVEL SECURITY;
ALTER TABLE tipo_veiculo NO FORCE ROW LEVEL SECURITY;

ALTER TABLE equipe
    -- Ponto de partida da prévia (no "Iniciar jornada" vale a posição do agente).
    ADD COLUMN base            geometry(Point, 4674) CHECK (base IS NULL OR (st_isvalid(base) AND NOT st_isempty(base))),
    ADD COLUMN jornada_inicio  time NOT NULL DEFAULT '07:30',
    ADD COLUMN jornada_fim     time NOT NULL DEFAULT '16:30',
    -- Almoço e paradas da equipe: tempo descontado da jornada.
    ADD COLUMN pausa_min       integer NOT NULL DEFAULT 60 CHECK (pausa_min BETWEEN 0 AND 240),
    ADD COLUMN tipo_veiculo_id uuid,
    ADD CONSTRAINT equipe_jornada_em_ordem CHECK (jornada_fim > jornada_inicio),
    ADD FOREIGN KEY (tenant_id, tipo_veiculo_id) REFERENCES tipo_veiculo (tenant_id, id);

CREATE TABLE equipe_habilidade (
    tenant_id     uuid NOT NULL,
    equipe_id     uuid NOT NULL,
    habilidade_id uuid NOT NULL,
    PRIMARY KEY (equipe_id, habilidade_id),
    FOREIGN KEY (tenant_id, equipe_id)     REFERENCES equipe (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, habilidade_id) REFERENCES habilidade (tenant_id, id)
);

-- Equipes já cadastradas ficam com todas as habilidades ativas (a programação delas continua como antes da RN-23
-- completa); o gestor ajusta depois. Veículo fica em branco: só os serviços sem veículo exigido seguem para elas.
INSERT INTO equipe_habilidade (tenant_id, equipe_id, habilidade_id)
SELECT e.tenant_id, e.id, h.id
  FROM equipe e
  JOIN habilidade h ON h.tenant_id = e.tenant_id AND h.ativo;

ALTER TABLE equipe FORCE ROW LEVEL SECURITY;
ALTER TABLE habilidade FORCE ROW LEVEL SECURITY;
ALTER TABLE tipo_veiculo FORCE ROW LEVEL SECURITY;

-- RN-23, veículo (ADR 0046): moto exigida é atendida por qualquer veículo; carro, pelo mesmo tipo ou por um
-- caminhão; caminhão (hidrojato, vácuo), só pelo mesmo tipo. Sem veículo exigido, qualquer equipe.
CREATE FUNCTION veiculo_atende(p_exigido uuid, p_da_equipe uuid) RETURNS boolean
LANGUAGE sql STABLE AS $$
    SELECT p_exigido IS NULL OR p_exigido = p_da_equipe OR EXISTS (
        SELECT 1 FROM tipo_veiculo e, tipo_veiculo d
         WHERE e.id = p_exigido AND d.id = p_da_equipe
           AND (e.perfil_rota = 'MOTO' OR (e.perfil_rota = 'CARRO' AND d.perfil_rota = 'CAMINHAO')))
$$;

-- Ordem da parada na rota publicada (o app usa para mostrar a lista; nula = sem rota).
ALTER TABLE ordem_servico ADD COLUMN sequencia_rota integer CHECK (sequencia_rota > 0);

-- ----- Roteirização -----

CREATE TABLE roteirizacao (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL REFERENCES tenant (id),
    -- Unidade onde o programador pediu (escopo da leitura).
    unidade_id      uuid NOT NULL,
    data            date NOT NULL,
    origem          text NOT NULL CHECK (origem IN ('PREVIA', 'JORNADA', 'REOTIMIZACAO')),
    situacao        text NOT NULL CHECK (situacao IN ('CALCULANDO', 'PRONTA', 'FALHOU', 'PUBLICADA', 'DESCARTADA')),
    -- Deslocamento pela malha viária (VALHALLA) ou, sem o serviço de rotas, em linha reta com fator de correção.
    calculo         text CHECK (calculo IN ('VALHALLA', 'LINHA_RETA')),
    -- Pontuação do otimizador (restrições duras/flexíveis), para auditoria e suporte.
    pontuacao       text CHECK (char_length(pontuacao) <= 200),
    erro            text CHECK (char_length(erro) <= 500),
    -- Roteirização que esta substitui (reotimização ou novo cálculo).
    anterior_id     uuid,
    criada_em       timestamptz NOT NULL DEFAULT now(),
    criada_por      uuid,
    concluida_em    timestamptz,
    publicada_em    timestamptz,
    publicada_por   uuid,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, unidade_id)    REFERENCES unidade (tenant_id, id),
    FOREIGN KEY (tenant_id, criada_por)    REFERENCES usuario (tenant_id, id),
    FOREIGN KEY (tenant_id, publicada_por) REFERENCES usuario (tenant_id, id),
    FOREIGN KEY (tenant_id, anterior_id)   REFERENCES roteirizacao (tenant_id, id),
    -- Publicada guarda quando e quem; descartada pode ter sido publicada antes (substituída por outra).
    CONSTRAINT roteirizacao_publicada CHECK (situacao <> 'PUBLICADA' OR (publicada_em IS NOT NULL AND publicada_por IS NOT NULL))
);

CREATE INDEX idx_roteirizacao_data ON roteirizacao (tenant_id, data, criada_em DESC);
-- Um cálculo por vez para o mesmo dia e unidade.
CREATE UNIQUE INDEX uq_roteirizacao_calculando ON roteirizacao (tenant_id, unidade_id, data)
    WHERE situacao = 'CALCULANDO';

CREATE TABLE rota (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid NOT NULL REFERENCES tenant (id),
    roteirizacao_id      uuid NOT NULL,
    equipe_id            uuid NOT NULL,
    -- Ponto de partida usado: base da equipe (prévia) ou última posição do agente (jornada, reotimização).
    partida              geometry(Point, 4674),
    inicio               timestamptz NOT NULL,
    fim_previsto         timestamptz NOT NULL,
    km                   numeric(9, 2) NOT NULL CHECK (km >= 0),
    minutos_deslocamento integer NOT NULL CHECK (minutos_deslocamento >= 0),
    minutos_servico      integer NOT NULL CHECK (minutos_servico >= 0),
    -- Traçado para o mapa (preenchido quando calculado pela malha viária).
    geom                 geometry(MultiLineString, 4674),
    UNIQUE (tenant_id, id),
    UNIQUE (roteirizacao_id, equipe_id),
    FOREIGN KEY (tenant_id, roteirizacao_id) REFERENCES roteirizacao (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, equipe_id)       REFERENCES equipe (tenant_id, id),
    CONSTRAINT rota_periodo CHECK (fim_previsto >= inicio)
);

CREATE TABLE rota_parada (
    tenant_id        uuid NOT NULL,
    rota_id          uuid NOT NULL,
    ordem_servico_id uuid NOT NULL,
    ordem            integer NOT NULL CHECK (ordem > 0),
    chegada_prevista timestamptz NOT NULL,
    saida_prevista   timestamptz NOT NULL,
    km_ate           numeric(9, 2) NOT NULL CHECK (km_ate >= 0),
    minutos_ate      integer NOT NULL CHECK (minutos_ate >= 0),
    -- Fixada pelo programador: o recálculo mantém a equipe e a posição (J6).
    fixada           boolean NOT NULL DEFAULT false,
    -- Estoura o prazo da OS (RN-21) pela chegada prevista.
    atrasa           boolean NOT NULL DEFAULT false,
    PRIMARY KEY (rota_id, ordem_servico_id),
    UNIQUE (rota_id, ordem),
    FOREIGN KEY (tenant_id, rota_id)          REFERENCES rota (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, ordem_servico_id) REFERENCES ordem_servico (tenant_id, id),
    CONSTRAINT parada_periodo CHECK (saida_prevista >= chegada_prevista)
);

CREATE INDEX idx_rota_parada_ordem ON rota_parada (tenant_id, ordem_servico_id);

-- OS pedidas que não couberam em nenhuma rota, com o motivo (J6).
CREATE TABLE roteirizacao_pendente (
    tenant_id        uuid NOT NULL,
    roteirizacao_id  uuid NOT NULL,
    ordem_servico_id uuid NOT NULL,
    motivo           text NOT NULL CHECK (motivo IN ('JORNADA', 'HABILIDADE', 'VEICULO', 'AREA', 'SEM_LOCAL')),
    PRIMARY KEY (roteirizacao_id, ordem_servico_id),
    FOREIGN KEY (tenant_id, roteirizacao_id)  REFERENCES roteirizacao (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, ordem_servico_id) REFERENCES ordem_servico (tenant_id, id)
);

-- ----- Acesso -----

ALTER TABLE equipe_habilidade ENABLE ROW LEVEL SECURITY;
ALTER TABLE equipe_habilidade FORCE ROW LEVEL SECURITY;
CREATE POLICY equipe_habilidade_pela_equipe ON equipe_habilidade USING (
    tenant_id = app_tenant() AND equipe_id IN (SELECT id FROM equipe));

ALTER TABLE roteirizacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE roteirizacao FORCE ROW LEVEL SECURITY;
CREATE POLICY roteirizacao_escopo ON roteirizacao USING (
    tenant_id = app_tenant() AND unidade_id IN (
        SELECT u.id FROM unidade u WHERE u.caminho <@ ANY (app_escopos())));

ALTER TABLE rota ENABLE ROW LEVEL SECURITY;
ALTER TABLE rota FORCE ROW LEVEL SECURITY;
CREATE POLICY rota_pela_equipe ON rota USING (
    tenant_id = app_tenant() AND equipe_id IN (SELECT id FROM equipe));

ALTER TABLE rota_parada ENABLE ROW LEVEL SECURITY;
ALTER TABLE rota_parada FORCE ROW LEVEL SECURITY;
CREATE POLICY rota_parada_pela_rota ON rota_parada USING (
    tenant_id = app_tenant() AND rota_id IN (SELECT id FROM rota));

ALTER TABLE roteirizacao_pendente ENABLE ROW LEVEL SECURITY;
ALTER TABLE roteirizacao_pendente FORCE ROW LEVEL SECURITY;
CREATE POLICY roteirizacao_pendente_pela_roteirizacao ON roteirizacao_pendente USING (
    tenant_id = app_tenant() AND roteirizacao_id IN (SELECT id FROM roteirizacao));

GRANT SELECT, INSERT, DELETE ON equipe_habilidade TO aurora_app;
GRANT SELECT, INSERT, UPDATE ON roteirizacao TO aurora_app;
GRANT SELECT, INSERT, DELETE ON rota, rota_parada, roteirizacao_pendente TO aurora_app;
GRANT UPDATE (fixada) ON rota_parada TO aurora_app;
GRANT EXECUTE ON FUNCTION veiculo_atende(uuid, uuid) TO aurora_app;
