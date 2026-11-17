-- ADR 0042: catálogo de serviços de campo (épico J, J1–J2, doc 10). Vale para o cliente inteiro (sem escopo de
-- unidade). Serviço versionado: a OS guarda a versão com que nasceu e alterar o serviço cria nova versão (RN-20).
-- V31 porque a V29 (situação mensal particionada) e a V30 (atendimento ao titular) estão em outras branches.

INSERT INTO permissao (codigo, descricao) VALUES
    ('servico:gerenciar', 'Cadastrar o catálogo de serviços de campo: grupos, serviços, parâmetros, motivos, veículos e habilidades');

CREATE TABLE tipo_veiculo (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL REFERENCES tenant (id),
    codigo      text NOT NULL CHECK (codigo ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    nome        text NOT NULL CHECK (char_length(nome) BETWEEN 2 AND 80),
    -- Perfil usado no cálculo das rotas (ADR da roteirização, M5).
    perfil_rota text NOT NULL CHECK (perfil_rota IN ('MOTO', 'CARRO', 'CAMINHAO')),
    ativo       boolean NOT NULL DEFAULT true,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, codigo)
);

CREATE TABLE habilidade (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant (id),
    codigo    text NOT NULL CHECK (codigo ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    nome      text NOT NULL CHECK (char_length(nome) BETWEEN 2 AND 80),
    ativo     boolean NOT NULL DEFAULT true,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, codigo)
);

CREATE TABLE grupo_servico (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant (id),
    codigo    text NOT NULL CHECK (codigo ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    nome      text NOT NULL CHECK (char_length(nome) BETWEEN 2 AND 80),
    ativo     boolean NOT NULL DEFAULT true,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, codigo)
);

CREATE TABLE servico (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES tenant (id),
    grupo_id      uuid NOT NULL,
    codigo        text NOT NULL CHECK (codigo ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    ativo         boolean NOT NULL DEFAULT true,
    versao_atual  integer NOT NULL CHECK (versao_atual > 0),
    criado_em     timestamptz NOT NULL DEFAULT now(),
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, codigo),
    FOREIGN KEY (tenant_id, grupo_id) REFERENCES grupo_servico (tenant_id, id)
);

CREATE TABLE servico_versao (
    id                       uuid PRIMARY KEY,
    tenant_id                uuid NOT NULL REFERENCES tenant (id),
    servico_id               uuid NOT NULL,
    versao                   integer NOT NULL CHECK (versao > 0),
    nome                     text NOT NULL CHECK (char_length(nome) BETWEEN 3 AND 120),
    descricao                text CHECK (char_length(descricao) <= 500),
    unidade_medida           text NOT NULL CHECK (unidade_medida IN ('UN', 'M', 'M2', 'M3')),
    alvo                     text NOT NULL CHECK (alvo IN ('IMOVEL', 'TRECHO', 'AMBOS')),
    -- Minutos por OS ou, com tempo_por_unidade, por unidade de medida (ex.: 3 min por metro de rede).
    tempo_padrao_min         numeric(7, 2) NOT NULL CHECK (tempo_padrao_min > 0 AND tempo_padrao_min <= 1440),
    tempo_por_unidade        boolean NOT NULL,
    -- RN-21: emergencial em horas corridas; as demais em dias úteis.
    prazo_emergencial_horas  integer NOT NULL CHECK (prazo_emergencial_horas BETWEEN 1 AND 72),
    prazo_alta_dias          integer NOT NULL CHECK (prazo_alta_dias BETWEEN 1 AND 365),
    prazo_media_dias         integer NOT NULL CHECK (prazo_media_dias BETWEEN 1 AND 365),
    prazo_baixa_dias         integer NOT NULL CHECK (prazo_baixa_dias BETWEEN 1 AND 365),
    tipo_veiculo_id          uuid,
    habilidade_id            uuid,
    -- RN-19: a carga mensal confirma a execução (só serviços em imóvel).
    conferido_pela_carga     boolean NOT NULL,
    criada_em                timestamptz NOT NULL DEFAULT now(),
    criada_por               uuid,
    UNIQUE (tenant_id, id),
    UNIQUE (servico_id, versao),
    FOREIGN KEY (tenant_id, servico_id)      REFERENCES servico (tenant_id, id),
    FOREIGN KEY (tenant_id, tipo_veiculo_id) REFERENCES tipo_veiculo (tenant_id, id),
    FOREIGN KEY (tenant_id, habilidade_id)   REFERENCES habilidade (tenant_id, id),
    FOREIGN KEY (tenant_id, criada_por)      REFERENCES usuario (tenant_id, id),
    CONSTRAINT servico_prazos_em_ordem CHECK (prazo_alta_dias <= prazo_media_dias AND prazo_media_dias <= prazo_baixa_dias),
    CONSTRAINT servico_carga_so_em_imovel CHECK (NOT conferido_pela_carga OR alvo = 'IMOVEL')
);

CREATE TABLE parametro_servico (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL REFERENCES tenant (id),
    servico_versao_id uuid NOT NULL,
    chave             text NOT NULL CHECK (chave ~ '^[a-z][a-z0-9_]{0,39}$'),
    rotulo            text NOT NULL CHECK (char_length(rotulo) BETWEEN 2 AND 80),
    ajuda             text CHECK (char_length(ajuda) <= 200),
    tipo              text NOT NULL CHECK (tipo IN ('INTEIRO', 'DECIMAL', 'TEXTO', 'LISTA', 'LISTA_MULTIPLA', 'SIM_NAO',
                                                    'DATA_HORA', 'FOTO')),
    obrigatorio       boolean NOT NULL,
    -- Número: faixa do valor; texto: caracteres; foto: quantidade.
    minimo            numeric(12, 3),
    maximo            numeric(12, 3),
    unidade           text CHECK (char_length(unidade) <= 10),
    opcoes            jsonb,
    -- Só aparece quando outro parâmetro (sim/não ou lista, anterior) tem o valor indicado: {"chave": "...", "valor": "..."}.
    condicao          jsonb,
    ordem             integer NOT NULL CHECK (ordem > 0),
    UNIQUE (servico_versao_id, chave),
    UNIQUE (servico_versao_id, ordem),
    FOREIGN KEY (tenant_id, servico_versao_id) REFERENCES servico_versao (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT parametro_faixa CHECK (minimo IS NULL OR maximo IS NULL OR minimo <= maximo),
    CONSTRAINT parametro_opcoes_so_em_lista CHECK ((tipo IN ('LISTA', 'LISTA_MULTIPLA')) = (opcoes IS NOT NULL))
);

CREATE TABLE motivo (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL REFERENCES tenant (id),
    tipo             text NOT NULL CHECK (tipo IN ('PARALISACAO', 'DEVOLUCAO', 'NAO_EXECUCAO', 'CANCELAMENTO')),
    codigo           text NOT NULL CHECK (codigo ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    nome             text NOT NULL CHECK (char_length(nome) BETWEEN 2 AND 80),
    exige_observacao boolean NOT NULL,
    -- Nulo: vale para todos os serviços.
    servico_id       uuid,
    ativo            boolean NOT NULL DEFAULT true,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, tipo, codigo),
    FOREIGN KEY (tenant_id, servico_id) REFERENCES servico (tenant_id, id)
);

CREATE INDEX idx_servico_grupo ON servico (tenant_id, grupo_id);
CREATE INDEX idx_motivo_servico ON motivo (tenant_id, servico_id) WHERE servico_id IS NOT NULL;

-- Catálogo inicial (doc 10, 10.5): chamado para os clientes existentes aqui e para os novos no provisionamento.
-- Idempotente: só semeia cliente sem nenhum grupo.
CREATE FUNCTION semear_catalogo_servicos(p_tenant uuid) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    g_lig uuid; g_rede uuid; g_pav uuid; g_com uuid;
    v_moto uuid; v_carro uuid; v_caminhonete uuid; v_hidrojato uuid; v_vacuo uuid;
    h_ligacao uuid; h_rede uuid; h_pavimento uuid; h_cftv uuid;
BEGIN
    IF EXISTS (SELECT 1 FROM grupo_servico WHERE tenant_id = p_tenant) THEN
        RETURN;
    END IF;

    g_lig := gen_random_uuid(); g_rede := gen_random_uuid(); g_pav := gen_random_uuid(); g_com := gen_random_uuid();
    INSERT INTO grupo_servico (id, tenant_id, codigo, nome) VALUES
        (g_lig,  p_tenant, 'LIGACOES',          'Ligações'),
        (g_rede, p_tenant, 'MANUTENCAO_DE_REDE', 'Manutenção de rede'),
        (g_pav,  p_tenant, 'PAVIMENTO',          'Pavimento'),
        (g_com,  p_tenant, 'COMERCIAL',          'Comercial');

    v_moto := gen_random_uuid(); v_carro := gen_random_uuid(); v_caminhonete := gen_random_uuid();
    v_hidrojato := gen_random_uuid(); v_vacuo := gen_random_uuid();
    INSERT INTO tipo_veiculo (id, tenant_id, codigo, nome, perfil_rota) VALUES
        (v_moto,        p_tenant, 'MOTO',              'Moto',                   'MOTO'),
        (v_carro,       p_tenant, 'CARRO_LEVE',        'Carro leve',             'CARRO'),
        (v_caminhonete, p_tenant, 'CAMINHONETE',       'Caminhonete',            'CARRO'),
        (v_hidrojato,   p_tenant, 'CAMINHAO_HIDROJATO', 'Caminhão hidrojato',    'CAMINHAO'),
        (v_vacuo,       p_tenant, 'CAMINHAO_VACUO',    'Caminhão de vácuo',      'CAMINHAO');

    h_ligacao := gen_random_uuid(); h_rede := gen_random_uuid(); h_pavimento := gen_random_uuid(); h_cftv := gen_random_uuid();
    INSERT INTO habilidade (id, tenant_id, codigo, nome) VALUES
        (h_ligacao,   p_tenant, 'LIGACAO_PREDIAL',    'Ligação predial'),
        (h_rede,      p_tenant, 'MANUTENCAO_DE_REDE', 'Manutenção de rede'),
        (h_pavimento, p_tenant, 'PAVIMENTACAO',       'Pavimentação'),
        (h_cftv,      p_tenant, 'INSPECAO_CFTV',      'Inspeção por câmera (CFTV)');

    -- Serviço + versão 1. Prazos: emergencial (h), alta, média, baixa (dias úteis).
    WITH x (codigo, grupo, nome, unidade, alvo, tempo, por_unidade, emerg, alta, media, baixa, veiculo, habilidade,
            carga) AS (VALUES
        ('VISTORIA',               g_lig,  'Vistoria de viabilidade',              'UN', 'IMOVEL', 20,  false, 24, 2, 5, 10, v_moto,      NULL,        false),
        ('LIGACAO_NOVA',           g_lig,  'Ligação nova de esgoto',               'UN', 'IMOVEL', 120, false, 24, 5, 10, 20, v_caminhonete, h_ligacao, true),
        ('RELIGACAO',              g_lig,  'Religação',                            'UN', 'IMOVEL', 40,  false, 24, 2, 3, 5,   v_carro,     h_ligacao,   true),
        ('NOTIFICACAO',            g_com,  'Notificação',                          'UN', 'IMOVEL', 10,  false, 24, 2, 5, 10,  v_moto,      NULL,        false),
        ('DESOBSTRUCAO_RAMAL',     g_rede, 'Desobstrução de ramal',                'UN', 'IMOVEL', 60,  false, 6,  1, 2, 3,   v_hidrojato, h_rede,      false),
        ('DESOBSTRUCAO_REDE',      g_rede, 'Desobstrução de rede coletora',        'M',  'TRECHO', 3,   true,  6,  1, 2, 3,   v_hidrojato, h_rede,      false),
        ('LIMPEZA_REDE',           g_rede, 'Limpeza de rede',                      'M',  'TRECHO', 2,   true,  24, 5, 10, 30, v_vacuo,     h_rede,      false),
        ('LIMPEZA_PV',             g_rede, 'Limpeza de PV',                        'UN', 'TRECHO', 30,  false, 24, 5, 10, 30, v_vacuo,     h_rede,      false),
        ('REPARO_REDE',            g_rede, 'Reparo de rede',                       'M',  'TRECHO', 180, false, 12, 2, 5, 10,  v_caminhonete, h_rede,    false),
        ('INSPECAO_CFTV',          g_rede, 'Inspeção de rede (CFTV)',              'M',  'TRECHO', 2,   true,  24, 5, 15, 30, v_caminhonete, h_cftv,    false),
        ('REPOSICAO_PAVIMENTO',    g_pav,  'Reposição de pavimento',               'M2', 'AMBOS',  15,  true,  24, 3, 7, 15,  v_caminhonete, h_pavimento, false),
        ('LANCAMENTO_IRREGULAR',   g_com,  'Verificação de lançamento irregular',  'UN', 'IMOVEL', 30,  false, 24, 3, 7, 15,  v_carro,     NULL,        false)),
    novos AS (
        INSERT INTO servico (id, tenant_id, grupo_id, codigo, versao_atual)
        SELECT gen_random_uuid(), p_tenant, x.grupo, x.codigo, 1 FROM x
        RETURNING id, codigo)
    INSERT INTO servico_versao (id, tenant_id, servico_id, versao, nome, unidade_medida, alvo, tempo_padrao_min,
                                tempo_por_unidade, prazo_emergencial_horas, prazo_alta_dias, prazo_media_dias,
                                prazo_baixa_dias, tipo_veiculo_id, habilidade_id, conferido_pela_carga)
    SELECT gen_random_uuid(), p_tenant, n.id, 1, x.nome, x.unidade, x.alvo, x.tempo, x.por_unidade, x.emerg, x.alta,
           x.media, x.baixa, x.veiculo, x.habilidade, x.carga
      FROM x JOIN novos n ON n.codigo = x.codigo;

    INSERT INTO parametro_servico (id, tenant_id, servico_versao_id, chave, rotulo, tipo, obrigatorio, minimo, maximo,
                                   unidade, opcoes, condicao, ordem)
    SELECT gen_random_uuid(), p_tenant, vs.id, p.chave, p.rotulo, p.tipo, p.obrigatorio, p.minimo, p.maximo, p.unidade,
           p.opcoes::jsonb, p.condicao::jsonb, p.ordem
      FROM (VALUES
        ('VISTORIA', 1, 'viavel', 'Ligação viável', 'SIM_NAO', true, NULL, NULL, NULL, NULL, NULL),
        ('VISTORIA', 2, 'abaixo_da_rede', 'Imóvel abaixo da cota da rede', 'SIM_NAO', true, NULL, NULL, NULL, NULL, NULL),
        ('VISTORIA', 3, 'distancia_rede', 'Distância até a rede', 'DECIMAL', false, 0, 200, 'm', NULL, NULL),
        ('VISTORIA', 4, 'foto', 'Foto da fachada', 'FOTO', true, 1, 3, NULL, NULL, NULL),
        ('LIGACAO_NOVA', 1, 'extensao_ramal', 'Extensão do ramal', 'DECIMAL', true, 0.5, 100, 'm', NULL, NULL),
        ('LIGACAO_NOVA', 2, 'diametro', 'Diâmetro', 'LISTA', true, NULL, NULL, 'mm', '["100", "150"]', NULL),
        ('LIGACAO_NOVA', 3, 'profundidade', 'Profundidade', 'DECIMAL', true, 0.3, 6, 'm', NULL, NULL),
        ('LIGACAO_NOVA', 4, 'caixa_inspecao', 'Caixa de inspeção instalada', 'SIM_NAO', true, NULL, NULL, NULL, NULL, NULL),
        ('LIGACAO_NOVA', 5, 'fotos', 'Fotos antes e depois', 'FOTO', true, 2, 6, NULL, NULL, NULL),
        ('RELIGACAO', 1, 'tipo_religacao', 'Tipo de religação', 'LISTA', true, NULL, NULL, NULL, '["Retirada de tampão", "Reconstrução do ramal", "Desobstrução"]', NULL),
        ('RELIGACAO', 2, 'foto', 'Foto', 'FOTO', true, 1, 3, NULL, NULL, NULL),
        ('NOTIFICACAO', 1, 'entregue_a', 'Entregue a', 'LISTA', true, NULL, NULL, NULL, '["Morador", "Vizinho", "Não entregue"]', NULL),
        ('NOTIFICACAO', 2, 'foto', 'Foto do aviso', 'FOTO', true, 1, 2, NULL, NULL, NULL),
        ('DESOBSTRUCAO_RAMAL', 1, 'causa', 'Causa', 'LISTA', true, NULL, NULL, NULL, '["Gordura", "Raiz", "Resíduo sólido", "Ramal quebrado", "Outra"]', NULL),
        ('DESOBSTRUCAO_RAMAL', 2, 'equipamento', 'Equipamento', 'LISTA', true, NULL, NULL, NULL, '["Hidrojato", "Varetas", "Manual"]', NULL),
        ('DESOBSTRUCAO_RAMAL', 3, 'foto', 'Foto', 'FOTO', true, 1, 4, NULL, NULL, NULL),
        ('DESOBSTRUCAO_REDE', 1, 'extensao', 'Extensão desobstruída', 'DECIMAL', true, 1, 2000, 'm', NULL, NULL),
        ('DESOBSTRUCAO_REDE', 2, 'causa', 'Causa', 'LISTA', true, NULL, NULL, NULL, '["Gordura", "Raiz", "Resíduo sólido", "Rede quebrada", "Outra"]', NULL),
        ('DESOBSTRUCAO_REDE', 3, 'equipamento', 'Equipamento', 'LISTA', true, NULL, NULL, NULL, '["Hidrojato", "Varetas", "Manual"]', NULL),
        ('DESOBSTRUCAO_REDE', 4, 'fotos', 'Fotos', 'FOTO', true, 1, 6, NULL, NULL, NULL),
        ('LIMPEZA_REDE', 1, 'extensao', 'Extensão limpa', 'DECIMAL', true, 1, 5000, 'm', NULL, NULL),
        ('LIMPEZA_REDE', 2, 'volume', 'Volume retirado', 'DECIMAL', false, 0, 100, 'm³', NULL, NULL),
        ('LIMPEZA_REDE', 3, 'fotos', 'Fotos', 'FOTO', true, 1, 6, NULL, NULL, NULL),
        ('LIMPEZA_PV', 1, 'quantidade_pv', 'Quantidade de PVs', 'INTEIRO', true, 1, 50, NULL, NULL, NULL),
        ('LIMPEZA_PV', 2, 'estado_tampa', 'Estado da tampa', 'LISTA', true, NULL, NULL, NULL, '["Boa", "Danificada", "Ausente"]', NULL),
        ('LIMPEZA_PV', 3, 'foto', 'Foto', 'FOTO', true, 1, 4, NULL, NULL, NULL),
        ('REPARO_REDE', 1, 'extensao', 'Extensão reparada', 'DECIMAL', true, 0.5, 500, 'm', NULL, NULL),
        ('REPARO_REDE', 2, 'diametro', 'Diâmetro', 'LISTA', true, NULL, NULL, 'mm', '["150", "200", "250", "300", "400"]', NULL),
        ('REPARO_REDE', 3, 'material', 'Material', 'LISTA', true, NULL, NULL, NULL, '["PVC", "Manilha cerâmica", "Concreto", "PEAD"]', NULL),
        ('REPARO_REDE', 4, 'fotos', 'Fotos antes e depois', 'FOTO', true, 2, 8, NULL, NULL, NULL),
        ('INSPECAO_CFTV', 1, 'extensao', 'Extensão inspecionada', 'DECIMAL', true, 1, 2000, 'm', NULL, NULL),
        ('INSPECAO_CFTV', 2, 'anomalias', 'Anomalias encontradas', 'LISTA_MULTIPLA', false, NULL, NULL, NULL, '["Trinca", "Raiz", "Deformação", "Desalinhamento", "Infiltração", "Ligação clandestina"]', NULL),
        ('REPOSICAO_PAVIMENTO', 1, 'area', 'Área reposta', 'DECIMAL', true, 0.1, 500, 'm²', NULL, NULL),
        ('REPOSICAO_PAVIMENTO', 2, 'tipo_pavimento', 'Tipo de pavimento', 'LISTA', true, NULL, NULL, NULL, '["Asfalto", "Bloquete", "Concreto", "Paralelepípedo", "Calçada"]', NULL),
        ('REPOSICAO_PAVIMENTO', 3, 'fotos', 'Fotos antes e depois', 'FOTO', true, 2, 6, NULL, NULL, NULL),
        ('LANCAMENTO_IRREGULAR', 1, 'irregularidade', 'Irregularidade', 'LISTA', true, NULL, NULL, NULL, '["Nenhuma", "Água de chuva na rede de esgoto", "Esgoto na rede pluvial", "Esgoto a céu aberto"]', NULL),
        ('LANCAMENTO_IRREGULAR', 2, 'teste_corante', 'Teste com corante feito', 'SIM_NAO', true, NULL, NULL, NULL, NULL, NULL),
        ('LANCAMENTO_IRREGULAR', 3, 'resultado_corante', 'Corante apareceu na rede', 'SIM_NAO', true, NULL, NULL, NULL, NULL, '{"chave": "teste_corante", "valor": "true"}'),
        ('LANCAMENTO_IRREGULAR', 4, 'foto', 'Foto', 'FOTO', true, 1, 4, NULL, NULL, NULL)) AS p (servico, ordem, chave, rotulo, tipo, obrigatorio, minimo, maximo, unidade, opcoes, condicao)
      JOIN servico sv ON sv.tenant_id = p_tenant AND sv.codigo = p.servico
      JOIN servico_versao vs ON vs.servico_id = sv.id AND vs.versao = 1;

    -- Motivos: os de não execução herdam os códigos da V28 (recusa do morador e inviabilidade técnica).
    INSERT INTO motivo (id, tenant_id, tipo, codigo, nome, exige_observacao)
    SELECT gen_random_uuid(), p_tenant, m.tipo, m.codigo, m.nome, m.codigo = 'OUTRO'
      FROM (VALUES
        ('PARALISACAO', 'CHUVA', 'Chuva'),
        ('PARALISACAO', 'FALTA_DE_MATERIAL', 'Falta de material'),
        ('PARALISACAO', 'AGUARDANDO_OUTRA_EQUIPE', 'Aguardando outra equipe'),
        ('PARALISACAO', 'TRANSITO_INTERDITADO', 'Trânsito interditado'),
        ('PARALISACAO', 'ACESSO_NEGADO_TEMPORARIO', 'Acesso negado temporariamente'),
        ('PARALISACAO', 'INTERVALO', 'Intervalo'),
        ('PARALISACAO', 'QUEBRA_DE_EQUIPAMENTO', 'Quebra de equipamento'),
        ('PARALISACAO', 'OUTRO', 'Outro'),
        ('DEVOLUCAO', 'ENDERECO_NAO_ENCONTRADO', 'Endereço não encontrado'),
        ('DEVOLUCAO', 'SERVICO_DE_OUTRA_EQUIPE', 'Serviço de outra equipe ou veículo'),
        ('DEVOLUCAO', 'FALTA_DE_MATERIAL', 'Falta de material'),
        ('DEVOLUCAO', 'AREA_DE_RISCO', 'Área de risco'),
        ('DEVOLUCAO', 'IMOVEL_FECHADO', 'Imóvel fechado'),
        ('DEVOLUCAO', 'SERVICO_JA_EXECUTADO', 'Serviço já executado'),
        ('DEVOLUCAO', 'OUTRO', 'Outro'),
        ('NAO_EXECUCAO', 'SEM_INTERESSE', 'Morador sem interesse'),
        ('NAO_EXECUCAO', 'CUSTO_DA_LIGACAO', 'Custo da ligação'),
        ('NAO_EXECUCAO', 'SOLUCAO_PROPRIA', 'Solução própria (fossa)'),
        ('NAO_EXECUCAO', 'DEBITO_PENDENTE', 'Débito pendente'),
        ('NAO_EXECUCAO', 'SEM_REDE_NA_RUA', 'Sem rede na rua'),
        ('NAO_EXECUCAO', 'IMOVEL_ABAIXO_DA_REDE', 'Imóvel abaixo da rede'),
        ('NAO_EXECUCAO', 'IMOVEL_DESOCUPADO', 'Imóvel desocupado'),
        ('NAO_EXECUCAO', 'OUTRO', 'Outro'),
        ('CANCELAMENTO', 'DUPLICADA', 'OS duplicada'),
        ('CANCELAMENTO', 'SOLICITACAO_RETIRADA', 'Solicitação retirada'),
        ('CANCELAMENTO', 'OUTRO', 'Outro')) AS m (tipo, codigo, nome);
END;
$$;

-- Semeia os clientes existentes antes de ligar o RLS das tabelas novas. O dono está sujeito ao RLS forçado de
-- "tenant": suspenso só nesta transação (como na V28) e o contexto de cada cliente definido a cada volta.
ALTER TABLE tenant NO FORCE ROW LEVEL SECURITY;
DO $$
DECLARE t uuid;
BEGIN
    FOR t IN SELECT id FROM tenant LOOP
        PERFORM set_config('app.tenant_id', t::text, true);
        PERFORM semear_catalogo_servicos(t);
    END LOOP;
    PERFORM set_config('app.tenant_id', '', true);
END;
$$;
ALTER TABLE tenant FORCE ROW LEVEL SECURITY;

ALTER TABLE tipo_veiculo ENABLE ROW LEVEL SECURITY;
ALTER TABLE tipo_veiculo FORCE ROW LEVEL SECURITY;
CREATE POLICY tipo_veiculo_tenant ON tipo_veiculo USING (tenant_id = app_tenant());

ALTER TABLE habilidade ENABLE ROW LEVEL SECURITY;
ALTER TABLE habilidade FORCE ROW LEVEL SECURITY;
CREATE POLICY habilidade_tenant ON habilidade USING (tenant_id = app_tenant());

ALTER TABLE grupo_servico ENABLE ROW LEVEL SECURITY;
ALTER TABLE grupo_servico FORCE ROW LEVEL SECURITY;
CREATE POLICY grupo_servico_tenant ON grupo_servico USING (tenant_id = app_tenant());

ALTER TABLE servico ENABLE ROW LEVEL SECURITY;
ALTER TABLE servico FORCE ROW LEVEL SECURITY;
CREATE POLICY servico_tenant ON servico USING (tenant_id = app_tenant());

ALTER TABLE servico_versao ENABLE ROW LEVEL SECURITY;
ALTER TABLE servico_versao FORCE ROW LEVEL SECURITY;
CREATE POLICY servico_versao_tenant ON servico_versao USING (tenant_id = app_tenant());

ALTER TABLE parametro_servico ENABLE ROW LEVEL SECURITY;
ALTER TABLE parametro_servico FORCE ROW LEVEL SECURITY;
CREATE POLICY parametro_servico_tenant ON parametro_servico USING (tenant_id = app_tenant());

ALTER TABLE motivo ENABLE ROW LEVEL SECURITY;
ALTER TABLE motivo FORCE ROW LEVEL SECURITY;
CREATE POLICY motivo_tenant ON motivo USING (tenant_id = app_tenant());

-- Versões e parâmetros nunca mudam depois de criados (RN-20): só inserção.
GRANT SELECT, INSERT, UPDATE ON tipo_veiculo, habilidade, grupo_servico, servico, motivo TO aurora_app;
GRANT SELECT, INSERT ON servico_versao, parametro_servico TO aurora_app;
GRANT EXECUTE ON FUNCTION semear_catalogo_servicos(uuid) TO aurora_app;

-- Novo perfil do sistema "Gestor de serviços" nos clientes existentes (os novos recebem pelo provisionamento);
-- o administrador também gerencia o catálogo.
ALTER TABLE perfil NO FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao NO FORCE ROW LEVEL SECURITY;

INSERT INTO perfil (id, tenant_id, nome, sistema, descricao)
SELECT gen_random_uuid(), p.tenant_id, 'Gestor de serviços', true, 'Catálogo de serviços de campo e programação das equipes'
  FROM (SELECT DISTINCT tenant_id FROM perfil WHERE sistema) p
 WHERE NOT EXISTS (SELECT 1 FROM perfil x WHERE x.tenant_id = p.tenant_id AND x.nome = 'Gestor de serviços');

INSERT INTO perfil_permissao (tenant_id, perfil_id, permissao)
SELECT p.tenant_id, p.id, n.permissao
  FROM perfil p
  JOIN (VALUES ('Gestor de serviços', 'servico:gerenciar'),
               ('Gestor de serviços', 'unidade:ler'),
               ('Administrador do tenant', 'servico:gerenciar')) AS n (perfil, permissao) ON n.perfil = p.nome
 WHERE p.sistema
ON CONFLICT DO NOTHING;

ALTER TABLE perfil FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao FORCE ROW LEVEL SECURITY;
