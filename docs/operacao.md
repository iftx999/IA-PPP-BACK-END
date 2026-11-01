# Operação da Aurora (runbook)

Implantação num host único com Docker Compose (ADR 0022). Todos os comandos rodam em `infra/producao/`, com o
`.env` preenchido. **Nunca** versione o `.env` nem os backups.

## 1. Primeira instalação

1. Host Linux com Docker 27+ e Compose v2; portas 80 e 443 abertas; dois nomes DNS apontando para o host
   (`AURORA_DOMINIO`, ex. `aurora.empresa.com.br`, e `AURORA_DOMINIO_ID`, ex. `id.aurora.empresa.com.br`).
2. `cp .env.example .env` e preencha. Segredos: `openssl rand -base64 48`. `AURORA_VERSAO` = tag do commit publicada
   no GHCR (ex. `sha-84ecca8`). Defina SMTP real (convites, redefinição de senha e alertas por e-mail dependem dele; a API usa as mesmas
   variáveis `AURORA_SMTP_*` do Keycloak, ADR 0024). Métrica `aurora_alertas_emails_total{resultado="falha"}` indica
   falha de envio.
3. `docker compose --env-file .env --profile observabilidade up -d`
4. Confira: `docker compose --env-file .env ps` (api e web `healthy`); `https://AURORA_DOMINIO` abre o login.
5. Fundo do mapa: contrate um provedor de tiles (os tiles públicos do OpenStreetMap não admitem uso de produção) e
   ponha as origens dele em `AURORA_CSP_MAPA`.
6. Copie `backups/` para fora do host (rsync/objeto) com a frequência do backup (diária).

O console de administração do Keycloak **não** fica exposto na internet. Para usá-lo:
`ssh -L 8180:localhost:8180 host` e um `docker compose ... exec keycloak` com `kcadm.sh`, ou publique a porta só para
a rede de administração.

## 2. Novo cliente

**Com vários clientes na instalação (ADR 0027), pela tela:** a administração da plataforma entra em
`https://<AURORA_DOMINIO>/plataforma` e usa "Novo cliente" (código, nome, primeiro administrador e limites). A Aurora
cria o realm do cliente, a unidade principal, os perfis do sistema e envia o convite. Se o provisionamento ficar
pendente (ex.: Keycloak fora do ar), use "Concluir provisionamento" no menu do cliente. As pessoas entram com o código
da empresa (ou pelo link do convite, que já o traz).

Preparação, uma vez por instalação:
1. `AURORA_PLATAFORMA_SEGREDO` e `AURORA_VARIOS_CLIENTES=true` no `.env`; `docker compose --env-file .env up -d`
   (o realm `plataforma` é importado se ainda não existir; em instalação existente, importe
   `infra/keycloak/realm-plataforma-producao.json` pelo console do Keycloak).
2. Conta de serviço que cria os realms (só `create-realm`): ver o cabeçalho de `infra/keycloak/configurar-plataforma.sh`.
3. Primeiro operador: `./criar-operador-plataforma.sh operacao@empresa.com.br "Nome Sobrenome"` (TOTP obrigatório).

**Instalação de um cliente só, pelo script:**

```bash
./provisionar-cliente.sh "Grupo Saneamento X" admin@grupox.com.br "Nome Sobrenome"
```

Cria cliente, unidade raiz, perfis do sistema e o primeiro administrador, que recebe o e-mail para criar a senha
(72 h). Se o e-mail falhar, o script mostra uma senha temporária (troca obrigatória): entregue por canal seguro e
confirme que o e-mail é da pessoa. O administrador cadastra unidades, convida usuários e configura integrações pela
própria Aurora. O script pode rodar de novo sem duplicar nada.

**SSO corporativo (S10):** o administrador do cliente configura em Administração > Usuários > Login corporativo,
com o endereço de descoberta OIDC ou de metadados SAML do IdP da empresa (só HTTPS e endereço público). A tela mostra
os endereços que a TI do cliente registra no IdP. Só pessoas já convidadas entram pelo SSO.

## 3. Atualizar a versão

1. Escolha a tag publicada pelo CI (aba Packages do GitHub; o CI só publica imagem sem vulnerabilidade crítica).
2. Faça um backup na hora: `docker compose --env-file .env exec backup sh /backup.sh agora`.
3. Troque `AURORA_VERSAO` no `.env` e rode `docker compose --env-file .env up -d`. As migrações do banco (Flyway)
   rodam na subida da API; o encerramento é gracioso (requisições em andamento terminam).
4. Confira saúde, login e o painel do Grafana (erros 5xx e latência).
5. Se a nova versão exige mudanças no realm (o Keycloak só importa o realm na primeira subida), rode o script
   indicado nas notas da versão. Para o SSO corporativo (ADR 0025), uma vez: `./atualizar-realm-sso.sh`
   (idempotente). Para a trilha de acesso (ADR 0028), uma vez **em cada realm de cliente**:
   `KC_REALM=<código> ./atualizar-realm-eventos.sh` (idempotente; clientes criados pela tela já vêm prontos).

**Voltar versão:** recoloque a tag anterior e `up -d`. Se a versão nova aplicou migração incompatível, restaure o
backup do passo 2 (seção 4).

## 4. Backup e restauração

- Backup automático às 02:00 (Aurora e Keycloak) em `backups/`, retenção de `AURORA_BACKUP_DIAS` (14) dias.
  Manual: `docker compose --env-file .env exec backup sh /backup.sh agora`.
- Restaurar a Aurora: `./restaurar-backup.sh backups/aurora-AAAA-MM-DDTHHMM.dump`. A API para; o banco atual vira
  `aurora_antes_<instante>` (nada é apagado); o backup é restaurado num banco novo; a API volta. Depois de conferir
  o app e a verificação da trilha (Administração › Auditoria), libere o anterior com `DROP DATABASE ...`.
- Teste de restauração: ao menos uma vez por trimestre, num host de homologação (doc 09).
- Trilha de auditoria (ADR 0029): cada cliente define o prazo de retenção (Usuários › Retenção da auditoria; padrão
  60 meses). O expurgo diário apaga só o que venceu e registra na trilha; os backups acima guardam só os últimos
  `AURORA_BACKUP_DIAS` dias. Cliente que precise de arquivo de longo prazo exporta o CSV da Auditoria (com SHA-256)
  antes de reduzir o prazo. A tarefa também cria as partições anuais da trilha com 2 anos de antecedência.
- Metas: RPO 24 h (backup diário; reduzir com backup contínuo de WAL quando necessário); RTO de minutos para a base
  atual.

## 5. Observabilidade

- Grafana em `https://AURORA_DOMINIO/grafana/` (usuário do `.env`): painel **Aurora · Visão geral** (tráfego,
  latência p95 por rota contra as metas, erros, JVM, conexões, assistente e custo de IA, OCR, cargas) e traços no
  Tempo.
- Alertas (Prometheus): API fora do ar, painel/tiles lentos, erros 5xx acima de 2%, respostas do assistente com
  números sem fonte acima de 10%, gasto de IA acima de US$ 50/dia. Encaminhe-os ao canal da equipe pelo Grafana
  (contact points).
- Logs: `docker compose --env-file .env logs -f api` (JSON com `traceId` e `tenant_id`; sem dados pessoais).
- Saúde e métricas da API ficam só na rede interna (porta 8081).

## 6. Incidentes

1. Registre início, sintoma e impacto (clientes afetados).
2. Saúde: `docker compose ps`; logs da API e do proxy; painel do Grafana.
3. Suspeita de vazamento ou acesso indevido: preserve logs e a trilha de auditoria (verificação da cadeia em
   Administração › Auditoria), revogue credenciais do agente e chaves de IA envolvidas, troque segredos do `.env` e
   reinicie. Avalie comunicação à ANPD e aos titulares (LGPD).
4. Pós-incidente: causa, correção e prevenção registradas.

## 7. Segredos

Troca periódica (ou após incidente): gere novos valores, atualize o `.env` e `up -d`. Atenção:
- `AURORA_AUDITORIA_CHAVE` assina a trilha: trocar inicia uma nova cadeia (ADR 0009).
- `AURORA_CHAVE_CURSOR`: cursores de paginação antigos deixam de valer (o usuário só recarrega a lista).
- `AURORA_IDENTIDADE_SEGREDO`: troque também no cliente `aurora-api-admin` do Keycloak.
- `AURORA_SEGUNDO_FATOR_ADMINISTRADORES` (padrão `true`): exige o código do aplicativo autenticador de quem gerencia
  usuários (ADR 0023). Administrador que perdeu o celular: outro administrador remove o aplicativo em
  Usuários › Verificação em duas etapas; se não houver outro, remova a credencial OTP pelo console do Keycloak.
- Chaves de IA (ADR 0019): só por variável de ambiente; nunca em chat, e-mail ou repositório.
