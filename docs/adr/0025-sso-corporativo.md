# 0025 · SSO corporativo: IdP da empresa no realm do tenant, só para pessoas convidadas

**Contexto.** S10 e o doc 08 (8.6) pedem SSO por OIDC/SAML (ex.: Entra ID) por tenant. O ADR 0003 já previa que o SSO
seria feito no realm do tenant, sem mudar a validação de tokens da API. Faltava definir quem configura, quem pode
entrar por ele e como fica a verificação em duas etapas (ADR 0023).

**Decisão.**
- **Um IdP por tenant**, apelido fixo `corporativo`, criado pela API no realm do tenant pela Admin API do Keycloak
  (porta `ProvedorIdentidade`). O administrador informa o protocolo, o nome do botão, o endereço de descoberta OIDC
  ou dos metadados SAML e, no OIDC, o client ID e o segredo. O Keycloak lê o endereço (`import-config`) e guarda
  endpoints, chaves e certificado; a assinatura é sempre conferida. Endereço que não traz uma configuração válida →
  422 com orientação.
- **Segredo só no Keycloak.** Nunca é gravado no banco, na trilha ou devolvido pela API; editar sem informar mantém o
  atual (valor mascarado do Keycloak). Trocar de protocolo recria o IdP e exige o segredo de novo.
- **Endereço:** fora do desenvolvimento, só HTTPS e host público (sem loopback, rede privada, link-local, CGNAT,
  ULA), para o Keycloak não ser usado para sondar a rede interna (`aurora.seguranca.sso-permitir-enderecos-internos`).
- **Só quem foi convidado entra.** O fluxo de primeiro login `aurora-sso-primeiro-login` tem apenas "detectar conta
  existente" + "vincular automaticamente": a conta convidada com o mesmo e-mail é vinculada; e-mail sem convite é
  recusado com orientação e nenhuma conta é criada. Escopo e permissões continuam vindo das concessões (ADR 0013).
  O login com senha continua disponível.
- **Segundo fator após o SSO.** O fluxo pós-login `aurora-sso-pos-login` repete a regra do `aurora-browser`: quem
  cadastrou o TOTP digita o código e o token sai com `amr: ["otp"]`; a política e o cadastro obrigatório da API
  (ADR 0023) valem sem mudança. Um passo "permitir acesso" no fim é necessário: no Keycloak 26 um fluxo cujo único
  subfluxo condicional não roda é tratado como falha.
- **Confiar no MFA do IdP** (opcional, por tenant, `tenant.sso_confia_segundo_fator`, V21): o mapeador `idp` do
  client `aurora-web` põe no token o IdP usado no login; com a opção ligada, token com `idp = corporativo` cumpre a
  política sem o TOTP da Aurora. Para quando o IdP já exige MFA (ex.: acesso condicional do Entra ID). Remover o SSO
  desliga a opção.
- **Auditoria:** `SSO_CONFIGURADO` (protocolo, habilitado, confiança, novo, se o segredo mudou; sem endereços nem
  segredo) e `SSO_REMOVIDO`.
- **Papéis:** a conta de serviço `aurora-api-admin` ganha `manage-identity-providers` e `view-identity-providers`.

**Alternativas descartadas.** Organizations do Keycloak 26 (domínio → IdP): resolveria vários IdPs por realm, mas
o realm já é do tenant e a funcionalidade muda a tela de login e o modelo de membros; fica para quando houver mais
de um IdP por cliente. Criar contas automaticamente no primeiro SSO: daria conta a qualquer pessoa do diretório,
sem convite nem escopo. Guardar a configuração no banco: duplicaria a fonte da verdade e exigiria guardar o segredo.

**Consequências.** O tenant deve confiar no IdP que configura: ele afirma o e-mail usado no vínculo. Como o realm é
de um único tenant, o efeito fica restrito a ele. Realms criados antes desta versão precisam de
`infra/producao/atualizar-realm-sso.sh` (idempotente); os novos já vêm do `realm-aurora-producao.json`. O gerador do
realm de produção passou a manter a conta de serviço da API com os papéis: antes ela era removida junto com os
usuários de demonstração, e convites pela API falhariam em produção. No desenvolvimento, o realm
`corporativo-exemplo` faz o papel do diretório da empresa (OIDC e SAML) e é usado no E2E.
