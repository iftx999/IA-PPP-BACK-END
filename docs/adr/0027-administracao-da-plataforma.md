# 0027 · Administração da plataforma: clientes criados pela tela, realm por cliente e contexto de plataforma

**Contexto.** S1 (P0) pede que a administração da plataforma crie um cliente (tenant) com nome, logo, limites,
situação e primeiro administrador. Até aqui isso exigia rodar `provisionar-cliente.sh` no servidor, e cada
instalação atendia na prática um cliente só: o front conhecia um único emissor e o script usava sempre o realm
`aurora`. Cada cliente tem seu realm (ADR 0003), então criar cliente é também criar realm, e o front precisa saber em
qual realm a pessoa entra.

**Decisão.**
- **Quem administra a plataforma** entra num realm próprio, `plataforma` (client `aurora-plataforma-web`, TOTP
  obrigatório no primeiro acesso em produção). A API aceita esse emissor **apenas** em `/api/v1/plataforma/**`, numa
  cadeia de segurança própria que confere de novo o emissor e exige `otp` no `amr` (`aurora.plataforma.segundo-fator`,
  desligado só no desenvolvimento). Tokens de clientes não entram ali, e o token da plataforma não serve dentro de um
  cliente (o emissor não está em `tenant_emissor`).
- **Contexto de plataforma no banco** (`app.plataforma`, V23): a política de `tenant` e de `assistente_limite` passa a
  ser `CASE WHEN app_plataforma() THEN true ELSE … = app_tenant() END` (o `CASE` garante que `app_tenant()`, que recusa
  sem tenant, nem é chamada). Nenhuma outra tabela muda: no contexto de plataforma, usuários, contratos, imóveis e a
  trilha continuam inacessíveis. Um gatilho impede que a aplicação, no contexto de um cliente, altere nome, situação,
  limites, logo ou código do próprio cadastro (as políticas de acesso do cliente, como MFA e SSO, continuam dele).
  Sem papel com `BYPASSRLS`.
- **Criar cliente** reserva o código (3–40 minúsculas, números e hífens; nomes do Keycloak e da Aurora reservados;
  recusa realm já existente de outro dono), grava cadastro, limites e o emissor `<url-identidade>/realms/<código>`
  **antes** do realm existir (assim uma nova tentativa reconhece o realm como deste cliente), cria o realm a partir de
  `keycloak/realm-modelo.json` (gerado do realm de produção por `gerar-realm-producao.py`; a API preenche segredo,
  SMTP e endereço do front com a própria configuração) e provisiona raiz, perfis do sistema e o convite do primeiro
  administrador pelo **mesmo fluxo da tela de Usuários** (`ProvisionamentoDeCliente`, módulo de acesso). Cada etapa é
  idempotente; se alguma falha (ex.: Keycloak fora do ar), o cliente fica com "provisionamento pendente" e a tela
  oferece concluir. `provisionado_em` marca o fim.
- **Menor privilégio no Keycloak:** a API cria realms com a conta `aurora-plataforma-admin` do master, que só tem
  `create-realm` (o Keycloak dá a quem cria um realm a administração só daquele realm). Validado: ela recebe 403 no
  realm `aurora`, criado antes. Dentro de cada realm novo, a conta de serviço `aurora-api-admin` continua cuidando de
  usuários e SSO. Configuração idempotente em `infra/keycloak/configurar-plataforma.sh`.
- **Limites:** usuários ativos (convites pendentes contam), conferido ao convidar e ao reativar (422 com orientação);
  orçamento mensal do assistente em `assistente_limite` (já usado pelo ADR 0018). O limite não pode ficar abaixo dos
  usuários já ativos.
- **Logo:** PNG, JPEG ou WebP até 256 KB, conferido pelo conteúdo (SVG recusado). Servida sem login em
  `/api/v1/publico/clientes/{código}/logo` com `nosniff`, só de cliente ativo; aparece no menu do cliente.
- **Escolha do cliente no front:** com `escolherCliente` ligado (`AURORA_VARIOS_CLIENTES`), quem não tem empresa
  lembrada no navegador passa pela tela "Código da empresa" (`/acesso`), que consulta `GET /api/v1/publico/clientes/
  {código}` (nome, emissor e se tem logo; 404 igual para inexistente, inativo ou incompleto) e lembra a escolha. A
  biblioteca OIDC recebe o emissor certo antes do Angular iniciar; `/plataforma` usa o realm da plataforma e volta
  por `/plataforma/entrar`. `?cliente=<código>` escolhe direto, e o link do convite por e-mail já volta com ele.
  "Trocar de empresa" esquece a escolha e encerra a sessão. Desligado, tudo funciona como antes (um realm).
- **Trilha:** `CLIENTE_CRIADO` e `CLIENTE_ALTERADO` na trilha **do cliente**, com o `sub` do operador (sem nome ou
  e-mail) e só o que mudou.

**Alternativas descartadas.** Um realm para todos com Organizations do Keycloak: mudaria a identificação do tenant
(ADR 0003) e o isolamento de SSO e políticas por cliente. Papel de banco com `BYPASSRLS` para a plataforma: um erro
na aplicação exporia todas as tabelas. Subdomínio por cliente: exigiria DNS e certificado por cliente e configuração
do front por host.

**Consequências.** O código do cliente vira parte do endereço de login e não muda depois. Desativar um cliente
bloqueia o uso da Aurora (403), mas não desliga o realm nem apaga dados. Instalações existentes: os clientes ganharam
como código o nome do realm (`aurora`); para usar a tela de clientes é preciso importar o realm `plataforma`, rodar
`configurar-plataforma.sh`, criar o primeiro operador (`criar-operador-plataforma.sh`) e ligar
`AURORA_VARIOS_CLIENTES`. No desenvolvimento, o Keycloak precisa enxergar o SMTP pelo nome da rede do Docker
(`aurora.plataforma.smtp-host`).
