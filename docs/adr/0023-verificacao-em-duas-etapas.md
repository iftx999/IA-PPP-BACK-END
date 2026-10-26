# 0023 · Verificação em duas etapas: TOTP no Keycloak, política conferida pela API

**Contexto.** RN-17 e S9 pedem MFA obrigatório para administradores e configurável pelo tenant. O login acontece no
Keycloak (ADR 0003), mas quem sabe quem é administrador (concessões perfil × unidade) e qual é a política do
tenant é a Aurora. Os papéis do Keycloak não refletem as concessões.

**Decisão.**
- **Fator:** TOTP (aplicativo autenticador), nativo do Keycloak. No fluxo de login padrão (`Browser - Conditional 2FA`)
  o código é pedido a toda pessoa que já cadastrou um aplicativo. Quem quiser pode cadastrar por conta própria.
- **Prova no token:** o realm usa o fluxo `aurora-browser` (cópia do `browser` em que senha e OTP declaram a
  referência `pwd`/`otp`) e o client `aurora-web` ganha o mapeador `amr`. Login com código sai com
  `amr: ["pwd","otp"]`.
- **Política:** exigido de quem tem `usuario:gerenciar` (configuração da plataforma
  `aurora.seguranca.segundo-fator-administradores`, padrão ligado, desligado só no perfil `dev`) e de todos quando
  o tenant liga `tenant.segundo_fator_obrigatorio` (tela Usuários, auditado). Vale na requisição seguinte.
- **Conferência:** ao resolver o contexto de acesso, se a política se aplica e o `amr` não tem `otp`, a API responde
  403 com `codigo: SEGUNDO_FATOR_NECESSARIO` e, se a pessoa não tem TOTP, marca no Keycloak a ação obrigatória
  `CONFIGURE_TOTP`. O front mostra o aviso e faz novo login com `prompt=login`.
- **Login do cadastro:** o Keycloak não inclui `otp` no `amr` do login em que o TOTP é cadastrado, embora a pessoa
  tenha digitado um código válido para concluir o cadastro. A API aceita esse token quando o TOTP foi criado depois
  do `auth_time` e até 10 min depois. A conferência no provedor fica guardada por login (`sub` + `auth_time`):
  15 min quando aceita, 30 s quando recusada. Provedor fora do ar → recusa (falha fechada).
- **Celular perdido:** o administrador remove o TOTP de outra pessoa (não o próprio) na tela Usuários. Se a política
  exigir, o cadastro de um novo é pedido no próximo login. Evento `SEGUNDO_FATOR_REMOVIDO`.

**Alternativas descartadas.** Subfluxo condicionado a um papel `mfa-obrigatoria` sincronizado pela API: pede o código
duas vezes a quem já tem TOTP (os dois subfluxos rodam) e duplica no Keycloak a regra que vive nas concessões.
Exigir `acr`/LoA por client: a obrigatoriedade depende da pessoa, não do client.

**Consequências.** A primeira vez que a política alcança alguém, a pessoa entra uma vez a mais. Quem é obrigado e
ainda não cadastrou pode cadastrar o TOTP com a senha apenas, o que é inerente a todo cadastro no primeiro login.
O tenant novo precisa do realm com o fluxo `aurora-browser` e o mapeador `amr` (já no `realm-aurora-producao.json`).
SSO corporativo (S10) fica para depois; com SSO, o `amr` virá do IdP externo e a regra precisará aceitar os métodos
dele.
