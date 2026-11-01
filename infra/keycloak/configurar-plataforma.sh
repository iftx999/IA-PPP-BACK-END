#!/usr/bin/env bash
# Conta de serviço da API no realm master para a administração da plataforma (S1, ADR 0027): o único papel é
# create-realm. Quem cria um realm vira administrador só dele; os realms já existentes e o master ficam fora.
# Idempotente. Roda dentro do contêiner do Keycloak (tem o kcadm):
#
#   desenvolvimento: serviço keycloak-configuracao do infra/compose.yaml (automático)
#   produção:        docker compose --env-file .env exec -T -e KC_SERVIDOR=http://localhost:8080 \
#                      -e KC_USUARIO="$KEYCLOAK_ADMIN_USUARIO" -e KC_SENHA="$KEYCLOAK_ADMIN_SENHA" \
#                      -e AURORA_PLATAFORMA_SEGREDO="$AURORA_PLATAFORMA_SEGREDO" keycloak bash \
#                      < ../keycloak/configurar-plataforma.sh
set -euo pipefail
: "${KC_SERVIDOR:?}" "${KC_USUARIO:?}" "${KC_SENHA:?}" "${AURORA_PLATAFORMA_SEGREDO:?}"
KCADM=/opt/keycloak/bin/kcadm.sh
export HOME=/tmp

for tentativa in $(seq 1 60); do
  if "$KCADM" config credentials --server "$KC_SERVIDOR" --realm master --user "$KC_USUARIO" --password "$KC_SENHA" \
       >/dev/null 2>&1; then
    break
  fi
  [ "$tentativa" = 60 ] && { echo "Keycloak não respondeu em $KC_SERVIDOR" >&2; exit 1; }
  sleep 3
done

ID=$("$KCADM" get clients -r master -q clientId=aurora-plataforma-admin --fields id --format csv --noquotes | head -1)
if [ -z "$ID" ]; then
  "$KCADM" create clients -r master -s clientId=aurora-plataforma-admin \
    -s "name=Aurora API (criação de realms de clientes)" -s publicClient=false -s serviceAccountsEnabled=true \
    -s standardFlowEnabled=false -s directAccessGrantsEnabled=false -s "secret=$AURORA_PLATAFORMA_SEGREDO" >/dev/null
else
  "$KCADM" update "clients/$ID" -r master -s "secret=$AURORA_PLATAFORMA_SEGREDO"
fi
"$KCADM" add-roles -r master --uusername service-account-aurora-plataforma-admin --rolename create-realm
echo "Conta de serviço aurora-plataforma-admin pronta (papel create-realm no master)."
