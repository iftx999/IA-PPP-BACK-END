#!/bin/sh
# Backup diário dos bancos (roda no serviço "backup" do compose). Formato custom do pg_dump (compactado, restauração
# seletiva). Retenção local de AURORA_BACKUP_DIAS dias; copie /backups para fora do host (doc 09: RPO 24 h).
set -eu
DIAS=${AURORA_BACKUP_DIAS:-14}
fazer() {
  instante=$(date +%Y-%m-%dT%H%M)
  for alvo in "aurora:postgres:postgres:$PGPASSWORD_AURORA" "keycloak:keycloak-db:keycloak:$PGPASSWORD_KEYCLOAK"; do
    banco=${alvo%%:*}; resto=${alvo#*:}; host=${resto%%:*}; resto=${resto#*:}; usuario=${resto%%:*}; senha=${resto#*:}
    arquivo="/backups/${banco}-${instante}.dump"
    PGPASSWORD="$senha" pg_dump -h "$host" -U "$usuario" -d "$banco" -Fc -Z 6 -f "${arquivo}.parcial"
    mv "${arquivo}.parcial" "$arquivo"
    echo "$(date -Iseconds) backup ${arquivo} ($(du -h "$arquivo" | cut -f1))"
  done
  find /backups -name '*.dump' -mtime "+${DIAS}" -delete
}
if [ "${1:-}" = "agora" ]; then fazer; exit 0; fi
while true; do
  # Próxima execução às 02:00 (horário de São Paulo).
  agora=$(date +%s); alvo=$(date -d "$(date +%Y-%m-%d) 02:00" +%s 2>/dev/null || echo $((agora + 86400)))
  [ "$alvo" -le "$agora" ] && alvo=$((alvo + 86400))
  sleep $((alvo - agora))
  fazer || echo "$(date -Iseconds) FALHA no backup" >&2
done
