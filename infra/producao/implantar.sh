#!/usr/bin/env bash
# Implantação de uma versão (ADR 0040), no host do ambiente (homologação ou produção). Chamado pelo workflow
# "Implantar" via SSH ou à mão:
#   ./implantar.sh <commit|manter> <versao-api|manter> <versao-web|manter>
#     commit       commit do repositório do back-end cujo infra/ será usado (compose, scripts); "manter" não mexe
#     versao-api   tag das imagens aurora-api e aurora-postgres (ex.: sha-1a2b3c4)
#     versao-web   tag da imagem aurora-web (publicada pelo repositório do front, com o SHA de lá)
#
# Pela chave de implantação, o authorized_keys do host deve forçar este script, sem shell:
#   command="/opt/aurora/infra/producao/implantar.sh",restrict ssh-ed25519 AAAA... implantacao-github
# Os argumentos chegam então em SSH_ORIGINAL_COMMAND e são validados aqui (nada vira comando de shell).
#
# Passos: backup na hora → troca de versão no .env → pull → up --wait (healthchecks) → conferência pela borda.
# Se a subida ou a conferência falhar, volta as versões anteriores no .env e sobe de novo.
set -euo pipefail

# O checkout do commit novo reescreve este arquivo; o bash lê o script aos poucos, então roda de uma cópia.
if [ -z "${IMPLANTAR_PASTA:-}" ]; then
  IMPLANTAR_PASTA=$(dirname "$(readlink -f "$0")")
  copia=$(mktemp "${TMPDIR:-/tmp}/implantar.XXXXXX")
  cp "$0" "$copia"
  IMPLANTAR_PASTA=$IMPLANTAR_PASTA exec bash "$copia" "$@"
fi
trap 'rm -f "$0"' EXIT
cd "$IMPLANTAR_PASTA"
REPOSITORIO=$(git rev-parse --show-toplevel)
COMPOSE=(docker compose --env-file .env)
REGISTRO_LOG=implantacoes.log

if [ -n "${SSH_ORIGINAL_COMMAND:-}" ]; then
  # Separação por espaço sem expansão de curingas; cada parte é validada abaixo.
  read -r -a ARGS <<< "$SSH_ORIGINAL_COMMAND"
else
  ARGS=("$@")
fi
[ "${#ARGS[@]}" -eq 3 ] || { echo "uso: implantar.sh <commit|manter> <versao-api|manter> <versao-web|manter>" >&2; exit 2; }
COMMIT=${ARGS[0]}; VERSAO_API=${ARGS[1]}; VERSAO_WEB=${ARGS[2]}

valido() { [[ "$1" == manter || "$1" =~ ^$2$ ]] || { echo "argumento inválido: $1" >&2; exit 2; }; }
valido "$COMMIT" '[0-9a-f]{7,40}'
valido "$VERSAO_API" 'sha-[0-9a-f]{7,40}'
valido "$VERSAO_WEB" 'sha-[0-9a-f]{7,40}'
[ "$COMMIT$VERSAO_API$VERSAO_WEB" != mantermantermanter ] || { echo "nada a implantar" >&2; exit 2; }

valor() { sed -n "s/^$1=//p" .env | tail -1; }
definir() {
  if grep -q "^$1=" .env; then sed -i "s|^$1=.*|$1=$2|" .env; else echo "$1=$2" >> .env; fi
}
registrar() { echo "$(date -Iseconds) $*" | tee -a "$REGISTRO_LOG"; }

# Uma implantação por vez (o workflow também serializa, mas alguém pode rodar à mão).
exec 9>.implantar.trava
flock -n 9 || { echo "outra implantação em andamento" >&2; exit 3; }

ANTES_COMMIT=$(git -C "$REPOSITORIO" rev-parse HEAD)
ANTES_API=$(valor AURORA_VERSAO)
ANTES_WEB=$(valor AURORA_VERSAO_WEB)
registrar "início: commit=${COMMIT} api=${VERSAO_API} web=${VERSAO_WEB} (antes: ${ANTES_COMMIT:0:12} ${ANTES_API} ${ANTES_WEB})"

subir_e_conferir() {
  "${COMPOSE[@]}" pull --quiet api web postgres
  "${COMPOSE[@]}" up -d --wait --wait-timeout 300
  # Pela borda, como o usuário: front servido e API respondendo através dele.
  local dominio
  dominio=$(valor AURORA_DOMINIO)
  for tentativa in 1 2 3 4 5 6; do
    # /api/v1/eu sem token: 401 vem da API (com ela fora, o nginx responde 502).
    if curl -fsS --max-time 10 -o /dev/null "https://${dominio}/saude" \
        && [ "$(curl -s --max-time 10 -o /dev/null -w '%{http_code}' "https://${dominio}/api/v1/eu")" = 401 ]; then
      return 0
    fi
    sleep $((tentativa * 5))
  done
  echo "conferência pela borda falhou (https://${dominio})" >&2
  return 1
}

if [ "$COMMIT" != manter ]; then
  git -C "$REPOSITORIO" fetch --quiet origin
  # Commit sem este script deixaria a chave de implantação apontando para um arquivo que não existe.
  git -C "$REPOSITORIO" cat-file -e "${COMMIT}:infra/producao/implantar.sh" 2>/dev/null \
    || { registrar "recusado: o commit ${COMMIT} não tem infra/producao/implantar.sh"; exit 2; }
fi

# Backup na hora, antes das migrações da versão nova: o contínuo (se existir) e o lógico.
if "${COMPOSE[@]}" ps --services --status running | grep -qx backup-pitr; then
  "${COMPOSE[@]}" exec -T backup-pitr sh /pitr/agendar.sh agora diff >/dev/null
fi
"${COMPOSE[@]}" exec -T backup sh /backup.sh agora

if [ "$COMMIT" != manter ]; then
  git -C "$REPOSITORIO" checkout --quiet --detach "$COMMIT"
fi
[ "$VERSAO_API" != manter ] && definir AURORA_VERSAO "$VERSAO_API"
[ "$VERSAO_WEB" != manter ] && definir AURORA_VERSAO_WEB "$VERSAO_WEB"

if subir_e_conferir; then
  registrar "ok: commit=$(git -C "$REPOSITORIO" rev-parse --short HEAD) api=$(valor AURORA_VERSAO) web=$(valor AURORA_VERSAO_WEB)"
  exit 0
fi

registrar "FALHA: voltando para commit=${ANTES_COMMIT:0:12} api=${ANTES_API} web=${ANTES_WEB}"
"${COMPOSE[@]}" logs --tail 80 api web >&2 || true
git -C "$REPOSITORIO" checkout --quiet --detach "$ANTES_COMMIT"
definir AURORA_VERSAO "$ANTES_API"
definir AURORA_VERSAO_WEB "$ANTES_WEB"
if subir_e_conferir; then
  registrar "versão anterior de volta no ar. Se a nova aplicou migração incompatível, restaure o backup (docs/operacao.md, seção 4)."
else
  registrar "FALHA também na versão anterior: intervenção manual (docs/operacao.md, seções 3 e 4)."
fi
exit 1
