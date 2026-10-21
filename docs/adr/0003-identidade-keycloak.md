# 0003 · Identidade no Keycloak, um emissor por tenant

**Contexto.** São necessários login seguro, MFA, convites, recuperação de senha e SSO corporativo por cliente
(doc 08, 8.6). Autenticação caseira é descartada (doc 09, 9.10). O sistema anterior usava Keycloak.

**Decisão.** Keycloak como provedor OIDC. A API é um resource server JWT e aceita apenas emissores cadastrados
em `tenant_emissor`, o que mapeia o emissor para o tenant. O token precisa ter a audiência `aurora-api`.
Permissões e escopo **não** vêm do token: são lidos do banco a cada requisição, então uma mudança de perfil vale
na requisição seguinte. No primeiro login, o usuário convidado é vinculado pelo e-mail verificado. O front usa
código + PKCE com refresh token.

**Consequências.** SSO por cliente é feito no realm do tenant, sem mudança na API. O resolvedor faz consultas
leves por requisição; se necessário, entra um cache curto invalidado por evento.
