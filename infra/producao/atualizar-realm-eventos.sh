#!/usr/bin/env bash
# Prepara um realm já existente para a trilha de acesso (doc 08, 8.6 e 8.7; ADR 0028). O Keycloak só importa o realm
# na primeira subida; instalações anteriores a esta versão precisam rodar este script uma vez em cada realm de
# cliente (rodar de novo não muda nada). Realms criados pela administração da plataforma já nascem assim.
#
#   ./atualizar-realm-eventos.sh                 # realm "aurora"
#   KC_REALM=grupo-norte ./atualizar-realm-eventos.sh
#
# O que faz no realm:
#   1. Guarda os eventos de login, falha de login, bloqueio por tentativas e logout por 30 dias (a API copia para a
#      trilha de auditoria a cada poucos minutos; o Keycloak é só a origem).
#   2. Conta de serviço aurora-api-admin: papel view-events (ler esses eventos).
#
# Para testar contra outro Keycloak (ex.: o de desenvolvimento), defina KC_CONTEINER, KC_SERVIDOR, KC_USUARIO e
# KC_SENHA; nesse caso o .env não é lido.
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

echo "1/2 Eventos de acesso no realm $R"
"${KC[@]}" update events/config -r "$R" -f - <<'JSON'
{"eventsEnabled":true,"eventsExpiration":2592000,"eventsListeners":["jboss-logging"],
 "enabledEventTypes":["LOGIN","LOGIN_ERROR","LOGOUT","USER_DISABLED_BY_TEMPORARY_LOCKOUT",
                      "USER_DISABLED_BY_PERMANENT_LOCKOUT"]}
JSON

echo "2/2 Conta de serviço: papel view-events"
"${KC[@]}" add-roles -r "$R" --uusername service-account-aurora-api-admin --cclientid realm-management \
  --rolename view-events

echo "Pronto: realm $R grava os eventos de acesso."
