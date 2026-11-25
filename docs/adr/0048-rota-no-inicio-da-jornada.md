# 0048 · Rota calculada no início da jornada e mapa da rota no app

**Contexto.** A decisão D6 do doc 10 manda calcular a rota no "Iniciar jornada", a partir da posição do agente; a base
da equipe serve só para a prévia do programador. A ADR 0046 deixou esse gatilho para quando o app existisse (M6). Até
aqui o app mostrava a ordem da última publicação do programador, calculada a partir da base.

**Decisão.**
- **Gatilho:** abrir a jornada (`POST /campo/jornada/inicio`) publica o evento `JornadaIniciada` (API do módulo
  `campanhas`) só quando a jornada é realmente criada. Quando outro membro já tinha aberto a jornada da equipe, ou o
  pedido é repetido, nada é disparado. A roteirização escuta depois do commit e roda no contexto do sistema do
  tenant: a equipe e as OS já foram conferidas na abertura. A propriedade `aurora.roteirizacao.na-jornada` (padrão
  `true`) desliga o gatilho.
- **Cálculo:** origem `JORNADA`, só a equipe da jornada, com as OS abertas dela no dia (programadas, em execução e
  paralisadas); as em execução ficam fixas no começo. A partida é a última posição da jornada aberta, e a posição do
  início vale até chegar a primeira amostra (a consulta das equipes passou a considerá-la, o que também melhora a
  reotimização J7). O cálculo é assíncrono, com o mesmo limite de tempo da reotimização. Se o programador estiver
  calculando o dia da unidade, a jornada não dispara cálculo e fica valendo a ordem que ele publicar.
- **Publicação automática, restrita:** quando o cálculo fica pronto, a rota é publicada sem confirmação, mas só como
  **sequência das OS da própria equipe** (`programarEmSequencia`). Nenhuma OS muda de equipe e as publicações do
  programador para as outras equipes não são substituídas; uma publicação dele feita depois substitui esta. Quem
  pede e quem publica é o agente que abriu a jornada (`criada_por`, `publicada_por`). A auditoria registra o pedido
  e a publicação com `origem=JORNADA` e `automatica=true`. Uma falha na publicação deixa a rota `PRONTA`, que o
  programador pode publicar, e o app segue com a ordem anterior. Reordenar a lista não gera conflito de campo (10.9.5
  só trata cancelamento, troca de equipe e OS já encerrada).
- **`GET /api/v1/campo/rota`** (módulo `roteirizacao`, permissão `os:executar`, celular conferido pela API
  `JornadasDoCampo` de `campanhas`): a publicação mais recente com rota para a equipe da jornada aberta. Traz km,
  tempos, fim previsto, partida, paradas com chegada prevista e o traçado em `[longitude, latitude]`. ETag = id da
  roteirização: o app manda `If-None-Match` e recebe 304 quando nada mudou. Sem rota, 204; sem jornada, 409. A
  dependência segue a direção que já existia (`roteirizacao` → `campanhas`), verificada pelo Spring Modulith.
- **App:** a rota fica no banco SQLCipher (mapa sem sinal) e é apagada com o pacote ao encerrar a jornada. É baixada
  ao iniciar, a cada atualização e mais duas vezes (15 s e 45 s depois do início) enquanto a rota da jornada não
  chega. A tela "Mapa da rota" (botão no Hoje) mostra o traçado pelas ruas ou, sem Valhalla, segmentos retos
  tracejados; as OS numeradas na ordem da rota e na cor da situação, que abrem a OS ao tocar; a posição do agente; e
  o resumo "km · min de deslocamento · fim previsto", sempre rotulado como estimativa (RN-25) e com a origem
  (calculada da posição ou pelo programador).
- **Mapa com `flutter_map`** (BSD-3; dependências MIT, BSD e Apache 2.0: `latlong2`, `proj4dart`, `dart_earcut`
  etc.) em vez do plugin nativo do MapLibre. É Dart puro, então funciona nos testes de widget, o que pesa porque não
  há aparelho nem emulador no ambiente de desenvolvimento, e não acrescenta build nativo. O fundo usa tiles raster
  configuráveis na compilação (`AURORA_MAPA_TILES`, `AURORA_MAPA_ATRIBUICAO`, `AURORA_MAPA_ZOOM_MAXIMO`), como o
  front (ADR 0017). O padrão OSM é só para desenvolvimento, e um modelo vazio desliga o fundo.

**Consequências.** Cada início de jornada custa um cálculo (até 10 s de CPU do otimizador por equipe, dentro do limite
de cálculos simultâneos). Muitas equipes começando juntas entram na fila do semáforo, e a ordem chega ao app alguns
segundos ou minutos depois, sem bloquear o início. A lista do app pode mudar de ordem logo após o início. Sem sinal no
campo, o fundo do mapa só mostra o que o celular já baixou: o cache de tiles fica para quando a empresa escolher o
provedor. Os gatilhos automáticos de reotimização (emergencial nova, paralisação longa, atraso de 30 min) continuam
pendentes (M7).
