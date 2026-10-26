-- H9/S9: o tenant pode exigir verificação em duas etapas (TOTP) de todos os usuários. Administradores são
-- obrigados pela configuração da API (aurora.seguranca.segundo-fator-administradores), não por esta coluna.
ALTER TABLE tenant ADD COLUMN segundo_fator_obrigatorio boolean NOT NULL DEFAULT false;

-- A API só altera a política (o RLS já restringe ao próprio tenant).
GRANT UPDATE (segundo_fator_obrigatorio) ON tenant TO aurora_app;
