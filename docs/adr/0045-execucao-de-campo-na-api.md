# 0045 · Execução de campo na API: eventos idempotentes, jornada, fotos em partes, conflitos e celulares

**Contexto.** O quarto marco dos serviços de campo (doc 10, M4; histórias J8 a J11 e J13) prepara a API que o app
Flutter (M6) vai usar sem sinal: cada ação do agente vira um evento na fila local do celular e chega depois, talvez
repetida, fora de ordem ou quando a programação já mudou. O ciclo da OS da ADR 0044 ganha iniciar, paralisar e
retomar. Migração V34.

**Decisão.**
- **Eventos idempotentes e em ordem** (RN-26; `POST /api/v1/campo/eventos`, até 200 por envio): o `id` do evento é
  gerado no celular e a `sequencia` é por OS. Evento já recebido responde `REPETIDO` com a situação original, sem
  aplicar de novo (inclusive na corrida de dois envios iguais: chave primária). Sequência acima da próxima fica
  `AGUARDANDO` e é processada quando a anterior chega; sequência já usada é `REJEITADO`. Todo evento processado ocupa
  a sequência — inclusive o recusado por dados inválidos, para a fila seguinte não travar. Cada evento roda em
  transação própria (um recusado não desfaz os outros) com a OS travada (`FOR UPDATE`).
- **Respostas por evento:** `APLICADO`, `REPETIDO`, `CONFLITO` (registrado; vale o campo e o programador decide),
  `SUPERADO` (não aplicado; vale a programação), `AGUARDANDO` e `REJEITADO` (com a mensagem). O que o campo fez nunca é
  descartado: todo evento fica em `os_evento`, com hora do celular, hora do servidor, GPS e precisão, distância até o
  alvo no início, usuário, equipe, celular, jornada e versão do pacote.
- **Conflitos (10.9.5):** OS cancelada ou passada para outra equipe e o campo iniciou, paralisou, retomou ou encerrou →
  a OS fica com a equipe que trabalhou (cancelada volta a programada para receber o evento) e abre-se um conflito
  (`OS_CANCELADA`, `OUTRA_EQUIPE`); se a cancelada já foi substituída por outra OS do mesmo alvo, ou a transição não
  cabe, o evento fica registrado sem mudar a OS. OS já encerrada (por outra equipe ou pelo gestor) e o campo também
  trabalhou → as duas execuções ficam registradas (`JA_ENCERRADA`). Devolver não é trabalho de campo: com a programação
  mudada, é `SUPERADO`. Um conflito aberto por OS e tipo; a caixa `GET /api/v1/ordens/conflitos` e a decisão em texto
  (`POST .../conflitos/{id}/decisao`, auditada) ficam com `os:programar`.
- **Regras do campo (RN-22):** paralisar e devolver exigem motivo do tipo certo — aceito mesmo se desativado depois de
  o celular baixar o pacote; encerrar executada valida os **parâmetros da versão da OS** (RN-20): obrigatórios só
  quando a condição vale (os de condição falsa são descartados), tipo, faixa (valor, caracteres ou fotos), opções,
  data com fuso; fotos são ids de anexos e contam no mínimo e no máximo mesmo antes de chegarem. Quantidade como na
  web. Paralisada não encerra. Avisos no evento, sem recusar: `RELOGIO` (celular a mais de 5 min do servidor no
  envio), `FORA_DO_LOCAL` (início além do raio do cliente, 300 m por padrão, `tenant.campo_raio_inicio_m`; a OS fica
  marcada), `SEM_GPS`, `SEM_JORNADA`, `EXECUCAO_SIMULTANEA` (a equipe já tinha outra OS em execução) e
  `APARELHO_REVOGADO`. Os encerramentos pela web (gestor e "Minhas OS") continuam sem os parâmetros: são registro
  posterior.
- **Localização pelo campo (J9):** no encerramento executado de um imóvel com localização aproximada (pelo logradouro
  ou pelo bairro), o GPS da execução passa a ser a localização (`CAMPO`, precisão de endereço).
- **Jornada e pacote do dia (J8; 10.9.2):** `POST /campo/jornada/inicio` (id do celular; outro membro da equipe entra
  na mesma jornada aberta), `POST /campo/jornada/fim` (recusa com OS em execução), `GET /campo/jornada` com as OS
  programadas até hoje, em execução ou paralisadas, os parâmetros por versão do serviço, os motivos do campo e o raio.
  A **versão** do pacote é a última alteração das OS dele; com `?desde=` vêm só as alteradas e sempre a lista de ids
  (o celular remove as que saíram). A rota entra no M5.
- **Fotos em partes (10.9.3):** `PUT /campo/anexos/{id}?os=&parametro=` com `Content-Range` (até 1 MB por parte,
  5 MB por foto, JPEG, PNG ou WebP) e o SHA-256 do arquivo; a parte já recebida é aceita de novo, a fora de ordem
  responde 409 e `GET /campo/anexos/{id}` diz de onde retomar. Completa, a foto é conferida pelo SHA-256, pela
  assinatura do formato e pelo antivírus (ADR 0033); a recusada é descartada e auditada. Fica em `bytea`, como os
  documentos de contrato. A web vê o histórico (`GET /ordens/{id}/execucao`) e a foto (`GET /ordens/{id}/anexos/{a}`,
  auditada, sem cache).
- **Posições (RN-27):** `POST /campo/posicoes` (até 500) só dentro da jornada mais recente da equipe; repetidas são
  ignoradas. Expurgo diário depois de 90 dias (P4, `aurora.campo.posicoes.guarda`). Sem particionamento por mês por
  enquanto: na escala de referência (50 equipes, uma amostra a cada 2 min) são cerca de 1,4 milhão de linhas em 90
  dias, que o índice por equipe e momento atende; particionar quando o volume pedir.
- **Celulares (J13):** toda chamada do app leva `X-Aurora-Dispositivo` (id gerado no primeiro login), cadastrado por
  `POST /campo/dispositivo` para a pessoa (outro login no mesmo celular o transfere, auditado). Sem cadastro ou
  bloqueado: 403 com `codigo` `DISPOSITIVO_NAO_CADASTRADO` ou `DISPOSITIVO_REVOGADO`. O bloqueado ainda envia a fila
  pendente (eventos marcados e resposta `revogado: true`) e confirma a limpeza local (`/campo/dispositivo/apagado`).
  Permissão nova `dispositivo:gerenciar` (Administrador e Gestor de serviços): `GET /api/v1/dispositivos` e
  `POST .../{id}/revogar`, auditado.
- **Acesso:** eventos, fotos e conflitos guardam a unidade da OS e seguem o escopo por unidade (RLS, como a OS);
  jornada e posições seguem a equipe. Auditoria: `DISPOSITIVO_CADASTRADO`, `DISPOSITIVO_REVOGADO`,
  `CONFLITO_DE_CAMPO_ABERTO`, `CONFLITO_DE_CAMPO_DECIDIDO`, `ANEXO_RECUSADO`, `ANEXO_DE_ORDEM_LIDO`, além de
  `ORDEM_DEVOLVIDA` e `RESULTADO_DE_ORDEM_REGISTRADO` (`registradoPor: APP`).

**Celular da empresa (P10, decidido em 18/11/2026).** O app roda em celulares da empresa: a revogação apaga todos os
dados do app sem risco a dados pessoais do agente, e o app (M6) pode exigir bloqueio de tela e impedir captura de
tela.

**Consequências.** O app (M6) tem o contrato completo para trabalhar sem sinal. A web mostra o histórico, as fotos e
a caixa de conflitos; o mapa do dia com as posições fica no M7, e a rota no pacote, no M5. Pendem: PIN e biometria
(no app), sessão de 7 dias sem rede (P5, no Keycloak), e o RIPD com a
localização dos funcionários.
