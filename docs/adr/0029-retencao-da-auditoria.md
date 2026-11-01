# 0029 · Retenção da trilha de auditoria: prazo por cliente, expurgo do começo da cadeia e âncora

**Contexto.** O doc 08 (8.7) pede que a auditoria seja "retida por prazo configurável e protegida contra edição". A
trilha é somente inserção (a aplicação não tem `DELETE`) e encadeada por HMAC (ADR 0009): apagar eventos sem critério
quebraria a verificação ou esconderia adulteração. A tabela é particionada por ano, e só existiam as partições de 2026
e 2027.

**Decisão.**
- **Prazo por cliente:** `tenant.auditoria_retencao_meses`, padrão 60 (5 anos), entre 12 e 240 por `CHECK` no banco.
  É política do cliente, como MFA e SSO: altera quem tem `usuario:gerenciar` no cliente inteiro, na tela de Usuários
  (`PUT /api/v1/seguranca/retencao-auditoria`). Quem tem `auditoria:ler` só consulta
  (`GET /api/v1/auditoria/retencao`), mantendo a segregação de funções do banco herdado (o administrador não lê a
  trilha; o Auditor não muda a política). A alteração vai para a trilha (`RETENCAO_AUDITORIA_ALTERADA`, de/para).
- **Expurgo diário** (`expurgar-trilha`, agenda de operação): a função `expurgar_auditoria_vencida()`
  (`SECURITY DEFINER`, V25) apaga, no tenant do contexto, **só um prefixo contínuo da cadeia**: tudo antes do primeiro
  evento, em sequência, que ainda está no prazo. Um evento antigo que veio depois de um recente (ex.: login importado
  com o horário original, ADR 0028) fica, para não abrir buraco no meio. A aplicação continua sem `DELETE`; mesmo
  comprometida, não apaga nada dentro do prazo nem baixa o prazo abaixo de 12 meses.
- **Âncora:** cada expurgo grava em `auditoria_expurgo` até qual sequência apagou e o hash do último evento apagado. A
  verificação da trilha começa por essa âncora: o primeiro evento que ficou precisa apontar para ela, então apagar
  algo fora do expurgo continua aparecendo como adulteração. O expurgo também vira evento na trilha
  (`TRILHA_EXPURGADA`, com quantidade, sequência e limite).
- **Partições:** a mesma tarefa garante as partições do ano atual e dos dois seguintes
  (`garantir_particao_auditoria`), antes de existirem eventos daquele ano na partição padrão.

**Alternativas descartadas.** Apagar por data (`ocorrido_em < limite`): abriria buracos na cadeia por causa dos
eventos importados com horário original. Desanexar partições inteiras: a tabela é compartilhada entre clientes com
prazos diferentes. Arquivar em outro armazenamento antes de apagar: os backups (`pgBackRest`/`pg_dump`, ADR 0022)
já guardam o histórico pelo prazo deles, e a Auditoria exporta CSV com SHA-256 quando o cliente quer um arquivo.

**Consequências.** Reduzir o prazo apaga, no próximo expurgo diário, o que ficou fora dele (a tela avisa para
exportar antes). Eventos legados importados da versão anterior também saem quando vencem. Um evento antigo que veio
depois de um recente só sai quando o recente também vencer.
