#!/usr/bin/env bash
# Cria no Keycloak LOCAL o client "aurora-carga" (password grant, audiência aurora-api) usado pelos testes de carga
# do k6 para obter token sem navegador. Só para desenvolvimento/homologação: recusa qualquer servidor que não seja
# localhost. Rodar de novo não muda nada.
#
#   ./preparar-cliente-de-carga.sh
set -euo pipefail
KC_SERVIDOR=${KC_SERVIDOR:-http://localhost:8180}
case "$KC_SERVIDOR" in
  http://localhost:*|http://127.0.0.1:*) ;;
  *) echo "Recusado: o client de carga só pode existir no Keycloak local ($KC_SERVIDOR)." >&2; exit 1 ;;
esac
KC=(docker exec -i "${KC_CONTEINER:-aurora-dev-keycloak-1}" /opt/keycloak/bin/kcadm.sh)
"${KC[@]}" config credentials --server http://localhost:8180 --realm master --user "${KC_USUARIO:-admin}" \
  --password "${KC_SENHA:-admin-dev}" >/dev/null
R=${KC_REALM:-aurora}
if [ -z "$("${KC[@]}" get clients -r "$R" -q clientId=aurora-carga --fields id --format csv --noquotes)" ]; then
  "${KC[@]}" create clients -r "$R" -f - >/dev/null <<'JSON'
{"clientId":"aurora-carga","name":"Testes de carga (somente local)","publicClient":true,
 "standardFlowEnabled":false,"directAccessGrantsEnabled":true,
 "protocolMappers":[{"name":"aud-aurora-api","protocol":"openid-connect","protocolMapper":"oidc-audience-mapper",
   "config":{"included.custom.audience":"aurora-api","access.token.claim":"true","id.token.claim":"false"}}]}
JSON
fi
echo "Client aurora-carga pronto no realm $R."
