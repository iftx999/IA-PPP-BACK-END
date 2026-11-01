-- S1 (ADR 0027): administração da plataforma. Quem administra a plataforma cria e configura clientes (tenants), mas
-- nunca vê dados de dentro deles: o contexto de plataforma (app.plataforma) só alcança tenant, tenant_emissor e
-- assistente_limite. Nas demais tabelas, sem tenant definido, app_tenant() continua recusando (fechado por padrão).

CREATE FUNCTION app_plataforma() RETURNS boolean
    LANGUAGE sql STABLE
AS $$ SELECT COALESCE(current_setting('app.plataforma', true), '') = 'on' $$;

GRANT EXECUTE ON FUNCTION app_plataforma() TO aurora_app;

-- Código do cliente: identifica a empresa na tela de entrada e dá nome ao realm (emissor) no Keycloak.
ALTER TABLE tenant
    ADD COLUMN codigo          text,
    ADD COLUMN limite_usuarios integer CHECK (limite_usuarios > 0),
    ADD COLUMN logo            bytea CHECK (octet_length(logo) <= 262144),
    ADD COLUMN logo_tipo       text CHECK (logo_tipo IN ('image/png', 'image/jpeg', 'image/webp')),
    ADD COLUMN atualizado_em   timestamptz NOT NULL DEFAULT now(),
    -- Primeiro administrador informado na criação (contato do cliente); permite retomar o provisionamento.
    ADD COLUMN contato_nome    text,
    ADD COLUMN contato_email   text,
    -- Fim do provisionamento (realm, raiz, perfis, convite). Nulo = incompleto: a plataforma pode tentar de novo.
    ADD COLUMN provisionado_em timestamptz,
    ADD CONSTRAINT tenant_logo_com_tipo CHECK ((logo IS NULL) = (logo_tipo IS NULL));

-- Clientes existentes: o código é o nome do realm do emissor (ex.: .../realms/aurora -> aurora); sem emissor, o id.
-- O dono está sujeito ao RLS forçado: suspenso só nesta transação (como na V7).
ALTER TABLE tenant NO FORCE ROW LEVEL SECURITY;
UPDATE tenant t
   SET codigo = COALESCE(
       (SELECT lower(substring(e.emissor FROM '/realms/([A-Za-z0-9-]+)$'))
          FROM tenant_emissor e WHERE e.tenant_id = t.id ORDER BY e.emissor LIMIT 1),
       'cliente-' || left(replace(t.id::text, '-', ''), 12));
-- Dois clientes no mesmo realm (não deveria acontecer): o segundo ganha sufixo.
UPDATE tenant t
   SET codigo = t.codigo || '-' || left(replace(t.id::text, '-', ''), 6)
 WHERE EXISTS (SELECT 1 FROM tenant o WHERE o.codigo = t.codigo AND o.id < t.id);
-- Clientes existentes foram provisionados pelo script.
UPDATE tenant SET provisionado_em = criado_em;
ALTER TABLE tenant FORCE ROW LEVEL SECURITY;

ALTER TABLE tenant
    ALTER COLUMN codigo SET NOT NULL,
    ADD CONSTRAINT tenant_codigo_formato CHECK (codigo ~ '^[a-z0-9](-?[a-z0-9])+$' AND length(codigo) BETWEEN 3 AND 40),
    ADD CONSTRAINT tenant_codigo_unico UNIQUE (codigo);

-- CASE garante a ordem: em contexto de plataforma, app_tenant() (que recusa sem tenant) nem é chamada.
DROP POLICY tenant_proprio ON tenant;
CREATE POLICY tenant_proprio ON tenant
    USING (CASE WHEN app_plataforma() THEN true ELSE id = app_tenant() END)
    WITH CHECK (CASE WHEN app_plataforma() THEN true ELSE id = app_tenant() END);

DROP POLICY limite_do_tenant ON assistente_limite;
CREATE POLICY limite_do_tenant ON assistente_limite
    USING (CASE WHEN app_plataforma() THEN true ELSE tenant_id = app_tenant() END)
    WITH CHECK (CASE WHEN app_plataforma() THEN true ELSE tenant_id = app_tenant() END);

-- Só a plataforma cria clientes, muda nome, situação, limites e logo (a política acima restringe ao contexto dela).
GRANT INSERT ON tenant TO aurora_app;
GRANT UPDATE (nome, ativo, limite_usuarios, logo, logo_tipo, atualizado_em, provisionado_em) ON tenant TO aurora_app;
GRANT INSERT ON tenant_emissor TO aurora_app;
GRANT INSERT, UPDATE, DELETE ON assistente_limite TO aurora_app;

-- As permissões de coluna valem em qualquer contexto; o gatilho garante que só a plataforma usa as de cima
-- (o cliente continua alterando apenas as próprias políticas de acesso). Vale só para a aplicação: migrações e o
-- script de provisionamento rodam como dono ou superusuário.
CREATE FUNCTION tenant_so_plataforma() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
  IF app_plataforma() OR current_user <> 'aurora_app' THEN
    RETURN NEW;
  END IF;
  IF TG_OP = 'INSERT' OR NEW.nome IS DISTINCT FROM OLD.nome OR NEW.ativo IS DISTINCT FROM OLD.ativo
     OR NEW.limite_usuarios IS DISTINCT FROM OLD.limite_usuarios OR NEW.logo IS DISTINCT FROM OLD.logo
     OR NEW.codigo IS DISTINCT FROM OLD.codigo OR NEW.provisionado_em IS DISTINCT FROM OLD.provisionado_em THEN
    RAISE EXCEPTION 'somente a administração da plataforma altera os dados do cliente'
      USING ERRCODE = 'insufficient_privilege';
  END IF;
  RETURN NEW;
END
$$;

CREATE TRIGGER tenant_so_plataforma BEFORE INSERT OR UPDATE ON tenant
    FOR EACH ROW EXECUTE FUNCTION tenant_so_plataforma();
