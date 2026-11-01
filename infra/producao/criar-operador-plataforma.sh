#!/usr/bin/env bash
# Cria (ou reenvia o convite de) uma pessoa da administração da plataforma (S1, ADR 0027) no realm "plataforma".
# Ela recebe por e-mail o link para criar a senha e, no primeiro acesso, cadastra o aplicativo autenticador (TOTP,
# obrigatório nesse realm). Quem administra a plataforma cria e configura clientes, mas não vê dados de dentro deles.
#
#   ./criar-operador-plataforma.sh operacao@empresa.com.br "Nome Sobrenome"
#
# Roda no host de produção, na pasta infra/producao, com o .env preenchido e os serviços no ar. Idempotente.
set -euo pipefail

EMAIL=${1:?informe o e-mail}
NOME=${2:?informe o nome}

set -a; source .env; set +a
KC=(docker compose --env-file .env exec -T keycloak /opt/keycloak/bin/kcadm.sh)
"${KC[@]}" config credentials --server http://localhost:8080 --realm master \
  --user "$KEYCLOAK_ADMIN_USUARIO" --password "$KEYCLOAK_ADMIN_SENHA" >/dev/null

ID=$("${KC[@]}" get users -r plataforma -q "email=${EMAIL}" -q exact=true --fields id --format csv --noquotes | head -1)
PRIMEIRO_NOME=${NOME%% *}
SOBRENOME=${NOME#* }
if [ "$SOBRENOME" = "$NOME" ]; then SOBRENOME="-"; fi
if [ -z "$ID" ]; then
  "${KC[@]}" create users -r plataforma -s "username=${EMAIL}" -s "email=${EMAIL}" -s "firstName=${PRIMEIRO_NOME}" \
    -s "lastName=${SOBRENOME}" -s enabled=true -s emailVerified=false >/dev/null
  ID=$("${KC[@]}" get users -r plataforma -q "email=${EMAIL}" -q exact=true --fields id --format csv --noquotes | head -1)
fi
REDIRECT=$(printf 'https://%s/plataforma' "$AURORA_DOMINIO" | sed 's/:/%3A/g; s/\//%2F/g')
"${KC[@]}" update \
  "users/${ID}/execute-actions-email?client_id=aurora-plataforma-web&lifespan=259200&redirect_uri=${REDIRECT}" \
  -r plataforma -n -b '["VERIFY_EMAIL","UPDATE_PASSWORD","CONFIGURE_TOTP"]'
echo "Convite enviado para ${EMAIL} (vale 72 h). Acesso em https://${AURORA_DOMINIO}/plataforma"
