# 0011 · Gestão de usuários pela Admin API do provedor de identidade

**Contexto.** A administração precisa convidar pessoas, bloquear o login de quem for desativado, encerrar sessões
e permitir redefinir senha (H8, RN-16, RN-17). A Aurora não guarda senhas (ADR 0003).

**Decisão.**
- A API usa a **Keycloak Admin REST API** do realm do tenant (o emissor em `tenant_emissor`), autenticada por
  *client credentials* no client `aurora-api-admin` (conta de serviço só com `manage-users`, `view-users` e
  `query-users` de `realm-management`). O segredo vem de `AURORA_IDENTIDADE_SEGREDO` (cofre em produção). A
  porta `ProvedorIdentidade` isola o Keycloak do restante do módulo.
- **Convite:** cria a conta no realm (ou reaproveita a que já existe com o e-mail), grava o `usuario` já com o
  `sub` e as concessões, e dispara o e-mail de ações do Keycloak (`VERIFY_EMAIL` + `UPDATE_PASSWORD`). O link
  é de uso único e expira em 72 h (`aurora.identidade.validade-convite`). Enquanto não há primeiro acesso, o
  convite está pendente e pode ser reenviado.
- **Desativar:** `usuario.ativo = false` (vale na requisição seguinte, pois o contexto é lido do banco), conta
  desabilitada no realm e sessões encerradas. Não é possível desativar a si mesmo nem o último administrador.
- As chamadas ao provedor acontecem **dentro** da transação: se o provedor falhar, nada é gravado e a API
  responde 503; a conta eventualmente criada é reaproveitada na nova tentativa.
- Gestão de usuários, como a de perfis, exige escopo no tenant inteiro. A delegação por unidade fica para
  quando as permissões passarem a valer por unidade.

**Consequências.** Cada realm de tenant precisa do client `aurora-api-admin` (criado no onboarding do tenant).
Contas criadas no provedor sem linha na Aurora não dão acesso a nada. A lista de usuários é devolvida inteira
(adequado a alguns milhares por tenant); acima disso, paginar no servidor.
