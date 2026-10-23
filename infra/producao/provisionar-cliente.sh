#!/usr/bin/env bash
# Cria um cliente (tenant) novo na Aurora: unidade raiz, perfis do sistema e o primeiro administrador, que recebe
# por e-mail o link para criar a senha. Os demais usuários são convidados pela própria tela (H8).
#
#   ./provisionar-cliente.sh "Grupo Saneamento X" admin@grupox.com.br "Nome do Administrador"
#
# Roda no host de produção, na pasta infra/producao, com o .env preenchido e os serviços no ar. Idempotente: rodar de
# novo com o mesmo e-mail não duplica nada.
set -euo pipefail

NOME_CLIENTE=${1:?informe o nome do cliente}
EMAIL_ADMIN=${2:?informe o e-mail do primeiro administrador}
NOME_ADMIN=${3:?informe o nome do primeiro administrador}

set -a; source .env; set +a
EMISSOR="https://${AURORA_DOMINIO_ID}/realms/aurora"
COMPOSE=(docker compose --env-file .env)

echo "1/2 Banco: cliente, unidade raiz, perfis do sistema e administrador"
"${COMPOSE[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d aurora \
  -v nome="$NOME_CLIENTE" -v email="$EMAIL_ADMIN" -v admin="$NOME_ADMIN" -v emissor="$EMISSOR" <<'SQL'
BEGIN;
-- Um emissor (realm) por cliente: se o emissor já tem cliente, reaproveita (idempotência).
SELECT coalesce((SELECT tenant_id FROM tenant_emissor WHERE emissor = :'emissor'), gen_random_uuid()) AS tenant \gset
INSERT INTO tenant (id, nome) VALUES (:'tenant', :'nome') ON CONFLICT (id) DO NOTHING;
INSERT INTO tenant_emissor (emissor, tenant_id) VALUES (:'emissor', :'tenant') ON CONFLICT (emissor) DO NOTHING;

-- Unidade raiz (grupo econômico); as demais são cadastradas pela tela de Unidades (H1).
SELECT coalesce((SELECT id FROM unidade WHERE tenant_id = :'tenant' AND unidade_pai_id IS NULL), gen_random_uuid())
       AS raiz \gset
INSERT INTO unidade (id, tenant_id, tipo, codigo, nome, caminho)
VALUES (:'raiz', :'tenant', 'GRUPO', 'GRUPO', :'nome', ('u' || replace(:'raiz', '-', ''))::ltree)
ON CONFLICT (id) DO NOTHING;

-- Perfis do sistema (não editáveis; o cliente cria os seus a partir do catálogo, RN-16).
CREATE TEMP TABLE perfis_sistema (nome text, descricao text, permissoes text[]) ON COMMIT DROP;
INSERT INTO perfis_sistema VALUES
  ('Administrador do tenant', 'Usuários, perfis, unidades e integrações',
   ARRAY['assistente:usar','contrato:gerenciar','contrato:ler','dados_pessoais:ler','indicador:gerenciar',
         'indicador:ler','integracao:gerenciar','mapa:marcar','relatorio:exportar','unidade:gerenciar','unidade:ler',
         'usuario:gerenciar']),
  ('Auditor', 'Leitura da trilha de auditoria', ARRAY['auditoria:ler','unidade:ler']),
  ('Diretor', 'Leitura de todos os indicadores no escopo',
   ARRAY['assistente:usar','contrato:ler','indicador:ler','relatorio:exportar','unidade:ler']),
  ('Gestor comercial', 'Indicadores e dados comerciais no escopo',
   ARRAY['assistente:usar','contrato:ler','dados_pessoais:ler','indicador:ler','mapa:marcar','unidade:ler']);
INSERT INTO perfil (id, tenant_id, nome, sistema, descricao)
SELECT gen_random_uuid(), :'tenant', p.nome, true, p.descricao FROM perfis_sistema p
 WHERE NOT EXISTS (SELECT 1 FROM perfil x WHERE x.tenant_id = :'tenant' AND x.nome = p.nome);
INSERT INTO perfil_permissao (tenant_id, perfil_id, permissao)
SELECT :'tenant', f.id, unnest(p.permissoes) FROM perfis_sistema p JOIN perfil f ON f.tenant_id = :'tenant' AND f.nome = p.nome
ON CONFLICT DO NOTHING;

-- Primeiro administrador: o vínculo com o Keycloak (sub) acontece no primeiro login, pelo e-mail.
INSERT INTO usuario (id, tenant_id, email, nome, convidado_em)
SELECT gen_random_uuid(), :'tenant', lower(:'email'), :'admin', now()
 WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE tenant_id = :'tenant' AND email = lower(:'email'));
INSERT INTO usuario_perfil_escopo (id, tenant_id, usuario_id, perfil_id, unidade_id)
SELECT gen_random_uuid(), :'tenant', u.id, f.id, :'raiz'
  FROM usuario u JOIN perfil f ON f.tenant_id = u.tenant_id AND f.nome = 'Administrador do tenant'
 WHERE u.tenant_id = :'tenant' AND u.email = lower(:'email')
   AND NOT EXISTS (SELECT 1 FROM usuario_perfil_escopo e WHERE e.usuario_id = u.id AND e.perfil_id = f.id);
COMMIT;
SELECT 'cliente ' || :'tenant' || ' pronto' AS resultado;
SQL

echo "2/2 Keycloak: usuário e e-mail para criar a senha"
KC=("${COMPOSE[@]}" exec -T keycloak /opt/keycloak/bin/kcadm.sh)
"${KC[@]}" config credentials --server http://localhost:8080 --realm master \
  --user "$KEYCLOAK_ADMIN_USUARIO" --password "$KEYCLOAK_ADMIN_SENHA" >/dev/null
ID=$("${KC[@]}" get users -r aurora -q "email=${EMAIL_ADMIN}" -q exact=true --fields id --format csv --noquotes | head -1)
# Nome e sobrenome como no convite da API (o perfil de usuário do Keycloak exige os dois).
PRIMEIRO_NOME=${NOME_ADMIN%% *}
SOBRENOME=${NOME_ADMIN#* }
if [ "$SOBRENOME" = "$NOME_ADMIN" ]; then SOBRENOME="-"; fi
if [ -z "$ID" ]; then
  "${KC[@]}" create users -r aurora -s "username=${EMAIL_ADMIN}" -s "email=${EMAIL_ADMIN}" \
    -s "firstName=${PRIMEIRO_NOME}" -s "lastName=${SOBRENOME}" -s enabled=true -s emailVerified=false >/dev/null
  ID=$("${KC[@]}" get users -r aurora -q "email=${EMAIL_ADMIN}" -q exact=true --fields id --format csv --noquotes | head -1)
fi
REDIRECT=$(printf 'https://%s/' "$AURORA_DOMINIO" | sed 's/:/%3A/g; s/\//%2F/g')
# -n: este recurso só aceita PUT (sem o GET de mesclagem do kcadm).
if ! "${KC[@]}" update "users/${ID}/execute-actions-email?client_id=aurora-web&lifespan=259200&redirect_uri=${REDIRECT}" \
     -r aurora -n -b '["UPDATE_PASSWORD","VERIFY_EMAIL"]'; then
  # Sem e-mail (SMTP fora do ar ou mal configurado): senha temporária, trocada no primeiro acesso.
  TEMPORARIA=$(openssl rand -base64 18 | tr -d '/+=')
  "${KC[@]}" set-password -r aurora --userid "$ID" --new-password "$TEMPORARIA" --temporary
  # Sem o e-mail de verificação, quem confirma o endereço é o operador que entrega a senha (a API só vincula o
  # primeiro acesso por e-mail verificado).
  "${KC[@]}" update "users/${ID}" -r aurora -s emailVerified=true
  echo "ATENÇÃO: o e-mail não foi enviado (confira o SMTP no .env e o log do Keycloak)."
  echo "Senha temporária de ${EMAIL_ADMIN} (exibida só agora; entregue por canal seguro, troca obrigatória no"
  echo "primeiro acesso): ${TEMPORARIA}"
  echo "O e-mail foi marcado como verificado: confirme pessoalmente que ${EMAIL_ADMIN} é da pessoa certa."
else
  echo "Pronto: ${EMAIL_ADMIN} recebeu o link (válido por 72 h) para criar a senha."
fi
