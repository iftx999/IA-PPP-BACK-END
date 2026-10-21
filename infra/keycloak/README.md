# Keycloak (desenvolvimento)

- `realm-aurora.json`: realm importado na subida (client `aurora-web` com PKCE, usuários de demonstração com IDs fixos).
- `temas/aurora/login`: tema de login da Aurora. Estende o `keycloak.v2` e muda só o layout do `template.ftl`
  (painel de marca + formulário) e o CSS (`resources/css/aurora.css`), mantendo os fluxos do Keycloak
  (recuperação e troca de senha, MFA, mensagens). Fontes servidas pelo próprio tema (sem Google Fonts).

Em `start-dev` o cache de temas fica desligado: editar o CSS e recarregar a página basta. Ao atualizar o Keycloak,
compare o `template.ftl` com o do `keycloak.v2` da nova versão.
