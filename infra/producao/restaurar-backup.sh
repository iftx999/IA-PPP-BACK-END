#!/usr/bin/env bash
# Restaura o banco da Aurora a partir de um backup. O banco atual NÃO é apagado: vira aurora_antes_<instante> (volta
# atrás com um RENAME). A API fica parada durante a restauração.
#   ./restaurar-backup.sh backups/aurora-2026-10-23T0200.dump
set -euo pipefail
ARQUIVO=${1:?informe o arquivo .dump}
[ -f "$ARQUIVO" ] || { echo "arquivo não encontrado: $ARQUIVO"; exit 1; }
COMPOSE=(docker compose --env-file .env)
PSQL=("${COMPOSE[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres)
ANTERIOR="aurora_antes_$(date +%Y%m%d%H%M)"
read -r -p "Restaurar ${ARQUIVO}? O banco atual vira ${ANTERIOR}. Digite RESTAURAR para continuar: " ok
[ "$ok" = "RESTAURAR" ] || { echo "cancelado"; exit 1; }

"${COMPOSE[@]}" stop api
trap '"${COMPOSE[@]}" start api >/dev/null' EXIT
"${PSQL[@]}" -d postgres <<SQL
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'aurora' AND pid <> pg_backend_pid();
ALTER DATABASE aurora RENAME TO ${ANTERIOR};
CREATE DATABASE aurora OWNER aurora_owner;
SQL
# Restaura num banco vazio (sem --clean: o schema tem tabelas particionadas). Extensões e donos vêm do dump.
"${COMPOSE[@]}" exec -T postgres pg_restore -U postgres -d aurora --exit-on-error < "$ARQUIVO"
"${PSQL[@]}" -d aurora -c "ALTER SCHEMA public OWNER TO aurora_owner;" >/dev/null
echo "Restaurado. Confira o app e a verificação da trilha (Administração > Auditoria)."
echo "Depois de validar, libere o espaço do banco anterior:  DROP DATABASE ${ANTERIOR};"
