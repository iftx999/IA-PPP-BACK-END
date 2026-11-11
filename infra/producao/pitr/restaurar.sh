#!/usr/bin/env bash
# Restauração a um instante (PITR, ADR 0039). O diretório de dados atual NÃO é apagado: é renomeado para
# 18/docker.antes_<instante> no mesmo volume (para voltar atrás, pare o postgres e troque os nomes de novo).
#   ./pitr/restaurar.sh "2026-11-11 14:30:00-03"   volta àquele instante
#   ./pitr/restaurar.sh mais-recente              tudo o que foi arquivado (ex.: disco do host perdido)
# Rode na pasta infra/producao. A API e o backup ficam parados durante a restauração.
set -euo pipefail
ALVO=${1:?informe o instante (ex.: "2026-11-11 14:30:00-03") ou mais-recente}
COMPOSE=(docker compose --env-file .env)
DADOS=/var/lib/postgresql/18/docker
ANTERIOR="${DADOS}.antes_$(date +%Y%m%d%H%M)"

if [ "$ALVO" = "mais-recente" ]; then
  OPCOES=(--type=default)
else
  # Em UTC no formato que o pgBackRest aceita (qualquer formato que o date entenda vale na linha de comando).
  INSTANTE=$(date -u -d "$ALVO" '+%Y-%m-%d %H:%M:%S+00' 2>/dev/null) || { echo "instante inválido: $ALVO"; exit 1; }
  OPCOES=(--type=time "--target=${INSTANTE}" --target-action=promote)
fi

"${COMPOSE[@]}" run --rm --no-deps -T --entrypoint pgbackrest backup-pitr info </dev/null
read -r -p "Restaurar o banco da Aurora para '${ALVO}'? Digite RESTAURAR para continuar: " ok
[ "$ok" = "RESTAURAR" ] || { echo "cancelado"; exit 1; }

"${COMPOSE[@]}" stop api backup-pitr backup postgres
# Se algo falhar daqui em diante, os serviços voltam a subir (com os dados de antes, se a restauração falhou).
trap 'echo "FALHA na restauração; religando os serviços. Se ainda existir, o diretório anterior está em ${ANTERIOR}." >&2
      "${COMPOSE[@]}" up -d postgres; "${COMPOSE[@]}" up -d api backup-pitr backup' ERR
# Como root só para renomear (o diretório 18/ do volume é do root); a restauração roda como postgres. Se o
# pgBackRest falhar, o diretório anterior volta ao lugar.
"${COMPOSE[@]}" run --rm --no-deps -T --user root --entrypoint sh backup-pitr -c \
  "set -e; mv '${DADOS}' '${ANTERIOR}'; install -d -o postgres -g postgres -m 700 '${DADOS}'
   if ! gosu postgres pgbackrest restore ${OPCOES[*]@Q}; then
     rm -rf '${DADOS}'; mv '${ANTERIOR}' '${DADOS}'; exit 1
   fi"
"${COMPOSE[@]}" up -d --wait postgres
trap - ERR
"${COMPOSE[@]}" up -d api backup-pitr backup
echo "Restaurado para '${ALVO}'. Confira o app e a verificação da trilha (Administração > Auditoria)."
echo "A restauração abre uma linha do tempo nova: faça um backup completo agora:"
echo "  docker compose --env-file .env exec backup-pitr sh /pitr/agendar.sh agora full"
echo "Depois de validar, libere o espaço do diretório anterior:"
echo "  docker compose --env-file .env run --rm --no-deps --user root --entrypoint rm backup-pitr -rf '${ANTERIOR}'"
