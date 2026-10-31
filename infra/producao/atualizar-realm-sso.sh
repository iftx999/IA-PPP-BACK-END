#!/usr/bin/env bash
# Prepara um realm já existente para o SSO corporativo (S10, ADR 0025). O Keycloak só importa o realm na primeira
# subida; instalações anteriores a esta versão precisam rodar este script uma vez (rodar de novo não muda nada).
#
#   ./atualizar-realm-sso.sh            # no host de produção, na pasta infra/producao, com o .env e os serviços no ar
#
# O que faz no realm:
#   1. Conta de serviço aurora-api-admin: papéis manage-identity-providers e view-identity-providers.
#   2. Client aurora-web: mapeador "idp" (IdP usado no login vai no token, para a política de segundo fator).
#   3. Fluxo aurora-sso-primeiro-login: só vincula contas já convidadas (sem criar contas novas).
#   4. Fluxo aurora-sso-pos-login: código TOTP depois do SSO para quem cadastrou, com referência "otp" no amr.
#
# Para testar contra outro Keycloak (ex.: o de desenvolvimento), defina KC_CONTEINER, KC_SERVIDOR, KC_USUARIO,
# KC_SENHA e KC_REALM; nesse caso o .env não é lido.
set -euo pipefail

if [ -n "${KC_CONTEINER:-}" ]; then
  KC=(docker exec -i "$KC_CONTEINER" /opt/keycloak/bin/kcadm.sh)
else
  set -a; source .env; set +a
  KC=(docker compose --env-file .env exec -T keycloak /opt/keycloak/bin/kcadm.sh)
  KC_SERVIDOR=http://localhost:8080
  KC_USUARIO=$KEYCLOAK_ADMIN_USUARIO
  KC_SENHA=$KEYCLOAK_ADMIN_SENHA
fi
R=${KC_REALM:-aurora}
"${KC[@]}" config credentials --server "$KC_SERVIDOR" --realm master --user "$KC_USUARIO" --password "$KC_SENHA" \
  >/dev/null

kc() { "${KC[@]}" "$@" -r "$R"; }
# Id da execução de um fluxo pelo provider (ou pelo nome, no caso de subfluxo).
execucao() {
  kc get "authentication/flows/$1/executions" --fields id,providerId,displayName --format csv --noquotes \
    | awk -F, -v alvo="$2" '$2 == alvo || $3 == alvo { print $1; exit }'
}
exigir() { # fluxo (codificado) id requisito
  kc update "authentication/flows/$1/executions" -n -b "{\"id\":\"$2\",\"requirement\":\"$3\"}"
}
adicionar() { # fluxo (codificado) provider requisito
  if [ -z "$(execucao "$1" "$2")" ]; then
    kc create "authentication/flows/$1/executions/execution" -s "provider=$2" >/dev/null
  fi
  exigir "$1" "$(execucao "$1" "$2")" "$3"
}
fluxo_existe() {
  kc get authentication/flows --fields alias --format csv --noquotes | grep -qx "$1"
}

echo "1/4 Conta de serviço: papéis de gestão de IdP"
kc add-roles --uusername service-account-aurora-api-admin --cclientid realm-management \
  --rolename manage-identity-providers --rolename view-identity-providers

echo "2/4 Client aurora-web: mapeador idp"
WEB=$(kc get clients -q clientId=aurora-web --fields id --format csv --noquotes | head -1)
if ! kc get "clients/$WEB/protocol-mappers/models" --fields name --format csv --noquotes | grep -qx idp; then
  kc create "clients/$WEB/protocol-mappers/models" -f - >/dev/null <<'JSON'
{"name":"idp","protocol":"openid-connect","protocolMapper":"oidc-usersessionmodel-note-mapper",
 "config":{"user.session.note":"identity_provider","claim.name":"idp","jsonType.label":"String",
           "access.token.claim":"true","id.token.claim":"false","introspection.token.claim":"true"}}
JSON
fi

echo "3/4 Fluxo aurora-sso-primeiro-login"
P=aurora-sso-primeiro-login
if ! fluxo_existe "$P"; then
  kc create authentication/flows -s alias=$P -s providerId=basic-flow -s topLevel=true -s builtIn=false \
    -s "description=Primeiro login pelo SSO corporativo: só vincula contas já convidadas (ADR 0025)" >/dev/null
fi
adicionar $P idp-detect-existing-broker-user REQUIRED
adicionar $P idp-auto-link REQUIRED

echo "4/4 Fluxo aurora-sso-pos-login"
F=aurora-sso-pos-login
SUB="aurora-sso-pos-login segundo fator"
SUB_URL="aurora-sso-pos-login%20segundo%20fator"
if ! fluxo_existe "$F"; then
  kc create authentication/flows -s alias=$F -s providerId=basic-flow -s topLevel=true -s builtIn=false \
    -s "description=Depois do SSO corporativo: código TOTP para quem cadastrou (ADR 0023, ADR 0025)" >/dev/null
fi
if [ -z "$(execucao $F "$SUB")" ]; then
  kc create "authentication/flows/$F/executions/flow" -s "alias=$SUB" -s type=basic-flow \
    -s provider=registration-page-form -s "description=TOTP se a pessoa já cadastrou" >/dev/null
fi
exigir $F "$(execucao $F "$SUB")" CONDITIONAL
adicionar "$SUB_URL" conditional-user-configured REQUIRED
adicionar "$SUB_URL" auth-otp-form REQUIRED
OTP=$(execucao "$SUB_URL" auth-otp-form)
if ! kc get authentication/flows/$SUB_URL/executions --fields providerId,authenticationConfig --format csv \
     --noquotes | grep -q '^auth-otp-form,.\+'; then
  kc create "authentication/executions/$OTP/config" -s alias=aurora-amr-otp-sso \
    -s 'config."default.reference.value"=otp' -s 'config."default.reference.maxAge"=36000' >/dev/null
fi
# Sem um passo que sempre conclui, o fluxo falha quando o subfluxo condicional não roda (pessoa sem TOTP).
adicionar $F allow-access-authenticator REQUIRED

echo "Pronto. O administrador do cliente já pode configurar o SSO em Administração > Usuários."
