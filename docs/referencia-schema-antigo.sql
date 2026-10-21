--
-- PostgreSQL database dump
--

\restrict rua6oGONy4IwLjFeWj6u9qIpCbVWE9ockKCtjXiJRsNSrUvw3VnoQT0GgfdPQGZ

-- Dumped from database version 16.15 (Debian 16.15-1.pgdg12+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg12+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- Name: ltree; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS ltree WITH SCHEMA public;


--
-- Name: EXTENSION ltree; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION ltree IS 'data type for hierarchical tree-like structures';


--
-- Name: postgis; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;


--
-- Name: EXTENSION postgis; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION postgis IS 'PostGIS geometry and geography spatial types and functions';


--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


--
-- Name: app_escopos(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.app_escopos() RETURNS public.ltree[]
    LANGUAGE sql STABLE
    AS $$
    SELECT COALESCE(
      string_to_array(NULLIF(current_setting('app.escopos', true), ''), ',')::ltree[],
      '{}'::ltree[])
$$;


--
-- Name: app_tenant(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.app_tenant() RETURNS uuid
    LANGUAGE plpgsql STABLE
    AS $$
DECLARE
  valor text := NULLIF(current_setting('app.tenant_id', true), '');
BEGIN
  IF valor IS NULL THEN
    RAISE EXCEPTION 'contexto de tenant não definido na transação'
      USING ERRCODE = 'insufficient_privilege';
  END IF;
  RETURN valor::uuid;
END
$$;


--
-- Name: app_usuario(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.app_usuario() RETURNS uuid
    LANGUAGE sql STABLE
    AS $$ SELECT NULLIF(current_setting('app.usuario_id', true), '')::uuid $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: alerta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.alerta (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    tipo text NOT NULL,
    severidade text NOT NULL,
    unidade_id uuid,
    chave text NOT NULL,
    titulo text NOT NULL,
    descricao text NOT NULL,
    link text,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    notificado_em timestamp with time zone,
    reconhecido_por uuid,
    reconhecido_em timestamp with time zone,
    resolvido_em timestamp with time zone,
    CONSTRAINT alerta_severidade_check CHECK ((severidade = ANY (ARRAY['ATENCAO'::text, 'CRITICO'::text]))),
    CONSTRAINT alerta_tipo_check CHECK ((tipo = ANY (ARRAY['INDICADOR_ABAIXO_DA_META'::text, 'META_FORA_DO_RITMO'::text, 'FONTE_ATRASADA'::text, 'PRAZO_CONTRATUAL'::text, 'TRILHA_ALTERADA'::text])))
);

ALTER TABLE ONLY public.alerta FORCE ROW LEVEL SECURITY;


--
-- Name: assistente_conversa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assistente_conversa (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    titulo text NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.assistente_conversa FORCE ROW LEVEL SECURITY;


--
-- Name: assistente_limite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assistente_limite (
    tenant_id uuid NOT NULL,
    tokens_por_mes bigint,
    usd_por_mes numeric(12,2),
    CONSTRAINT assistente_limite_tokens_por_mes_check CHECK ((tokens_por_mes > 0)),
    CONSTRAINT assistente_limite_usd_por_mes_check CHECK ((usd_por_mes > (0)::numeric))
);

ALTER TABLE ONLY public.assistente_limite FORCE ROW LEVEL SECURITY;


--
-- Name: assistente_mensagem; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assistente_mensagem (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    conversa_id uuid NOT NULL,
    papel text NOT NULL,
    texto text NOT NULL,
    fontes jsonb DEFAULT '[]'::jsonb NOT NULL,
    dados jsonb DEFAULT '[]'::jsonb NOT NULL,
    modelo text,
    tokens_entrada integer,
    tokens_saida integer,
    avaliacao smallint,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT assistente_mensagem_avaliacao_check CHECK ((avaliacao = ANY (ARRAY['-1'::integer, 1]))),
    CONSTRAINT assistente_mensagem_papel_check CHECK ((papel = ANY (ARRAY['USUARIO'::text, 'ASSISTENTE'::text])))
);

ALTER TABLE ONLY public.assistente_mensagem FORCE ROW LEVEL SECURITY;


--
-- Name: assistente_uso_mensal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assistente_uso_mensal (
    tenant_id uuid NOT NULL,
    mes date NOT NULL,
    perguntas bigint DEFAULT 0 NOT NULL,
    tokens_entrada bigint DEFAULT 0 NOT NULL,
    tokens_saida bigint DEFAULT 0 NOT NULL,
    tokens_cache_lido bigint DEFAULT 0 NOT NULL,
    tokens_cache_escrito bigint DEFAULT 0 NOT NULL,
    custo_usd numeric(14,6) DEFAULT 0 NOT NULL,
    perguntas_sem_custo bigint DEFAULT 0 NOT NULL,
    CONSTRAINT assistente_uso_mensal_mes_check CHECK ((EXTRACT(day FROM mes) = (1)::numeric))
);

ALTER TABLE ONLY public.assistente_uso_mensal FORCE ROW LEVEL SECURITY;


--
-- Name: cadeia_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cadeia_auditoria (
    tenant_id uuid NOT NULL,
    sequencia bigint NOT NULL,
    ultimo_hash bytea NOT NULL
);

ALTER TABLE ONLY public.cadeia_auditoria FORCE ROW LEVEL SECURITY;


--
-- Name: contrato; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contrato (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    numero text NOT NULL,
    objeto text,
    poder_concedente text,
    regulador text,
    data_assinatura date,
    vigencia_fim date,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.contrato FORCE ROW LEVEL SECURITY;


--
-- Name: credencial_conector; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.credencial_conector (
    prefixo text NOT NULL,
    tenant_id uuid NOT NULL,
    fonte_dados_id uuid NOT NULL,
    hash bytea NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: documento_arquivo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documento_arquivo (
    documento_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    conteudo bytea NOT NULL
);

ALTER TABLE ONLY public.documento_arquivo FORCE ROW LEVEL SECURITY;


--
-- Name: documento_contrato; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documento_contrato (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    contrato_id uuid NOT NULL,
    tipo text NOT NULL,
    titulo text NOT NULL,
    nome_arquivo text NOT NULL,
    sha256 text NOT NULL,
    tamanho_bytes bigint NOT NULL,
    paginas integer,
    data_assinatura date,
    status text NOT NULL,
    erro text,
    modelo_ia text,
    itens_propostos integer DEFAULT 0 NOT NULL,
    itens_descartados integer DEFAULT 0 NOT NULL,
    enviado_por uuid,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    texto_por_ocr boolean DEFAULT false NOT NULL,
    CONSTRAINT documento_contrato_status_check CHECK ((status = ANY (ARRAY['RECEBIDO'::text, 'RECONHECENDO_TEXTO'::text, 'PROCESSANDO'::text, 'PRONTO'::text, 'SEM_TEXTO'::text, 'FALHA'::text]))),
    CONSTRAINT documento_contrato_tipo_check CHECK ((tipo = ANY (ARRAY['ORIGINAL'::text, 'ANEXO'::text, 'ADITIVO'::text])))
);

ALTER TABLE ONLY public.documento_contrato FORCE ROW LEVEL SECURITY;


--
-- Name: documento_pagina; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documento_pagina (
    tenant_id uuid NOT NULL,
    documento_id uuid NOT NULL,
    pagina integer NOT NULL,
    texto text NOT NULL
);

ALTER TABLE ONLY public.documento_pagina FORCE ROW LEVEL SECURITY;


--
-- Name: evento_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_auditoria (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    sequencia bigint NOT NULL,
    ocorrido_em timestamp with time zone NOT NULL,
    usuario_id uuid,
    tipo text NOT NULL,
    recurso text,
    detalhes jsonb NOT NULL,
    ip_origem inet,
    hash_anterior bytea NOT NULL,
    hash bytea NOT NULL
)
PARTITION BY RANGE (ocorrido_em);

ALTER TABLE ONLY public.evento_auditoria FORCE ROW LEVEL SECURITY;


--
-- Name: evento_auditoria_2026; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_auditoria_2026 (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    sequencia bigint NOT NULL,
    ocorrido_em timestamp with time zone NOT NULL,
    usuario_id uuid,
    tipo text NOT NULL,
    recurso text,
    detalhes jsonb NOT NULL,
    ip_origem inet,
    hash_anterior bytea NOT NULL,
    hash bytea NOT NULL
);


--
-- Name: evento_auditoria_2027; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_auditoria_2027 (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    sequencia bigint NOT NULL,
    ocorrido_em timestamp with time zone NOT NULL,
    usuario_id uuid,
    tipo text NOT NULL,
    recurso text,
    detalhes jsonb NOT NULL,
    ip_origem inet,
    hash_anterior bytea NOT NULL,
    hash bytea NOT NULL
);


--
-- Name: evento_auditoria_outros; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_auditoria_outros (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    sequencia bigint NOT NULL,
    ocorrido_em timestamp with time zone NOT NULL,
    usuario_id uuid,
    tipo text NOT NULL,
    recurso text,
    detalhes jsonb NOT NULL,
    ip_origem inet,
    hash_anterior bytea NOT NULL,
    hash bytea NOT NULL
);


--
-- Name: execucao_sincronizacao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.execucao_sincronizacao (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    fonte_dados_id uuid NOT NULL,
    inicio timestamp with time zone NOT NULL,
    fim timestamp with time zone,
    status text NOT NULL,
    registros_lidos bigint,
    registros_gravados bigint,
    marca_d_agua text,
    erro text,
    competencia date,
    origem text,
    registros_rejeitados bigint DEFAULT 0 NOT NULL,
    rejeicoes jsonb DEFAULT '[]'::jsonb NOT NULL,
    usuario_id uuid,
    registros_novos bigint DEFAULT 0 NOT NULL,
    registros_atualizados bigint DEFAULT 0 NOT NULL,
    registros_inalterados bigint DEFAULT 0 NOT NULL,
    CONSTRAINT execucao_sincronizacao_origem_check CHECK ((origem = ANY (ARRAY['AGENTE'::text, 'ARQUIVO'::text]))),
    CONSTRAINT execucao_sincronizacao_status_check CHECK ((status = ANY (ARRAY['EM_ANDAMENTO'::text, 'SUCESSO'::text, 'FALHA'::text])))
);

ALTER TABLE ONLY public.execucao_sincronizacao FORCE ROW LEVEL SECURITY;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: fonte_dados; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fonte_dados (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    nome text NOT NULL,
    tipo_adaptador text NOT NULL,
    ref_segredo text,
    frequencia text,
    atraso_maximo interval DEFAULT '2 days'::interval NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    sistema_origem text,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT fonte_dados_tipo_adaptador_check CHECK ((tipo_adaptador = ANY (ARRAY['AGENTE'::text, 'ARQUIVO'::text, 'CDC'::text, 'API'::text, 'ETL_JDBC'::text])))
);

ALTER TABLE ONLY public.fonte_dados FORCE ROW LEVEL SECURITY;


--
-- Name: imovel_localizacao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.imovel_localizacao (
    tenant_id uuid NOT NULL,
    codigo_origem text NOT NULL,
    unidade_id uuid NOT NULL,
    geom public.geometry(Point,4674) NOT NULL,
    origem text NOT NULL,
    precisao text NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_por uuid,
    CONSTRAINT imovel_localizacao_origem_check CHECK ((origem = ANY (ARRAY['GEOCODIFICADA'::text, 'CAMPO'::text, 'DESENHADA'::text, 'IMPORTADA'::text]))),
    CONSTRAINT imovel_localizacao_precisao_check CHECK ((precisao = ANY (ARRAY['ENDERECO'::text, 'LOGRADOURO'::text, 'BAIRRO'::text, 'MANUAL'::text])))
);

ALTER TABLE ONLY public.imovel_localizacao FORCE ROW LEVEL SECURITY;


--
-- Name: imovel_situacao_mensal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.imovel_situacao_mensal (
    tenant_id uuid NOT NULL,
    competencia date NOT NULL,
    codigo_origem text NOT NULL,
    unidade_id uuid NOT NULL,
    situacao text NOT NULL,
    fonte_dados_id uuid NOT NULL,
    execucao_id uuid NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT imovel_situacao_mensal_competencia_check CHECK ((EXTRACT(day FROM competencia) = (1)::numeric)),
    CONSTRAINT imovel_situacao_mensal_situacao_check CHECK ((situacao = ANY (ARRAY['LIGADO'::text, 'FACTIVEL'::text, 'CORTADO'::text, 'POTENCIAL'::text])))
);

ALTER TABLE ONLY public.imovel_situacao_mensal FORCE ROW LEVEL SECURITY;


--
-- Name: indicador; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.indicador (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    codigo text NOT NULL,
    nome text NOT NULL,
    descricao text,
    unidade_medida text NOT NULL,
    sentido text NOT NULL,
    periodicidade text NOT NULL,
    origem text NOT NULL,
    CONSTRAINT indicador_origem_check CHECK ((origem = ANY (ARRAY['CONTRATUAL'::text, 'REGULATORIO'::text, 'OPERACIONAL'::text]))),
    CONSTRAINT indicador_periodicidade_check CHECK ((periodicidade = ANY (ARRAY['DIARIA'::text, 'MENSAL'::text, 'TRIMESTRAL'::text, 'ANUAL'::text]))),
    CONSTRAINT indicador_sentido_check CHECK ((sentido = ANY (ARRAY['MAIOR_MELHOR'::text, 'MENOR_MELHOR'::text])))
);

ALTER TABLE ONLY public.indicador FORCE ROW LEVEL SECURITY;


--
-- Name: item_contratual; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_contratual (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    contrato_id uuid NOT NULL,
    documento_id uuid NOT NULL,
    tipo text NOT NULL,
    titulo text NOT NULL,
    resumo text NOT NULL,
    clausula text,
    pagina integer NOT NULL,
    trecho text NOT NULL,
    valor_referencia numeric(18,6),
    unidade_medida text,
    data_limite date,
    periodicidade text,
    status text NOT NULL,
    origem text NOT NULL,
    modelo_ia text,
    proposto_em timestamp with time zone DEFAULT now() NOT NULL,
    revisado_por uuid,
    revisado_em timestamp with time zone,
    motivo_revisao text,
    substituido_por uuid,
    CONSTRAINT item_contratual_origem_check CHECK ((origem = ANY (ARRAY['IA'::text, 'MANUAL'::text]))),
    CONSTRAINT item_contratual_status_check CHECK ((status = ANY (ARRAY['PROPOSTO'::text, 'APROVADO'::text, 'REJEITADO'::text, 'SUBSTITUIDO'::text]))),
    CONSTRAINT item_contratual_tipo_check CHECK ((tipo = ANY (ARRAY['OBRIGACAO'::text, 'INDICADOR'::text, 'META'::text, 'PRAZO'::text, 'PENALIDADE'::text, 'REAJUSTE'::text, 'RISCO'::text, 'OUTRO'::text])))
);

ALTER TABLE ONLY public.item_contratual FORCE ROW LEVEL SECURITY;


--
-- Name: item_contratual_vetor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_contratual_vetor (
    item_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    contrato_id uuid NOT NULL,
    modelo text NOT NULL,
    vetor public.vector(384) NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.item_contratual_vetor FORCE ROW LEVEL SECURITY;


--
-- Name: limite_requisicao; Type: TABLE; Schema: public; Owner: -
--

CREATE UNLOGGED TABLE public.limite_requisicao (
    chave text NOT NULL,
    minuto bigint NOT NULL,
    contagem integer NOT NULL
);


--
-- Name: marcacao_mapa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.marcacao_mapa (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    geom public.geometry(Point,4674) NOT NULL,
    titulo text NOT NULL,
    observacao text,
    criada_por uuid NOT NULL,
    criada_em timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT marcacao_mapa_observacao_check CHECK ((length(observacao) <= 500)),
    CONSTRAINT marcacao_mapa_titulo_check CHECK (((length(titulo) >= 1) AND (length(titulo) <= 80)))
);

ALTER TABLE ONLY public.marcacao_mapa FORCE ROW LEVEL SECURITY;


--
-- Name: medicao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medicao (
    tenant_id uuid NOT NULL,
    indicador_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    periodo_inicio date NOT NULL,
    valor numeric(18,6) NOT NULL,
    numerador numeric(18,6),
    denominador numeric(18,6),
    calculado_em timestamp with time zone DEFAULT now() NOT NULL,
    versao_regra text NOT NULL
)
PARTITION BY RANGE (periodo_inicio);

ALTER TABLE ONLY public.medicao FORCE ROW LEVEL SECURITY;


--
-- Name: medicao_2026; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medicao_2026 (
    tenant_id uuid NOT NULL,
    indicador_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    periodo_inicio date NOT NULL,
    valor numeric(18,6) NOT NULL,
    numerador numeric(18,6),
    denominador numeric(18,6),
    calculado_em timestamp with time zone DEFAULT now() NOT NULL,
    versao_regra text NOT NULL
);


--
-- Name: medicao_2027; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medicao_2027 (
    tenant_id uuid NOT NULL,
    indicador_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    periodo_inicio date NOT NULL,
    valor numeric(18,6) NOT NULL,
    numerador numeric(18,6),
    denominador numeric(18,6),
    calculado_em timestamp with time zone DEFAULT now() NOT NULL,
    versao_regra text NOT NULL
);


--
-- Name: medicao_ate_2025; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medicao_ate_2025 (
    tenant_id uuid NOT NULL,
    indicador_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    periodo_inicio date NOT NULL,
    valor numeric(18,6) NOT NULL,
    numerador numeric(18,6),
    denominador numeric(18,6),
    calculado_em timestamp with time zone DEFAULT now() NOT NULL,
    versao_regra text NOT NULL
);


--
-- Name: medicao_futuro; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medicao_futuro (
    tenant_id uuid NOT NULL,
    indicador_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    periodo_inicio date NOT NULL,
    valor numeric(18,6) NOT NULL,
    numerador numeric(18,6),
    denominador numeric(18,6),
    calculado_em timestamp with time zone DEFAULT now() NOT NULL,
    versao_regra text NOT NULL
);


--
-- Name: meta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meta (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    indicador_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    vigencia_inicio date NOT NULL,
    vigencia_fim date,
    valor_meta numeric(18,6) NOT NULL,
    valor_alerta numeric(18,6),
    definido_por uuid,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    item_contratual_id uuid,
    origem text,
    CONSTRAINT meta_check CHECK (((vigencia_fim IS NULL) OR (vigencia_fim >= vigencia_inicio))),
    CONSTRAINT meta_valores CHECK (((valor_meta >= (0)::numeric) AND ((valor_alerta IS NULL) OR (valor_alerta >= (0)::numeric))))
);

ALTER TABLE ONLY public.meta FORCE ROW LEVEL SECURITY;


--
-- Name: municipio_geometria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.municipio_geometria (
    codigo_ibge character(7) NOT NULL,
    geojson jsonb NOT NULL,
    fonte text NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: perfil; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.perfil (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    nome text NOT NULL,
    sistema boolean DEFAULT false NOT NULL,
    descricao text
);

ALTER TABLE ONLY public.perfil FORCE ROW LEVEL SECURITY;


--
-- Name: perfil_permissao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.perfil_permissao (
    tenant_id uuid NOT NULL,
    perfil_id uuid NOT NULL,
    permissao text NOT NULL
);

ALTER TABLE ONLY public.perfil_permissao FORCE ROW LEVEL SECURITY;


--
-- Name: permissao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissao (
    codigo text NOT NULL,
    descricao text NOT NULL
);


--
-- Name: rede_esgoto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rede_esgoto (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    geom public.geometry(LineString,4674) NOT NULL,
    situacao text NOT NULL,
    origem text NOT NULL,
    diametro_mm integer,
    material text,
    implantada_em date,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_por uuid,
    CONSTRAINT rede_esgoto_diametro_mm_check CHECK ((diametro_mm > 0)),
    CONSTRAINT rede_esgoto_geom_check CHECK ((public.st_isvalid(geom) AND (NOT public.st_isempty(geom)))),
    CONSTRAINT rede_esgoto_origem_check CHECK ((origem = ANY (ARRAY['DEDUZIDA'::text, 'DESENHADA'::text, 'IMPORTADA'::text, 'CAMPO'::text]))),
    CONSTRAINT rede_esgoto_situacao_check CHECK ((situacao = ANY (ARRAY['PRESUMIDA'::text, 'CONFIRMADA'::text])))
);

ALTER TABLE ONLY public.rede_esgoto FORCE ROW LEVEL SECURITY;


--
-- Name: setor_geometria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.setor_geometria (
    tenant_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    geom public.geometry(MultiPolygon,4674) NOT NULL,
    origem text NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_por uuid,
    CONSTRAINT setor_geometria_geom_check CHECK (public.st_isvalid(geom)),
    CONSTRAINT setor_geometria_origem_check CHECK ((origem = ANY (ARRAY['GERADO'::text, 'DESENHADO'::text, 'IMPORTADO'::text])))
);

ALTER TABLE ONLY public.setor_geometria FORCE ROW LEVEL SECURITY;


--
-- Name: spring_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spring_session (
    primary_id character(36) NOT NULL,
    session_id character(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name character varying(100)
);


--
-- Name: spring_session_attributes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spring_session_attributes (
    session_primary_id character(36) NOT NULL,
    attribute_name character varying(200) NOT NULL,
    attribute_bytes bytea NOT NULL
);


--
-- Name: tarefa_agendada; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tarefa_agendada (
    nome text NOT NULL,
    executando_ate timestamp with time zone,
    ultima_execucao timestamp with time zone,
    ultimo_resultado text
);


--
-- Name: tarefa_em_fila; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tarefa_em_fila (
    id uuid NOT NULL,
    tipo text NOT NULL,
    tenant_id uuid NOT NULL,
    contexto jsonb NOT NULL,
    referencia uuid,
    situacao text DEFAULT 'PENDENTE'::text NOT NULL,
    criada_em timestamp with time zone DEFAULT now() NOT NULL,
    disponivel_em timestamp with time zone DEFAULT now() NOT NULL,
    executando_ate timestamp with time zone,
    tentativas integer DEFAULT 0 NOT NULL,
    ultimo_erro text,
    CONSTRAINT tarefa_em_fila_situacao_check CHECK ((situacao = ANY (ARRAY['PENDENTE'::text, 'FALHOU'::text])))
);


--
-- Name: tenant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant (
    id uuid NOT NULL,
    nome text NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.tenant FORCE ROW LEVEL SECURITY;


--
-- Name: tenant_emissor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_emissor (
    emissor text NOT NULL,
    tenant_id uuid NOT NULL
);


--
-- Name: tipo_unidade; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tipo_unidade (
    codigo text NOT NULL,
    descricao text NOT NULL
);


--
-- Name: unidade; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.unidade (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    tipo text NOT NULL,
    unidade_pai_id uuid,
    codigo text NOT NULL,
    nome text NOT NULL,
    caminho public.ltree NOT NULL,
    codigo_ibge character(7),
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    latitude numeric(9,6),
    longitude numeric(9,6),
    CONSTRAINT unidade_check CHECK (((tipo = 'MUNICIPIO'::text) = (codigo_ibge IS NOT NULL))),
    CONSTRAINT unidade_check1 CHECK (((unidade_pai_id IS NULL) = (public.nlevel(caminho) = 1))),
    CONSTRAINT unidade_coordenada_completa CHECK (((latitude IS NULL) = (longitude IS NULL))),
    CONSTRAINT unidade_latitude_check CHECK (((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric))),
    CONSTRAINT unidade_longitude_check CHECK (((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric)))
);

ALTER TABLE ONLY public.unidade FORCE ROW LEVEL SECURITY;


--
-- Name: usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    sub_externo text,
    email text NOT NULL,
    nome text NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    ultimo_acesso timestamp with time zone,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.usuario FORCE ROW LEVEL SECURITY;


--
-- Name: usuario_perfil_escopo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_perfil_escopo (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    perfil_id uuid NOT NULL,
    unidade_id uuid NOT NULL,
    valido_ate timestamp with time zone,
    concedido_por uuid,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.usuario_perfil_escopo FORCE ROW LEVEL SECURITY;


--
-- Name: evento_auditoria_2026; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria ATTACH PARTITION public.evento_auditoria_2026 FOR VALUES FROM ('2026-01-01 03:00:00+00') TO ('2027-01-01 03:00:00+00');


--
-- Name: evento_auditoria_2027; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria ATTACH PARTITION public.evento_auditoria_2027 FOR VALUES FROM ('2027-01-01 03:00:00+00') TO ('2028-01-01 03:00:00+00');


--
-- Name: evento_auditoria_outros; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria ATTACH PARTITION public.evento_auditoria_outros DEFAULT;


--
-- Name: medicao_2026; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medicao ATTACH PARTITION public.medicao_2026 FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');


--
-- Name: medicao_2027; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medicao ATTACH PARTITION public.medicao_2027 FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');


--
-- Name: medicao_ate_2025; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medicao ATTACH PARTITION public.medicao_ate_2025 FOR VALUES FROM (MINVALUE) TO ('2026-01-01');


--
-- Name: medicao_futuro; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medicao ATTACH PARTITION public.medicao_futuro DEFAULT;


--
-- Name: alerta alerta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alerta
    ADD CONSTRAINT alerta_pkey PRIMARY KEY (id);


--
-- Name: assistente_conversa assistente_conversa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assistente_conversa
    ADD CONSTRAINT assistente_conversa_pkey PRIMARY KEY (id);


--
-- Name: assistente_conversa assistente_conversa_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assistente_conversa
    ADD CONSTRAINT assistente_conversa_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: assistente_limite assistente_limite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assistente_limite
    ADD CONSTRAINT assistente_limite_pkey PRIMARY KEY (tenant_id);


--
-- Name: assistente_mensagem assistente_mensagem_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assistente_mensagem
    ADD CONSTRAINT assistente_mensagem_pkey PRIMARY KEY (id);


--
-- Name: assistente_uso_mensal assistente_uso_mensal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assistente_uso_mensal
    ADD CONSTRAINT assistente_uso_mensal_pkey PRIMARY KEY (tenant_id, mes);


--
-- Name: cadeia_auditoria cadeia_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cadeia_auditoria
    ADD CONSTRAINT cadeia_auditoria_pkey PRIMARY KEY (tenant_id);


--
-- Name: contrato contrato_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contrato
    ADD CONSTRAINT contrato_pkey PRIMARY KEY (id);


--
-- Name: contrato contrato_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contrato
    ADD CONSTRAINT contrato_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: contrato contrato_tenant_id_numero_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contrato
    ADD CONSTRAINT contrato_tenant_id_numero_key UNIQUE (tenant_id, numero);


--
-- Name: credencial_conector credencial_conector_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credencial_conector
    ADD CONSTRAINT credencial_conector_pkey PRIMARY KEY (prefixo);


--
-- Name: documento_arquivo documento_arquivo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_arquivo
    ADD CONSTRAINT documento_arquivo_pkey PRIMARY KEY (documento_id);


--
-- Name: documento_contrato documento_contrato_contrato_id_sha256_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_contrato
    ADD CONSTRAINT documento_contrato_contrato_id_sha256_key UNIQUE (contrato_id, sha256);


--
-- Name: documento_contrato documento_contrato_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_contrato
    ADD CONSTRAINT documento_contrato_pkey PRIMARY KEY (id);


--
-- Name: documento_contrato documento_contrato_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_contrato
    ADD CONSTRAINT documento_contrato_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: documento_pagina documento_pagina_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_pagina
    ADD CONSTRAINT documento_pagina_pkey PRIMARY KEY (documento_id, pagina);


--
-- Name: evento_auditoria evento_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria
    ADD CONSTRAINT evento_auditoria_pkey PRIMARY KEY (id, ocorrido_em);


--
-- Name: evento_auditoria_2026 evento_auditoria_2026_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria_2026
    ADD CONSTRAINT evento_auditoria_2026_pkey PRIMARY KEY (id, ocorrido_em);


--
-- Name: evento_auditoria evento_auditoria_tenant_id_sequencia_ocorrido_em_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria
    ADD CONSTRAINT evento_auditoria_tenant_id_sequencia_ocorrido_em_key UNIQUE (tenant_id, sequencia, ocorrido_em);


--
-- Name: evento_auditoria_2026 evento_auditoria_2026_tenant_id_sequencia_ocorrido_em_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria_2026
    ADD CONSTRAINT evento_auditoria_2026_tenant_id_sequencia_ocorrido_em_key UNIQUE (tenant_id, sequencia, ocorrido_em);


--
-- Name: evento_auditoria_2027 evento_auditoria_2027_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria_2027
    ADD CONSTRAINT evento_auditoria_2027_pkey PRIMARY KEY (id, ocorrido_em);


--
-- Name: evento_auditoria_2027 evento_auditoria_2027_tenant_id_sequencia_ocorrido_em_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria_2027
    ADD CONSTRAINT evento_auditoria_2027_tenant_id_sequencia_ocorrido_em_key UNIQUE (tenant_id, sequencia, ocorrido_em);


--
-- Name: evento_auditoria_outros evento_auditoria_outros_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria_outros
    ADD CONSTRAINT evento_auditoria_outros_pkey PRIMARY KEY (id, ocorrido_em);


--
-- Name: evento_auditoria_outros evento_auditoria_outros_tenant_id_sequencia_ocorrido_em_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_auditoria_outros
    ADD CONSTRAINT evento_auditoria_outros_tenant_id_sequencia_ocorrido_em_key UNIQUE (tenant_id, sequencia, ocorrido_em);


--
-- Name: execucao_sincronizacao execucao_sincronizacao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.execucao_sincronizacao
    ADD CONSTRAINT execucao_sincronizacao_pkey PRIMARY KEY (id);


--
-- Name: execucao_sincronizacao execucao_sincronizacao_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.execucao_sincronizacao
    ADD CONSTRAINT execucao_sincronizacao_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: fonte_dados fonte_dados_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fonte_dados
    ADD CONSTRAINT fonte_dados_pkey PRIMARY KEY (id);


--
-- Name: fonte_dados fonte_dados_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fonte_dados
    ADD CONSTRAINT fonte_dados_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: imovel_localizacao imovel_localizacao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imovel_localizacao
    ADD CONSTRAINT imovel_localizacao_pkey PRIMARY KEY (tenant_id, codigo_origem);


--
-- Name: imovel_situacao_mensal imovel_situacao_mensal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imovel_situacao_mensal
    ADD CONSTRAINT imovel_situacao_mensal_pkey PRIMARY KEY (tenant_id, competencia, codigo_origem);


--
-- Name: indicador indicador_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indicador
    ADD CONSTRAINT indicador_pkey PRIMARY KEY (id);


--
-- Name: indicador indicador_tenant_id_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indicador
    ADD CONSTRAINT indicador_tenant_id_codigo_key UNIQUE (tenant_id, codigo);


--
-- Name: indicador indicador_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indicador
    ADD CONSTRAINT indicador_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: item_contratual item_contratual_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_contratual
    ADD CONSTRAINT item_contratual_pkey PRIMARY KEY (id);


--
-- Name: item_contratual item_contratual_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_contratual
    ADD CONSTRAINT item_contratual_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: item_contratual_vetor item_contratual_vetor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_contratual_vetor
    ADD CONSTRAINT item_contratual_vetor_pkey PRIMARY KEY (item_id);


--
-- Name: limite_requisicao limite_requisicao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.limite_requisicao
    ADD CONSTRAINT limite_requisicao_pkey PRIMARY KEY (chave, minuto);


--
-- Name: marcacao_mapa marcacao_mapa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.marcacao_mapa
    ADD CONSTRAINT marcacao_mapa_pkey PRIMARY KEY (id);


--
-- Name: marcacao_mapa marcacao_mapa_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.marcacao_mapa
    ADD CONSTRAINT marcacao_mapa_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: medicao medicao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medicao
    ADD CONSTRAINT medicao_pkey PRIMARY KEY (tenant_id, indicador_id, unidade_id, periodo_inicio);


--
-- Name: medicao_2026 medicao_2026_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medicao_2026
    ADD CONSTRAINT medicao_2026_pkey PRIMARY KEY (tenant_id, indicador_id, unidade_id, periodo_inicio);


--
-- Name: medicao_2027 medicao_2027_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medicao_2027
    ADD CONSTRAINT medicao_2027_pkey PRIMARY KEY (tenant_id, indicador_id, unidade_id, periodo_inicio);


--
-- Name: medicao_ate_2025 medicao_ate_2025_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medicao_ate_2025
    ADD CONSTRAINT medicao_ate_2025_pkey PRIMARY KEY (tenant_id, indicador_id, unidade_id, periodo_inicio);


--
-- Name: medicao_futuro medicao_futuro_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medicao_futuro
    ADD CONSTRAINT medicao_futuro_pkey PRIMARY KEY (tenant_id, indicador_id, unidade_id, periodo_inicio);


--
-- Name: meta meta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meta
    ADD CONSTRAINT meta_pkey PRIMARY KEY (id);


--
-- Name: municipio_geometria municipio_geometria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.municipio_geometria
    ADD CONSTRAINT municipio_geometria_pkey PRIMARY KEY (codigo_ibge);


--
-- Name: perfil_permissao perfil_permissao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil_permissao
    ADD CONSTRAINT perfil_permissao_pkey PRIMARY KEY (perfil_id, permissao);


--
-- Name: perfil perfil_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil
    ADD CONSTRAINT perfil_pkey PRIMARY KEY (id);


--
-- Name: perfil perfil_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil
    ADD CONSTRAINT perfil_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: perfil perfil_tenant_id_nome_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil
    ADD CONSTRAINT perfil_tenant_id_nome_key UNIQUE (tenant_id, nome);


--
-- Name: permissao permissao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissao
    ADD CONSTRAINT permissao_pkey PRIMARY KEY (codigo);


--
-- Name: rede_esgoto rede_esgoto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rede_esgoto
    ADD CONSTRAINT rede_esgoto_pkey PRIMARY KEY (id);


--
-- Name: rede_esgoto rede_esgoto_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rede_esgoto
    ADD CONSTRAINT rede_esgoto_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: setor_geometria setor_geometria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.setor_geometria
    ADD CONSTRAINT setor_geometria_pkey PRIMARY KEY (tenant_id, unidade_id);


--
-- Name: spring_session_attributes spring_session_attributes_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name);


--
-- Name: spring_session spring_session_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session
    ADD CONSTRAINT spring_session_pk PRIMARY KEY (primary_id);


--
-- Name: tarefa_agendada tarefa_agendada_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarefa_agendada
    ADD CONSTRAINT tarefa_agendada_pkey PRIMARY KEY (nome);


--
-- Name: tarefa_em_fila tarefa_em_fila_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarefa_em_fila
    ADD CONSTRAINT tarefa_em_fila_pkey PRIMARY KEY (id);


--
-- Name: tenant_emissor tenant_emissor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_emissor
    ADD CONSTRAINT tenant_emissor_pkey PRIMARY KEY (emissor);


--
-- Name: tenant tenant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_pkey PRIMARY KEY (id);


--
-- Name: tipo_unidade tipo_unidade_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_unidade
    ADD CONSTRAINT tipo_unidade_pkey PRIMARY KEY (codigo);


--
-- Name: unidade unidade_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidade
    ADD CONSTRAINT unidade_pkey PRIMARY KEY (id);


--
-- Name: unidade unidade_tenant_id_caminho_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidade
    ADD CONSTRAINT unidade_tenant_id_caminho_key UNIQUE (tenant_id, caminho);


--
-- Name: unidade unidade_tenant_id_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidade
    ADD CONSTRAINT unidade_tenant_id_codigo_key UNIQUE (tenant_id, codigo);


--
-- Name: unidade unidade_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidade
    ADD CONSTRAINT unidade_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: usuario_perfil_escopo usuario_perfil_escopo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_perfil_escopo
    ADD CONSTRAINT usuario_perfil_escopo_pkey PRIMARY KEY (id);


--
-- Name: usuario_perfil_escopo usuario_perfil_escopo_usuario_id_perfil_id_unidade_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_perfil_escopo
    ADD CONSTRAINT usuario_perfil_escopo_usuario_id_perfil_id_unidade_id_key UNIQUE (usuario_id, perfil_id, unidade_id);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id);


--
-- Name: usuario usuario_tenant_id_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_tenant_id_id_key UNIQUE (tenant_id, id);


--
-- Name: usuario usuario_tenant_id_sub_externo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_tenant_id_sub_externo_key UNIQUE (tenant_id, sub_externo);


--
-- Name: idx_evento_tenant_seq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_tenant_seq ON ONLY public.evento_auditoria USING btree (tenant_id, sequencia);


--
-- Name: evento_auditoria_2026_tenant_id_sequencia_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX evento_auditoria_2026_tenant_id_sequencia_idx ON public.evento_auditoria_2026 USING btree (tenant_id, sequencia);


--
-- Name: evento_auditoria_2027_tenant_id_sequencia_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX evento_auditoria_2027_tenant_id_sequencia_idx ON public.evento_auditoria_2027 USING btree (tenant_id, sequencia);


--
-- Name: evento_auditoria_outros_tenant_id_sequencia_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX evento_auditoria_outros_tenant_id_sequencia_idx ON public.evento_auditoria_outros USING btree (tenant_id, sequencia);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_alerta_abertos; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alerta_abertos ON public.alerta USING btree (tenant_id, resolvido_em, severidade);


--
-- Name: idx_conversa_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversa_usuario ON public.assistente_conversa USING btree (usuario_id, atualizado_em DESC);


--
-- Name: idx_execucao_fonte; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_execucao_fonte ON public.execucao_sincronizacao USING btree (fonte_dados_id, inicio DESC);


--
-- Name: idx_imovel_localizacao_geom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_imovel_localizacao_geom ON public.imovel_localizacao USING gist (geom);


--
-- Name: idx_imovel_situacao_matricula; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_imovel_situacao_matricula ON public.imovel_situacao_mensal USING btree (tenant_id, codigo_origem, competencia);


--
-- Name: idx_imovel_situacao_unidade; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_imovel_situacao_unidade ON public.imovel_situacao_mensal USING btree (tenant_id, competencia, unidade_id);


--
-- Name: idx_item_contrato_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_contrato_status ON public.item_contratual USING btree (contrato_id, status);


--
-- Name: idx_item_vetor_cosseno; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_vetor_cosseno ON public.item_contratual_vetor USING hnsw (vetor public.vector_cosine_ops);


--
-- Name: idx_marcacao_mapa_geom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_marcacao_mapa_geom ON public.marcacao_mapa USING gist (geom);


--
-- Name: idx_medicao_unidade; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_medicao_unidade ON ONLY public.medicao USING btree (unidade_id, periodo_inicio);


--
-- Name: idx_mensagem_conversa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mensagem_conversa ON public.assistente_mensagem USING btree (conversa_id, criado_em);


--
-- Name: idx_meta_indicador_unidade; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meta_indicador_unidade ON public.meta USING btree (indicador_id, unidade_id);


--
-- Name: idx_rede_esgoto_geom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rede_esgoto_geom ON public.rede_esgoto USING gist (geom);


--
-- Name: idx_rede_esgoto_unidade; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rede_esgoto_unidade ON public.rede_esgoto USING btree (tenant_id, unidade_id);


--
-- Name: idx_setor_geometria_geom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_setor_geometria_geom ON public.setor_geometria USING gist (geom);


--
-- Name: idx_unidade_caminho; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_unidade_caminho ON public.unidade USING gist (caminho);


--
-- Name: idx_unidade_pai; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_unidade_pai ON public.unidade USING btree (unidade_pai_id);


--
-- Name: idx_upe_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_upe_usuario ON public.usuario_perfil_escopo USING btree (usuario_id);


--
-- Name: medicao_2026_unidade_id_periodo_inicio_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX medicao_2026_unidade_id_periodo_inicio_idx ON public.medicao_2026 USING btree (unidade_id, periodo_inicio);


--
-- Name: medicao_2027_unidade_id_periodo_inicio_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX medicao_2027_unidade_id_periodo_inicio_idx ON public.medicao_2027 USING btree (unidade_id, periodo_inicio);


--
-- Name: medicao_ate_2025_unidade_id_periodo_inicio_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX medicao_ate_2025_unidade_id_periodo_inicio_idx ON public.medicao_ate_2025 USING btree (unidade_id, periodo_inicio);


--
-- Name: medicao_futuro_unidade_id_periodo_inicio_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX medicao_futuro_unidade_id_periodo_inicio_idx ON public.medicao_futuro USING btree (unidade_id, periodo_inicio);


--
-- Name: spring_session_ix1; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX spring_session_ix1 ON public.spring_session USING btree (session_id);


--
-- Name: spring_session_ix2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_session_ix2 ON public.spring_session USING btree (expiry_time);


--
-- Name: spring_session_ix3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_session_ix3 ON public.spring_session USING btree (principal_name);


--
-- Name: tarefa_em_fila_pendentes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tarefa_em_fila_pendentes ON public.tarefa_em_fila USING btree (disponivel_em) WHERE (situacao = 'PENDENTE'::text);


--
-- Name: uq_alerta_aberto; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_alerta_aberto ON public.alerta USING btree (tenant_id, chave) WHERE (resolvido_em IS NULL);


--
-- Name: uq_usuario_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_usuario_email ON public.usuario USING btree (tenant_id, lower(email));


--
-- Name: evento_auditoria_2026_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.evento_auditoria_pkey ATTACH PARTITION public.evento_auditoria_2026_pkey;


--
-- Name: evento_auditoria_2026_tenant_id_sequencia_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_evento_tenant_seq ATTACH PARTITION public.evento_auditoria_2026_tenant_id_sequencia_idx;


--
-- Name: evento_auditoria_2026_tenant_id_sequencia_ocorrido_em_key; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.evento_auditoria_tenant_id_sequencia_ocorrido_em_key ATTACH PARTITION public.evento_auditoria_2026_tenant_id_sequencia_ocorrido_em_key;


--
-- Name: evento_auditoria_2027_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.evento_auditoria_pkey ATTACH PARTITION public.evento_auditoria_2027_pkey;


--
-- Name: evento_auditoria_2027_tenant_id_sequencia_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_evento_tenant_seq ATTACH PARTITION public.evento_auditoria_2027_tenant_id_sequencia_idx;


--
-- Name: evento_auditoria_2027_tenant_id_sequencia_ocorrido_em_key; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.evento_auditoria_tenant_id_sequencia_ocorrido_em_key ATTACH PARTITION public.evento_auditoria_2027_tenant_id_sequencia_ocorrido_em_key;


--
-- Name: evento_auditoria_outros_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.evento_auditoria_pkey ATTACH PARTITION public.evento_auditoria_outros_pkey;


--
-- Name: evento_auditoria_outros_tenant_id_sequencia_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_evento_tenant_seq ATTACH PARTITION public.evento_auditoria_outros_tenant_id_sequencia_idx;


--
-- Name: evento_auditoria_outros_tenant_id_sequencia_ocorrido_em_key; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.evento_auditoria_tenant_id_sequencia_ocorrido_em_key ATTACH PARTITION public.evento_auditoria_outros_tenant_id_sequencia_ocorrido_em_key;


--
-- Name: medicao_2026_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.medicao_pkey ATTACH PARTITION public.medicao_2026_pkey;


--
-- Name: medicao_2026_unidade_id_periodo_inicio_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_medicao_unidade ATTACH PARTITION public.medicao_2026_unidade_id_periodo_inicio_idx;


--
-- Name: medicao_2027_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.medicao_pkey ATTACH PARTITION public.medicao_2027_pkey;


--
-- Name: medicao_2027_unidade_id_periodo_inicio_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_medicao_unidade ATTACH PARTITION public.medicao_2027_unidade_id_periodo_inicio_idx;


--
-- Name: medicao_ate_2025_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.medicao_pkey ATTACH PARTITION public.medicao_ate_2025_pkey;


--
-- Name: medicao_ate_2025_unidade_id_periodo_inicio_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_medicao_unidade ATTACH PARTITION public.medicao_ate_2025_unidade_id_periodo_inicio_idx;


--
-- Name: medicao_futuro_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.medicao_pkey ATTACH PARTITION public.medicao_futuro_pkey;


--
-- Name: medicao_futuro_unidade_id_periodo_inicio_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_medicao_unidade ATTACH PARTITION public.medicao_futuro_unidade_id_periodo_inicio_idx;


--
-- Name: alerta alerta_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alerta
    ADD CONSTRAINT alerta_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: alerta alerta_tenant_id_unidade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alerta
    ADD CONSTRAINT alerta_tenant_id_unidade_id_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: assistente_conversa assistente_conversa_tenant_id_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assistente_conversa
    ADD CONSTRAINT assistente_conversa_tenant_id_usuario_id_fkey FOREIGN KEY (tenant_id, usuario_id) REFERENCES public.usuario(tenant_id, id);


--
-- Name: assistente_limite assistente_limite_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assistente_limite
    ADD CONSTRAINT assistente_limite_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: assistente_mensagem assistente_mensagem_tenant_id_conversa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assistente_mensagem
    ADD CONSTRAINT assistente_mensagem_tenant_id_conversa_id_fkey FOREIGN KEY (tenant_id, conversa_id) REFERENCES public.assistente_conversa(tenant_id, id);


--
-- Name: assistente_uso_mensal assistente_uso_mensal_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assistente_uso_mensal
    ADD CONSTRAINT assistente_uso_mensal_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: cadeia_auditoria cadeia_auditoria_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cadeia_auditoria
    ADD CONSTRAINT cadeia_auditoria_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: contrato contrato_tenant_id_unidade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contrato
    ADD CONSTRAINT contrato_tenant_id_unidade_id_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: credencial_conector credencial_conector_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credencial_conector
    ADD CONSTRAINT credencial_conector_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: credencial_conector credencial_conector_tenant_id_fonte_dados_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credencial_conector
    ADD CONSTRAINT credencial_conector_tenant_id_fonte_dados_id_fkey FOREIGN KEY (tenant_id, fonte_dados_id) REFERENCES public.fonte_dados(tenant_id, id);


--
-- Name: documento_arquivo documento_arquivo_tenant_id_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_arquivo
    ADD CONSTRAINT documento_arquivo_tenant_id_documento_id_fkey FOREIGN KEY (tenant_id, documento_id) REFERENCES public.documento_contrato(tenant_id, id);


--
-- Name: documento_contrato documento_contrato_tenant_id_contrato_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_contrato
    ADD CONSTRAINT documento_contrato_tenant_id_contrato_id_fkey FOREIGN KEY (tenant_id, contrato_id) REFERENCES public.contrato(tenant_id, id);


--
-- Name: documento_pagina documento_pagina_tenant_id_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_pagina
    ADD CONSTRAINT documento_pagina_tenant_id_documento_id_fkey FOREIGN KEY (tenant_id, documento_id) REFERENCES public.documento_contrato(tenant_id, id);


--
-- Name: execucao_sincronizacao execucao_sincronizacao_tenant_id_fonte_dados_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.execucao_sincronizacao
    ADD CONSTRAINT execucao_sincronizacao_tenant_id_fonte_dados_id_fkey FOREIGN KEY (tenant_id, fonte_dados_id) REFERENCES public.fonte_dados(tenant_id, id);


--
-- Name: fonte_dados fonte_dados_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fonte_dados
    ADD CONSTRAINT fonte_dados_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: imovel_localizacao imovel_localizacao_tenant_id_unidade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imovel_localizacao
    ADD CONSTRAINT imovel_localizacao_tenant_id_unidade_id_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: imovel_situacao_mensal imovel_situacao_mensal_tenant_id_execucao_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imovel_situacao_mensal
    ADD CONSTRAINT imovel_situacao_mensal_tenant_id_execucao_id_fkey FOREIGN KEY (tenant_id, execucao_id) REFERENCES public.execucao_sincronizacao(tenant_id, id);


--
-- Name: imovel_situacao_mensal imovel_situacao_mensal_tenant_id_fonte_dados_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imovel_situacao_mensal
    ADD CONSTRAINT imovel_situacao_mensal_tenant_id_fonte_dados_id_fkey FOREIGN KEY (tenant_id, fonte_dados_id) REFERENCES public.fonte_dados(tenant_id, id);


--
-- Name: imovel_situacao_mensal imovel_situacao_mensal_tenant_id_unidade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imovel_situacao_mensal
    ADD CONSTRAINT imovel_situacao_mensal_tenant_id_unidade_id_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: indicador indicador_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indicador
    ADD CONSTRAINT indicador_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: item_contratual item_contratual_tenant_id_contrato_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_contratual
    ADD CONSTRAINT item_contratual_tenant_id_contrato_id_fkey FOREIGN KEY (tenant_id, contrato_id) REFERENCES public.contrato(tenant_id, id);


--
-- Name: item_contratual item_contratual_tenant_id_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_contratual
    ADD CONSTRAINT item_contratual_tenant_id_documento_id_fkey FOREIGN KEY (tenant_id, documento_id) REFERENCES public.documento_contrato(tenant_id, id);


--
-- Name: item_contratual_vetor item_contratual_vetor_tenant_id_contrato_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_contratual_vetor
    ADD CONSTRAINT item_contratual_vetor_tenant_id_contrato_id_fkey FOREIGN KEY (tenant_id, contrato_id) REFERENCES public.contrato(tenant_id, id);


--
-- Name: item_contratual_vetor item_contratual_vetor_tenant_id_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_contratual_vetor
    ADD CONSTRAINT item_contratual_vetor_tenant_id_item_id_fkey FOREIGN KEY (tenant_id, item_id) REFERENCES public.item_contratual(tenant_id, id);


--
-- Name: marcacao_mapa marcacao_mapa_tenant_id_unidade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.marcacao_mapa
    ADD CONSTRAINT marcacao_mapa_tenant_id_unidade_id_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: medicao medicao_tenant_id_indicador_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.medicao
    ADD CONSTRAINT medicao_tenant_id_indicador_id_fkey FOREIGN KEY (tenant_id, indicador_id) REFERENCES public.indicador(tenant_id, id);


--
-- Name: medicao medicao_tenant_id_unidade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.medicao
    ADD CONSTRAINT medicao_tenant_id_unidade_id_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: meta meta_tenant_id_indicador_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meta
    ADD CONSTRAINT meta_tenant_id_indicador_id_fkey FOREIGN KEY (tenant_id, indicador_id) REFERENCES public.indicador(tenant_id, id);


--
-- Name: meta meta_tenant_id_unidade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meta
    ADD CONSTRAINT meta_tenant_id_unidade_id_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: perfil_permissao perfil_permissao_permissao_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil_permissao
    ADD CONSTRAINT perfil_permissao_permissao_fkey FOREIGN KEY (permissao) REFERENCES public.permissao(codigo);


--
-- Name: perfil_permissao perfil_permissao_tenant_id_perfil_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil_permissao
    ADD CONSTRAINT perfil_permissao_tenant_id_perfil_id_fkey FOREIGN KEY (tenant_id, perfil_id) REFERENCES public.perfil(tenant_id, id) ON DELETE CASCADE;


--
-- Name: perfil perfil_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.perfil
    ADD CONSTRAINT perfil_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: rede_esgoto rede_esgoto_tenant_id_unidade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rede_esgoto
    ADD CONSTRAINT rede_esgoto_tenant_id_unidade_id_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: setor_geometria setor_geometria_tenant_id_unidade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.setor_geometria
    ADD CONSTRAINT setor_geometria_tenant_id_unidade_id_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: spring_session_attributes spring_session_attributes_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES public.spring_session(primary_id) ON DELETE CASCADE;


--
-- Name: tenant_emissor tenant_emissor_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_emissor
    ADD CONSTRAINT tenant_emissor_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: unidade unidade_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidade
    ADD CONSTRAINT unidade_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: unidade unidade_tenant_id_unidade_pai_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidade
    ADD CONSTRAINT unidade_tenant_id_unidade_pai_id_fkey FOREIGN KEY (tenant_id, unidade_pai_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: unidade unidade_tipo_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidade
    ADD CONSTRAINT unidade_tipo_fkey FOREIGN KEY (tipo) REFERENCES public.tipo_unidade(codigo);


--
-- Name: usuario_perfil_escopo usuario_perfil_escopo_tenant_id_concedido_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_perfil_escopo
    ADD CONSTRAINT usuario_perfil_escopo_tenant_id_concedido_por_fkey FOREIGN KEY (tenant_id, concedido_por) REFERENCES public.usuario(tenant_id, id);


--
-- Name: usuario_perfil_escopo usuario_perfil_escopo_tenant_id_perfil_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_perfil_escopo
    ADD CONSTRAINT usuario_perfil_escopo_tenant_id_perfil_id_fkey FOREIGN KEY (tenant_id, perfil_id) REFERENCES public.perfil(tenant_id, id);


--
-- Name: usuario_perfil_escopo usuario_perfil_escopo_tenant_id_unidade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_perfil_escopo
    ADD CONSTRAINT usuario_perfil_escopo_tenant_id_unidade_id_fkey FOREIGN KEY (tenant_id, unidade_id) REFERENCES public.unidade(tenant_id, id);


--
-- Name: usuario_perfil_escopo usuario_perfil_escopo_tenant_id_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_perfil_escopo
    ADD CONSTRAINT usuario_perfil_escopo_tenant_id_usuario_id_fkey FOREIGN KEY (tenant_id, usuario_id) REFERENCES public.usuario(tenant_id, id);


--
-- Name: usuario usuario_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: alerta; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.alerta ENABLE ROW LEVEL SECURITY;

--
-- Name: alerta alerta_escopo; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY alerta_escopo ON public.alerta USING (((tenant_id = public.app_tenant()) AND ((unidade_id IS NULL) OR (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos())))))));


--
-- Name: documento_arquivo arquivo_pelo_documento; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY arquivo_pelo_documento ON public.documento_arquivo USING (((tenant_id = public.app_tenant()) AND (documento_id IN ( SELECT documento_contrato.id
   FROM public.documento_contrato))));


--
-- Name: assistente_conversa; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.assistente_conversa ENABLE ROW LEVEL SECURITY;

--
-- Name: assistente_limite; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.assistente_limite ENABLE ROW LEVEL SECURITY;

--
-- Name: assistente_mensagem; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.assistente_mensagem ENABLE ROW LEVEL SECURITY;

--
-- Name: assistente_uso_mensal; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.assistente_uso_mensal ENABLE ROW LEVEL SECURITY;

--
-- Name: cadeia_auditoria; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.cadeia_auditoria ENABLE ROW LEVEL SECURITY;

--
-- Name: cadeia_auditoria cadeia_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY cadeia_tenant ON public.cadeia_auditoria USING ((tenant_id = public.app_tenant()));


--
-- Name: contrato; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.contrato ENABLE ROW LEVEL SECURITY;

--
-- Name: contrato contrato_alteracao; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY contrato_alteracao ON public.contrato FOR UPDATE USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: contrato contrato_escrita; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY contrato_escrita ON public.contrato FOR INSERT WITH CHECK (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: contrato contrato_leitura; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY contrato_leitura ON public.contrato FOR SELECT USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE ((u.caminho OPERATOR(public.<@) ANY (public.app_escopos())) OR (u.caminho OPERATOR(public.@>) ANY (public.app_escopos())))))));


--
-- Name: assistente_conversa conversa_propria; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY conversa_propria ON public.assistente_conversa USING (((tenant_id = public.app_tenant()) AND (usuario_id = public.app_usuario())));


--
-- Name: documento_arquivo; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.documento_arquivo ENABLE ROW LEVEL SECURITY;

--
-- Name: documento_contrato; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.documento_contrato ENABLE ROW LEVEL SECURITY;

--
-- Name: documento_pagina; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.documento_pagina ENABLE ROW LEVEL SECURITY;

--
-- Name: documento_contrato documento_pelo_contrato; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY documento_pelo_contrato ON public.documento_contrato USING (((tenant_id = public.app_tenant()) AND (contrato_id IN ( SELECT contrato.id
   FROM public.contrato))));


--
-- Name: evento_auditoria; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.evento_auditoria ENABLE ROW LEVEL SECURITY;

--
-- Name: evento_auditoria evento_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY evento_tenant ON public.evento_auditoria USING ((tenant_id = public.app_tenant()));


--
-- Name: execucao_sincronizacao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.execucao_sincronizacao ENABLE ROW LEVEL SECURITY;

--
-- Name: execucao_sincronizacao execucao_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY execucao_tenant ON public.execucao_sincronizacao USING ((tenant_id = public.app_tenant()));


--
-- Name: fonte_dados; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.fonte_dados ENABLE ROW LEVEL SECURITY;

--
-- Name: fonte_dados fonte_dados_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY fonte_dados_tenant ON public.fonte_dados USING ((tenant_id = public.app_tenant()));


--
-- Name: imovel_localizacao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.imovel_localizacao ENABLE ROW LEVEL SECURITY;

--
-- Name: imovel_localizacao imovel_localizacao_escopo; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY imovel_localizacao_escopo ON public.imovel_localizacao USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: imovel_situacao_mensal imovel_situacao_escopo; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY imovel_situacao_escopo ON public.imovel_situacao_mensal USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: imovel_situacao_mensal; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.imovel_situacao_mensal ENABLE ROW LEVEL SECURITY;

--
-- Name: indicador; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.indicador ENABLE ROW LEVEL SECURITY;

--
-- Name: indicador indicador_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY indicador_tenant ON public.indicador USING ((tenant_id = public.app_tenant()));


--
-- Name: item_contratual; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.item_contratual ENABLE ROW LEVEL SECURITY;

--
-- Name: item_contratual_vetor; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.item_contratual_vetor ENABLE ROW LEVEL SECURITY;

--
-- Name: item_contratual item_pelo_contrato; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY item_pelo_contrato ON public.item_contratual USING (((tenant_id = public.app_tenant()) AND (contrato_id IN ( SELECT contrato.id
   FROM public.contrato))));


--
-- Name: assistente_limite limite_do_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY limite_do_tenant ON public.assistente_limite USING ((tenant_id = public.app_tenant()));


--
-- Name: marcacao_mapa; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.marcacao_mapa ENABLE ROW LEVEL SECURITY;

--
-- Name: marcacao_mapa marcacao_mapa_escopo; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY marcacao_mapa_escopo ON public.marcacao_mapa USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: medicao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.medicao ENABLE ROW LEVEL SECURITY;

--
-- Name: medicao medicao_escopo; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY medicao_escopo ON public.medicao USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: assistente_mensagem mensagem_da_conversa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY mensagem_da_conversa ON public.assistente_mensagem USING (((tenant_id = public.app_tenant()) AND (conversa_id IN ( SELECT assistente_conversa.id
   FROM public.assistente_conversa))));


--
-- Name: meta; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.meta ENABLE ROW LEVEL SECURITY;

--
-- Name: meta meta_escrita_alteracao; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY meta_escrita_alteracao ON public.meta FOR UPDATE USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: meta meta_escrita_exclusao; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY meta_escrita_exclusao ON public.meta FOR DELETE USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: meta meta_escrita_insercao; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY meta_escrita_insercao ON public.meta FOR INSERT WITH CHECK (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: meta meta_leitura; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY meta_leitura ON public.meta FOR SELECT USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE ((u.caminho OPERATOR(public.<@) ANY (public.app_escopos())) OR (u.caminho OPERATOR(public.@>) ANY (public.app_escopos())))))));


--
-- Name: documento_pagina pagina_pelo_documento; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY pagina_pelo_documento ON public.documento_pagina USING (((tenant_id = public.app_tenant()) AND (documento_id IN ( SELECT documento_contrato.id
   FROM public.documento_contrato))));


--
-- Name: perfil; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.perfil ENABLE ROW LEVEL SECURITY;

--
-- Name: perfil_permissao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.perfil_permissao ENABLE ROW LEVEL SECURITY;

--
-- Name: perfil_permissao perfil_permissao_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY perfil_permissao_tenant ON public.perfil_permissao USING ((tenant_id = public.app_tenant()));


--
-- Name: perfil perfil_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY perfil_tenant ON public.perfil USING ((tenant_id = public.app_tenant()));


--
-- Name: rede_esgoto; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.rede_esgoto ENABLE ROW LEVEL SECURITY;

--
-- Name: rede_esgoto rede_esgoto_escopo; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY rede_esgoto_escopo ON public.rede_esgoto USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: setor_geometria; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.setor_geometria ENABLE ROW LEVEL SECURITY;

--
-- Name: setor_geometria setor_geometria_escopo; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY setor_geometria_escopo ON public.setor_geometria USING (((tenant_id = public.app_tenant()) AND (unidade_id IN ( SELECT u.id
   FROM public.unidade u
  WHERE (u.caminho OPERATOR(public.<@) ANY (public.app_escopos()))))));


--
-- Name: tenant; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.tenant ENABLE ROW LEVEL SECURITY;

--
-- Name: tenant tenant_proprio; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_proprio ON public.tenant USING ((id = public.app_tenant()));


--
-- Name: unidade; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.unidade ENABLE ROW LEVEL SECURITY;

--
-- Name: unidade unidade_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY unidade_tenant ON public.unidade USING ((tenant_id = public.app_tenant()));


--
-- Name: usuario_perfil_escopo upe_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY upe_tenant ON public.usuario_perfil_escopo USING ((tenant_id = public.app_tenant()));


--
-- Name: assistente_uso_mensal uso_do_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY uso_do_tenant ON public.assistente_uso_mensal USING ((tenant_id = public.app_tenant()));


--
-- Name: usuario; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.usuario ENABLE ROW LEVEL SECURITY;

--
-- Name: usuario_perfil_escopo; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.usuario_perfil_escopo ENABLE ROW LEVEL SECURITY;

--
-- Name: usuario usuario_tenant; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY usuario_tenant ON public.usuario USING ((tenant_id = public.app_tenant()));


--
-- Name: item_contratual_vetor vetor_pelo_contrato; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY vetor_pelo_contrato ON public.item_contratual_vetor USING (((tenant_id = public.app_tenant()) AND (contrato_id IN ( SELECT contrato.id
   FROM public.contrato))));


--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: -
--

GRANT USAGE ON SCHEMA public TO aurora_app;


--
-- Name: FUNCTION app_escopos(); Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON FUNCTION public.app_escopos() TO aurora_app;


--
-- Name: FUNCTION app_tenant(); Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON FUNCTION public.app_tenant() TO aurora_app;


--
-- Name: FUNCTION app_usuario(); Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON FUNCTION public.app_usuario() TO aurora_app;


--
-- Name: TABLE alerta; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.alerta TO aurora_app;


--
-- Name: TABLE assistente_conversa; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.assistente_conversa TO aurora_app;


--
-- Name: TABLE assistente_limite; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.assistente_limite TO aurora_app;


--
-- Name: TABLE assistente_mensagem; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.assistente_mensagem TO aurora_app;


--
-- Name: TABLE assistente_uso_mensal; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.assistente_uso_mensal TO aurora_app;


--
-- Name: TABLE cadeia_auditoria; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.cadeia_auditoria TO aurora_app;


--
-- Name: TABLE contrato; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.contrato TO aurora_app;


--
-- Name: TABLE credencial_conector; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.credencial_conector TO aurora_app;


--
-- Name: TABLE documento_arquivo; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.documento_arquivo TO aurora_app;


--
-- Name: TABLE documento_contrato; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.documento_contrato TO aurora_app;


--
-- Name: TABLE documento_pagina; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.documento_pagina TO aurora_app;


--
-- Name: TABLE evento_auditoria; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.evento_auditoria TO aurora_app;


--
-- Name: TABLE execucao_sincronizacao; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.execucao_sincronizacao TO aurora_app;


--
-- Name: TABLE fonte_dados; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.fonte_dados TO aurora_app;


--
-- Name: TABLE imovel_localizacao; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.imovel_localizacao TO aurora_app;


--
-- Name: TABLE imovel_situacao_mensal; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.imovel_situacao_mensal TO aurora_app;


--
-- Name: TABLE indicador; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.indicador TO aurora_app;


--
-- Name: TABLE item_contratual; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.item_contratual TO aurora_app;


--
-- Name: TABLE item_contratual_vetor; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.item_contratual_vetor TO aurora_app;


--
-- Name: TABLE limite_requisicao; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.limite_requisicao TO aurora_app;


--
-- Name: TABLE marcacao_mapa; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE ON TABLE public.marcacao_mapa TO aurora_app;


--
-- Name: TABLE medicao; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.medicao TO aurora_app;


--
-- Name: TABLE meta; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.meta TO aurora_app;


--
-- Name: TABLE municipio_geometria; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.municipio_geometria TO aurora_app;


--
-- Name: TABLE perfil; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.perfil TO aurora_app;


--
-- Name: TABLE perfil_permissao; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.perfil_permissao TO aurora_app;


--
-- Name: TABLE permissao; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.permissao TO aurora_app;


--
-- Name: TABLE rede_esgoto; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.rede_esgoto TO aurora_app;


--
-- Name: TABLE setor_geometria; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.setor_geometria TO aurora_app;


--
-- Name: TABLE spring_session; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.spring_session TO aurora_app;


--
-- Name: TABLE spring_session_attributes; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.spring_session_attributes TO aurora_app;


--
-- Name: TABLE tarefa_agendada; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,UPDATE ON TABLE public.tarefa_agendada TO aurora_app;


--
-- Name: TABLE tarefa_em_fila; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.tarefa_em_fila TO aurora_app;


--
-- Name: TABLE tenant; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.tenant TO aurora_app;


--
-- Name: TABLE tenant_emissor; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.tenant_emissor TO aurora_app;


--
-- Name: TABLE tipo_unidade; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.tipo_unidade TO aurora_app;


--
-- Name: TABLE unidade; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.unidade TO aurora_app;


--
-- Name: TABLE usuario; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE public.usuario TO aurora_app;


--
-- Name: TABLE usuario_perfil_escopo; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.usuario_perfil_escopo TO aurora_app;


--
-- PostgreSQL database dump complete
--

\unrestrict rua6oGONy4IwLjFeWj6u9qIpCbVWE9ockKCtjXiJRsNSrUvw3VnoQT0GgfdPQGZ

