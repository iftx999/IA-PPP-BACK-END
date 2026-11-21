# 0049 · Acompanhamento da operação de campo e sugestões automáticas de reotimização

**Contexto.** O M7 do doc 10 (J12) pede o mapa do dia (equipes, OS por situação, atraso estimado) e os indicadores
de prazo, tempo e produtividade, com o tempo real medido alimentando a sugestão de ajuste do tempo padrão. A 10.8
pede ainda os gatilhos de reotimização do J7 (emergencial nova, paralisação longa, devolução, atraso de 30 min),
"sempre com confirmação do programador ou do agente". Até aqui a reotimização só existia como botão na roteirização.

**Decisão.**
- **Módulo `operacao`** (controller/service/repository/model), permissão `os:programar`, em `/api/v1/operacao`:
  `GET /dia`, `GET /indicadores`, `GET /sugestoes`, `POST /sugestoes/{id}/aceitar` e `/ignorar`. Tudo é consulta
  sobre o que já existe (OS, `os_evento`, jornadas, posições, rotas publicadas), sempre na área da unidade escolhida
  (ela e as de baixo) e dentro do RLS do usuário.
- **Mapa do dia:** OS com data programada no dia (menos canceladas) e, sendo hoje, as emergenciais abertas sem data;
  ponto do imóvel ou o meio do trecho. Equipes ativas da área ou com OS dela no dia, com a jornada do dia e a última
  posição (ou a do início). Atualiza a cada 30 s na tela.
- **Atraso estimado** (estimativa, RN-25), contra a rota publicada mais recente da equipe (a mesma do app): na
  primeira parada que não saiu da rota, em execução ou paralisada compara o término projetado (início real + duração
  prevista, nunca antes de agora) com a saída prevista; a caminho, a chegada projetada (último fim real da equipe, ou
  o início da jornada, + deslocamento previsto, nunca antes de agora) com a chegada prevista. OS cancelada ou passada
  a outra equipe só sai da conta. Até 10 min a tela mostra "No horário".
- **Indicadores** (até 92 dias, padrão 30), pelas OS programadas no período: prazo (RN-21) por serviço; tempo de
  execução medido pelo app como a soma dos trechos ativos (iniciar ou retomar até o evento seguinte, sem as
  paralisações), × o tempo padrão da versão da OS (por unidade de medida, vezes a quantidade executada);
  deslocamento = do último fim (encerrar, devolver, paralisar) na mesma jornada, ou do início dela, até o início de
  cada OS; motivos de paralisação, devolução e não execução; produtividade por equipe com horas de jornada e km pelas
  posições (precisão até 100 m). Os eventos que valem são os aplicados e os em conflito (o campo fez).
- **Sugestão de tempo padrão:** com 10 OS ou mais e mediana de real ÷ padrão fora de ±15%, sugere o padrão atual ×
  mediana (minutos inteiros por OS; duas casas por unidade de medida; entre o mínimo e 1.440). É só informação: o
  gestor muda o catálogo (versão nova) se concordar.
- **Gatilhos (detector agendado, a cada 2 min, trava `detectar-reotimizacao`):** emergencial aberta fora das rotas
  com alguma equipe em jornada que atenda a área; OS de hoje paralisada há mais de 30 min (`aurora.operacao.
  paralisacao-longa`) — interpretamos "motivo longo" pela duração real, sem classificar os motivos de antemão;
  devolução de hoje por equipe que segue em jornada com OS programadas; atraso estimado ≥ 30 min
  (`aurora.operacao.atraso-min`). Cada fato abre uma sugestão em `sugestao_reotimizacao` (V36), identificada por uma
  chave (o evento, a OS e o dia, ou a rota), que impede repetir o mesmo fato. A sugestão aberta expira quando o fato
  deixa de valer; no atraso, só quando cai abaixo da metade do limite (não some e volta a cada oscilação).
- **Confirmação do programador:** nada é recalculado sozinho. Aceitar junta todas as abertas da mesma unidade e dia
  num só pedido de reotimização (equipes afetadas; na emergencial sem equipe, as equipes em jornada que atendem a
  área, e a OS entra como extra) e leva à roteirização, onde ele confere e publica. A sugestão da emergencial fica na
  unidade da equipe mais próxima na hierarquia, a mesma das sugestões dessa equipe. Aceitar e ignorar são auditados
  (`REOTIMIZACAO_SUGERIDA_ACEITA`, `_IGNORADA`). A confirmação pelo agente no app fica para depois: o app já recebe a
  nova ordem pelo ETag da rota quando o programador publica.
- **API pública nova da roteirização:** `Reotimizacoes.pedirReotimizacao(unidade, equipes, extras)`; o
  `POST /roteirizacoes/reotimizar` também aceita `ordens` extras.
- **Correção na publicação (M5):** publicar descartava todas as publicações anteriores da unidade no dia. Uma
  reotimização de parte das equipes tirava a rota das outras no app. Agora só é descartada a publicação anterior
  cujas equipes estão todas na nova; o app de cada equipe segue com a publicada mais recente que a inclui.

**Consequências.**
- Índices novos: OS por data programada, eventos por jornada e hora, jornadas por início.
- Testes: unidade da estimativa e da sugestão de tempo; API de ponta a ponta (mapa, detector com os quatro gatilhos
  e sem repetição, ignorar, aceitar, publicação parcial, indicadores, permissão e período); E2E com axe nos dois
  temas.
- Fora por agora: confirmação pelo agente no app; aviso por notificação além do contador na tela; tempos de
  deslocamento comparados à matriz do Valhalla.
