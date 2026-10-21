-- Contexto de acesso da transação, definido pela API a partir do token (nunca do corpo da requisição).
--   app.tenant_id  -> tenant do usuário autenticado
--   app.escopos    -> caminhos ltree das unidades do escopo, separados por vírgula
--   app.usuario_id -> usuário autenticado
-- As extensões (postgis, ltree, vector, pg_trgm, btree_gist) são criadas pelo superusuário
-- na inicialização do banco (infra/postgres/init), pois o dono do schema não é superusuário.

CREATE FUNCTION app_tenant() RETURNS uuid
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

CREATE FUNCTION app_escopos() RETURNS ltree[]
    LANGUAGE sql STABLE
AS $$
    SELECT COALESCE(
      string_to_array(NULLIF(current_setting('app.escopos', true), ''), ',')::ltree[],
      '{}'::ltree[])
$$;

CREATE FUNCTION app_usuario() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('app.usuario_id', true), '')::uuid $$;

GRANT USAGE ON SCHEMA public TO aurora_app;
GRANT EXECUTE ON FUNCTION app_tenant(), app_escopos(), app_usuario() TO aurora_app;
