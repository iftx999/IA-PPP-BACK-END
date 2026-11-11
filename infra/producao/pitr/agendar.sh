#!/bin/sh
# Backup contínuo (ADR 0039), no serviço "backup-pitr": o PostgreSQL arquiva cada segmento de WAL no repositório
# (archive_command); aqui ficam os backups de base. Completo aos domingos e diferencial nos outros dias, às 01:00
# (America/Sao_Paulo). Com o WAL arquivado, a restauração volta a qualquer instante dentro da retenção.
#   agendar.sh            laço de agendamento (padrão do serviço)
#   agendar.sh agora full|diff|incr   um backup já
set -eu

preparar() {
  # Idempotente: cria o stanza na primeira subida e confere que o arquivamento do WAL chega ao repositório.
  until pg_isready -q -h /var/run/postgresql; do sleep 2; done
  pgbackrest stanza-create
  pgbackrest check
}

backup() {
  tipo=$1
  inicio=$(date +%s)
  if pgbackrest backup --type="$tipo"; then
    echo "$(date -Iseconds) backup ${tipo} concluído em $(( $(date +%s) - inicio )) s"
    date +%s > /var/lib/pgbackrest/ultimo-backup-ok
  else
    echo "$(date -Iseconds) FALHA no backup ${tipo}" >&2
    return 1
  fi
}

if [ "${1:-}" = "agora" ]; then
  preparar
  backup "${2:-full}"
  pgbackrest info
  exit 0
fi

preparar
# Sem nenhum backup de base ainda, faz o completo já: sem ele o WAL arquivado não serve para restaurar.
pgbackrest info --output=json | grep -q '"backup":\[\]' && backup full
while true; do
  agora=$(date +%s); alvo=$(date -d "$(date +%Y-%m-%d) 01:00" +%s 2>/dev/null || echo $((agora + 86400)))
  [ "$alvo" -le "$agora" ] && alvo=$((alvo + 86400))
  sleep $((alvo - agora))
  if [ "$(date +%u)" = "7" ]; then backup full || true; else backup diff || true; fi
  # Confere a integridade do repositório (checksums de backups e WAL) uma vez por semana.
  [ "$(date +%u)" = "7" ] && { pgbackrest verify || echo "$(date -Iseconds) FALHA na verificação do repositório" >&2; }
done
