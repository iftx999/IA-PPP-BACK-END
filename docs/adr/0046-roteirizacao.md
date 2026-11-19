# 0046 · Roteirização: Valhalla para a malha viária, Timefold para a ordem e a equipe de cada OS

**Contexto.** O quinto marco dos serviços de campo (doc 10, M5; histórias J6 e J7, RN-23 e RN-25) calcula a rota do
dia de cada equipe: qual equipe faz cada OS, em que ordem, com horários, km e deslocamento, e quais OS não couberam.
Sem custo por consulta e sem endereço saindo do ambiente do cliente (10.8). Migração V35.

**Decisão.**
- **Malha viária: Valhalla 3.9.3** (MIT), contêiner próprio (`infra/compose.yaml`, imagem fixada pelo digest), com o
  recorte do OpenStreetMap da área da concessão (`infra/scripts/recortar-osm.sh`, osmium num contêiner descartável).
  Perfis: moto → `motorcycle`, carro → `auto`, caminhão → `truck`. A matriz é pedida em blocos de 50 × 50 (limite
  padrão do Valhalla), até 4 em paralelo, e cada par fica num **cache** em memória (200 mil pares, ~1 m de precisão) que
  a reotimização reaproveita. Encaixe na via com raio de 150 m e `minimum_reachability` 50, para não cair em trecho
  isolado. Chamadas sob o disjuntor `rotas` (ADR 0030).
- **Reserva em linha reta.** Sem `AURORA_VALHALLA_URL`, com o disjuntor aberto ou com falha num bloco, o deslocamento
  é a distância em linha reta × 1,35 a 30 (moto), 27 (carro) ou 20 km/h (caminhão), e a roteirização sai com
  `calculo = LINHA_RETA` (a tela avisa). Par sem caminho na malha também é estimado assim. O cálculo nunca falha por
  causa do serviço de rotas.
- **Otimização: Timefold Solver 2.8.0** (Apache 2.0), só o núcleo, sem o starter do Spring. Modelo de roteamento de
  veículos com variável de lista: cada equipe é uma rota que parte da base (prévia) ou da última posição do agente
  (jornada e reotimização) e termina em aberto; cada OS é uma parada com chegada (imóvel ou início do trecho), saída
  (o mesmo ponto ou o fim do trecho) e duração = tempo padrão × quantidade prevista quando o serviço é por unidade.
  A pausa da equipe entra uma vez, na primeira parada a partir do meio da jornada. Pontuação dura/média/flexível:
  - **dura:** equipe incompatível (RN-23) e rota além do fim da jornada;
  - **média, na ordem da RN-25:** emergencial sem rota (1.000.000); OS que estoura o prazo, alocada chegando depois
    dele ou sem rota vencendo no dia (10.000); alta, média e baixa sem rota (100, 10, 1);
  - **flexível:** minutos até a chegada nas emergenciais (× 1.000); segundos de deslocamento; desequilíbrio de carga
    entre as equipes (`loadBalance`, × 10).
  Tempo por cálculo: 30 s na prévia e 10 s na reotimização, parando antes se ficar 5 s sem melhorar (configurável).
  No máximo 2 cálculos simultâneos por instância (CPU).
- **RN-23 por inteiro.** A equipe ganha base (ponto no mapa), início e fim da jornada, pausa, tipo de veículo e
  habilidades. A OS só vai para equipe da área, com a habilidade exigida e com veículo que atende: **moto exigida é
  atendida por qualquer veículo; carro, pelo mesmo tipo ou por caminhão; caminhão, só pelo mesmo tipo** (função SQL
  `veiculo_atende`, usada na programação manual, na roteirização e na publicação; confirmada pelo usuário em
  19/11/2026). Equipe sem veículo só recebe
  serviço sem veículo exigido. Equipes existentes recebem todas as habilidades ativas na migração (para não parar a
  programação de quem já usa) e ficam sem veículo até o gestor cadastrar.
- **Fluxo (J6).** `POST /api/v1/roteirizacoes` (equipes e OS escolhidas, `os:programar`) responde 202 com a
  roteirização em `CALCULANDO`; o cálculo roda em segundo plano no contexto de acesso de quem pediu (RLS vale) e grava
  `PRONTA` (rotas, paradas, OS sem rota com o motivo: `JORNADA`, `HABILIDADE`, `VEICULO`, `AREA`, `SEM_LOCAL`) ou
  `FALHOU`. Um cálculo por vez por unidade e dia (índice único); cálculo esquecido há 15 min (API reiniciada) é
  liberado. **Ajuste manual** = recalcular com OS fixadas: as fixadas ficam no começo da rota da equipe, na ordem
  dada (`@PlanningPinToIndex`); arrastar uma OS para outra equipe é fixá-la lá. **Publicar** programa as OS de cada rota
  para a equipe na data, na ordem (nova coluna `ordem_servico.sequencia_rota`, que ordena "Minhas OS" e o pacote do
  app), revalidando situação, área e RN-23; a publicação anterior do mesmo dia e unidade fica `DESCARTADA`. Nada muda
  nas OS antes de publicar. Pedido, publicação e descarte são auditados.
- **Reotimização (J7).** `POST /api/v1/roteirizacoes/reotimizar`: as OS abertas de hoje das equipes, a partir da última
  posição (RN-27) e de agora; OS em execução ficam fixas no começo. Confirmação pelo programador (publicar). Os
  gatilhos automáticos (emergencial nova, paralisação longa, atraso de 30 min) e o cálculo no "Iniciar jornada" do app
  ficam para o M6, quando o app existir.
- **Limites:** 50 equipes e 1.500 OS por cálculo (escala de referência do 10.8), data entre hoje e 30 dias.

**Consequências.** A Aurora passa a depender de um contêiner a mais (Valhalla, ~0,2 GB de RAM para o recorte de teste;
uma concessão inteira pede mais e a geração dos tiles leva de minutos a horas) e de atualizar o recorte do mapa
(mensal, 10.8). Sem ele tudo funciona, com estimativa grosseira e aviso. O cálculo é estimativa (RN-25): o trânsito
real não entra. A matriz cresce com o quadrado das OS: 1.500 OS são ~2,4 milhões de pares por perfil de veículo; o
cache e os blocos em paralelo amortecem, e a escala real precisa de medição no cliente (10.14).
