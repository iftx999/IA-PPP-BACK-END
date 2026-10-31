-- S10 (ADR 0025): a configuração do SSO corporativo (endereço, client, segredo) vive no realm do tenant, no Keycloak.
-- Aqui fica só a decisão do tenant de aceitar a verificação em duas etapas feita pelo IdP corporativo (ex.: acesso
-- condicional do Entra ID) no lugar do código TOTP da Aurora.
ALTER TABLE tenant ADD COLUMN sso_confia_segundo_fator boolean NOT NULL DEFAULT false;

GRANT UPDATE (sso_confia_segundo_fator) ON tenant TO aurora_app;
