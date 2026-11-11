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
6. Backup fora do host (seção 4): `cp pitr/pitr.env.example pitr/pitr.env` e preencha o bucket S3 (ou compatível)
   antes da primeira subida. Guarde `AURORA_BACKUP_CHAVE` e a senha do repositório S3 no cofre de segredos, **fora
   deste host**: sem elas nenhum backup é restaurável. Copie também `backups/` (pg_dump diário) para fora do host.

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

## 3.1 Antivírus (ADR 0033)

- O serviço `clamav` verifica todo PDF de contrato e toda logo antes de gravar. A API recusa o envio (503) se ele
  não responder; não há como desligar em produção sem mudar `AURORA_ANTIVIRUS_OBRIGATORIO` (não recomendado).
- Memória: reserve ~1,5 GB para ele. Assinaturas atualizadas pelo `freshclam` do próprio contêiner (volume
  `clamav-assinaturas`); depois de recriar o volume, os envios ficam recusados por alguns minutos.
- Ameaça encontrada vira evento `ARQUIVO_RECUSADO_PELO_ANTIVIRUS` na trilha do cliente (nome da ameaça e SHA-256) e
  a métrica `aurora_antivirus_verificacoes_total{resultado="Infectado"}`.
- Teste rápido: `CLAMAV_HOST=<host> ./mvnw test -Dtest=ClamAvAntivirusTest` envia o arquivo de teste EICAR.

## 4. Backup e restauração

Duas camadas (ADR 0039):

**Backup contínuo com PITR (pgBackRest, serviço `backup-pitr`)** — a principal.
- O PostgreSQL arquiva cada segmento de WAL no repositório (no máximo a cada 60 s); completo aos domingos e
  diferencial nos outros dias às 01:00; verificação de integridade do repositório aos domingos. Repositório 1 no
  volume `pitr-repositorio`, cifrado (AES-256); repositório 2 no S3 de `pitr/pitr.env`, também cifrado.
- Janela de restauração: `AURORA_PITR_SEMANAS` (2) semanas no volume local; `PGBACKREST_REPO2_RETENTION_FULL` (8)
  semanas no S3.
- Situação: `docker compose --env-file .env exec backup-pitr pgbackrest info` (backups, WAL arquivado, tamanhos).
  Falhas aparecem no log como `FALHA no backup` (`docker compose ... logs backup-pitr`).
- Backup na hora: `docker compose --env-file .env exec backup-pitr sh /pitr/agendar.sh agora full` (ou `diff`).
- **Restaurar a um instante** (ex.: exclusão errada às 14:32): `./pitr/restaurar.sh "2026-11-11 14:30:00-03"`.
  Sem perder nada do que foi arquivado (ex.: disco corrompido): `./pitr/restaurar.sh mais-recente`. API e backups
  param; o diretório de dados atual é **renomeado**, não apagado (`18/docker.antes_<instante>`); se a restauração
  falhar, ele volta ao lugar e os serviços sobem de novo. Depois: confira o app e a verificação da trilha, faça um
  backup completo (a restauração abre uma linha do tempo nova) e, validado, libere o diretório anterior com o
  comando que o script mostra.
- Host perdido: num host novo, com o mesmo `.env` e `pitr/pitr.env`, suba só o `postgres` e o `backup-pitr` e rode
  `./pitr/restaurar.sh mais-recente`. O pgBackRest procura o backup nos repositórios em ordem e, com o volume local
  vazio, usa o S3. Ensaie este caso no teste de restauração antes de precisar dele.

**Cópia lógica diária (pg_dump, serviço `backup`)** — segunda linha de defesa e restauração seletiva.
- Às 02:00 (Aurora e Keycloak) em `backups/`, retenção de `AURORA_BACKUP_DIAS` (14) dias. O Keycloak só tem esta.
  Manual: `docker compose --env-file .env exec backup sh /backup.sh agora`.
- Restaurar a Aurora do dump: `./restaurar-backup.sh backups/aurora-AAAA-MM-DDTHHMM.dump`. O banco atual vira
  `aurora_antes_<instante>` (nada é apagado); libere com `DROP DATABASE ...` depois de conferir.

**Teste de restauração:** todo mês, num host de homologação, com `./pitr/restaurar.sh` para um instante conhecido;
registre data, instante pedido, duração e resultado (doc 09).

- Trilha de auditoria (ADR 0029): cada cliente define o prazo de retenção (Usuários › Retenção da auditoria; padrão
  60 meses). O expurgo diário apaga só o que venceu e registra na trilha; os backups acima guardam só os últimos
  `AURORA_BACKUP_DIAS` dias. Cliente que precise de arquivo de longo prazo exporta o CSV da Auditoria (com SHA-256)
  antes de reduzir o prazo. A tarefa também cria as partições anuais da trilha com 2 anos de antecedência.
- Metas: **RPO de ~1 minuto** (WAL arquivado a cada 60 s no máximo; com o S3, vale também para perda do host); RTO
  de minutos (restauração de base de demonstração em ~2 s; medir no teste mensal com o volume real).

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
- `AURORA_BACKUP_CHAVE` cifra o repositório do backup contínuo: **não troque** num repositório existente (os backups
  antigos ficariam ilegíveis); para trocar, crie um repositório novo e mantenha a chave antiga até a retenção vencer.
- `AURORA_AUDITORIA_CHAVE` assina a trilha: trocar inicia uma nova cadeia (ADR 0009).
- `AURORA_CHAVE_CURSOR`: cursores de paginação antigos deixam de valer (o usuário só recarrega a lista).
- `AURORA_IDENTIDADE_SEGREDO`: troque também no cliente `aurora-api-admin` do Keycloak.
- `AURORA_SEGUNDO_FATOR_ADMINISTRADORES` (padrão `true`): exige o código do aplicativo autenticador de quem gerencia
  usuários (ADR 0023). Administrador que perdeu o celular: outro administrador remove o aplicativo em
  Usuários › Verificação em duas etapas; se não houver outro, remova a credencial OTP pelo console do Keycloak.
- Chaves de IA (ADR 0019): só por variável de ambiente; nunca em chat, e-mail ou repositório.
