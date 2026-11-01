-- Retenção da trilha de auditoria (doc 08, 8.7; ADR 0029): prazo configurável por tenant, expurgo só do começo da
-- cadeia e âncora para a verificação de integridade continuar provando que nada sumiu fora do expurgo.

-- Prazo em meses. O mínimo (12) fica no banco: nem a aplicação consegue apagar eventos mais novos que isso.
ALTER TABLE tenant ADD COLUMN auditoria_retencao_meses smallint NOT NULL DEFAULT 60
    CHECK (auditoria_retencao_meses BETWEEN 12 AND 240);
GRANT UPDATE (auditoria_retencao_meses) ON tenant TO aurora_app;

-- Cada expurgo: até qual sequência apagou e o hash do último evento apagado (o primeiro evento que ficou aponta para
-- ele). Só a função abaixo grava aqui.
CREATE TABLE auditoria_expurgo (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES tenant (id),
    executado_em  timestamptz NOT NULL,
    limite        timestamptz NOT NULL,
    ate_sequencia bigint NOT NULL,
    ultimo_hash   bytea NOT NULL,
    quantidade    bigint NOT NULL
);
CREATE INDEX idx_auditoria_expurgo_tenant ON auditoria_expurgo (tenant_id, ate_sequencia DESC);

ALTER TABLE auditoria_expurgo ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria_expurgo FORCE ROW LEVEL SECURITY;
CREATE POLICY auditoria_expurgo_tenant ON auditoria_expurgo USING (tenant_id = app_tenant());
GRANT SELECT ON auditoria_expurgo TO aurora_app;

-- Apaga, no tenant do contexto, os eventos anteriores ao prazo. Apaga sempre um prefixo contínuo da cadeia: para no
-- primeiro evento (em sequência) que ainda está no prazo, mesmo que haja eventos mais antigos depois dele (ex.: login
-- importado com o horário original). A aplicação não tem DELETE na trilha; só esta função apaga, e só o vencido.
CREATE FUNCTION expurgar_auditoria_vencida() RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_tenant   uuid := app_tenant();
    v_meses    smallint;
    v_limite   timestamptz;
    v_primeira bigint;
    v_ultimo   record;
    v_apagados bigint;
BEGIN
    SELECT auditoria_retencao_meses INTO STRICT v_meses FROM tenant WHERE id = v_tenant;
    v_limite := now() - make_interval(months => v_meses);

    SELECT min(sequencia) INTO v_primeira FROM evento_auditoria
     WHERE tenant_id = v_tenant AND ocorrido_em >= v_limite;

    SELECT sequencia, hash INTO v_ultimo FROM evento_auditoria
     WHERE tenant_id = v_tenant AND (v_primeira IS NULL OR sequencia < v_primeira)
     ORDER BY sequencia DESC LIMIT 1;
    IF NOT FOUND THEN
        RETURN 0;
    END IF;

    DELETE FROM evento_auditoria WHERE tenant_id = v_tenant AND sequencia <= v_ultimo.sequencia;
    GET DIAGNOSTICS v_apagados = ROW_COUNT;

    INSERT INTO auditoria_expurgo (id, tenant_id, executado_em, limite, ate_sequencia, ultimo_hash, quantidade)
    VALUES (gen_random_uuid(), v_tenant, now(), v_limite, v_ultimo.sequencia, v_ultimo.hash, v_apagados);
    RETURN v_apagados;
END;
$$;
REVOKE ALL ON FUNCTION expurgar_auditoria_vencida() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION expurgar_auditoria_vencida() TO aurora_app;

-- Partição anual da trilha criada com antecedência (antes de existirem eventos daquele ano na partição padrão).
CREATE FUNCTION garantir_particao_auditoria(p_ano integer) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_nome text := format('evento_auditoria_%s', p_ano);
BEGIN
    IF p_ano < 2026 OR p_ano > 2200 THEN
        RAISE EXCEPTION 'ano fora do intervalo: %', p_ano;
    END IF;
    IF to_regclass(v_nome) IS NOT NULL THEN
        RETURN false;
    END IF;
    EXECUTE format('CREATE TABLE %I PARTITION OF evento_auditoria FOR VALUES FROM (%L) TO (%L)', v_nome,
                   format('%s-01-01 00:00:00-03', p_ano), format('%s-01-01 00:00:00-03', p_ano + 1));
    RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION garantir_particao_auditoria(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION garantir_particao_auditoria(integer) TO aurora_app;

INSERT INTO tarefa_agendada (nome) VALUES ('expurgar-trilha');
