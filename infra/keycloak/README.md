# Keycloak (desenvolvimento)

- `realm-aurora.json`: realm importado na subida (client `aurora-web` com PKCE, usuários de demonstração com IDs fixos).
- `temas/aurora/login`: tema de login da Aurora. Estende o `keycloak.v2` e muda só o layout do `template.ftl`
  (painel de marca + formulário) e o CSS (`resources/css/aurora.css`), mantendo os fluxos do Keycloak
  (recuperação e troca de senha, MFA, mensagens). Fontes servidas pelo próprio tema (sem Google Fonts).

Em `start-dev` o cache de temas fica desligado: editar o CSS e recarregar a página basta. Ao atualizar o Keycloak,
compare o `template.ftl` com o do `keycloak.v2` da nova versão.
- `realm-corporativo-exemplo.json` (só desenvolvimento): faz o papel do diretório da empresa para testar o SSO
  corporativo (ADR 0025). Clients `aurora-sso` (OIDC, segredo `sso-dev`) e a entidade SAML do realm `aurora`; usuários
  `diretoria@exemplo.dev` (convidada na Aurora) e `pessoa.externa@exemplo.dev` (sem convite), senha `corporativo-dev`.
  Na tela de Usuários: OIDC com `http://localhost:8180/realms/corporativo-exemplo/.well-known/openid-configuration`,
  ou SAML com `http://localhost:8180/realms/corporativo-exemplo/protocol/saml/descriptor`.
- `realm-plataforma.json`: administração da plataforma (ADR 0027), usuário `plataforma@exemplo.dev` (senha
  `aurora-dev`), acesso em http://localhost:4200/plataforma. O serviço `keycloak-configuracao` do compose cria a conta
  `aurora-plataforma-admin` no master (só `create-realm`) com `configurar-plataforma.sh`.
- `gerar-realm-producao.py` também gera `realm-plataforma-producao.json` e o modelo que a API usa para criar o realm
  de cada cliente (`backend/src/main/resources/keycloak/realm-modelo.json`). Rode-o depois de mudar `realm-aurora.json`.
- Clientes criados pela tela deixam realms no Keycloak de desenvolvimento (os E2E criam "Cliente E2E …" e os
  desativam); recriar o contêiner do Keycloak apaga todos.
