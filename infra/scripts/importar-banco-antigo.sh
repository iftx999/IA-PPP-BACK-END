#!/usr/bin/env bash
# Importa os dados fictícios do banco da versão anterior da Aurora para o banco de desenvolvimento.
#
# Origem: um container Postgres rodando sobre uma CÓPIA do volume antigo (nunca o volume original),
#         com PostGIS e pgvector disponíveis (ver docs/06-pendencias.md, seção 6.4).
# Destino: o serviço "postgres" do infra/compose.yaml, já migrado (suba a API uma vez antes).
#
# Uso: infra/scripts/importar-banco-antigo.sh [container-origem]   (padrão: aurora-recupera)
set -euo pipefail

ORIGEM="${1:-aurora-recupera}"
DESTINO="$(docker compose -f "$(dirname "$0")/../compose.yaml" ps -q postgres)"

# Fora da importação: catálogos já semeados pelas migrações e tabelas de infraestrutura.
EXCLUIR=(flyway_schema_history spring_session spring_session_attributes limite_requisicao
         tarefa_agendada tarefa_em_fila permissao tipo_unidade spatial_ref_sys)
args=()
for t in "${EXCLUIR[@]}"; do args+=(--exclude-table-data="public.$t"); done

existentes=$(docker exec "$DESTINO" psql -U postgres -d aurora -Atc "SELECT count(*) FROM unidade")
if [[ "$existentes" != "0" ]]; then
  echo "O banco de destino já tem dados (unidade: $existentes linhas). Importação cancelada." >&2
  exit 1
fi

echo "Exportando dados de $ORIGEM..."
docker exec "$ORIGEM" pg_dump -U postgres --data-only --no-owner --disable-triggers "${args[@]}" aurora \
  | docker exec -i "$DESTINO" psql -U postgres -d aurora -v ON_ERROR_STOP=1 -q \
      -c "SET session_replication_role = replica" -f -

echo "Importado:"
docker exec "$DESTINO" psql -U postgres -d aurora -c "
  SELECT 'tenant' AS tabela, count(*) FROM tenant UNION ALL
  SELECT 'unidade', count(*) FROM unidade UNION ALL
  SELECT 'usuario', count(*) FROM usuario UNION ALL
  SELECT 'contrato', count(*) FROM contrato UNION ALL
  SELECT 'item_contratual', count(*) FROM item_contratual UNION ALL
  SELECT 'imovel_situacao_mensal', count(*) FROM imovel_situacao_mensal UNION ALL
  SELECT 'imovel_localizacao', count(*) FROM imovel_localizacao UNION ALL
  SELECT 'evento_auditoria', count(*) FROM evento_auditoria"
