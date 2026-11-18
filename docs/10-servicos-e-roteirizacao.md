# 10. Serviços, programação, roteirização e app de campo

> **Situação:** aprovada pelo autor em 17/11/2026, com as propostas de 10.14 aceitas como decisão até nova
> orientação. Histórias no doc 02 (épico J), regras no doc 03 (RN-20 a RN-27), modelo no doc 04, pendências no doc 06.
> **M1 (catálogo) entregue — ADR 0042.** Os demais marcos (10.12) seguem na ordem.

## 10.1 Contexto e decisões

Hoje a Aurora tem ordens de serviço só dentro de campanhas de ligação (ADR 0036): quatro tipos fixos no código
(vistoria, ligação nova, religação, notificação), destino sempre um imóvel e um único passo de resultado. Este
módulo transforma a OS em **gestão de serviços da operação de esgoto da PPP**: catálogo configurável, programação
por ligação ou por logradouro, rota otimizada para cada equipe e um app nativo que funciona sem sinal.

| # | Decisão | Escolha do autor |
|---|---|---|
| D1 | Alcance do catálogo | **Operação completa de esgoto** (ligação, religação, vistoria, notificação, manutenção de rede, desobstrução, limpeza de PV, reposição de pavimento…), não só serviços comerciais |
| D2 | Quem cadastra | Novo perfil **Gestor de serviços** |
| D3 | Programação | **Por ligação** (imóvel) **ou por logradouro**; no logradouro, **as duas formas**: uma OS para o trecho ou um lote com uma OS por imóvel |
| D4 | Endereços e logradouros | **Dados da concessionária** (formato a definir, 10.14) |
| D5 | Roteirização | A melhor rota possível para o deslocamento do agente; partida na **posição atual** da equipe |
| D6 | Momento do cálculo | No **"Iniciar jornada"** do app, pela posição do agente; a base da equipe serve só para a prévia do programador |
| D7 | Janelas de horário | **Sem restrição**; só a jornada da equipe limita |
| D8 | "Devolver" | A OS **volta para a programação** com motivo; o programador reprograma, troca a equipe ou cancela |
| D9 | App de campo | **Nativo em Flutter**, projeto separado |
| D10 | Sem sinal | **Obrigatório**: cada ação entra numa **fila local** e é enviada quando houver conexão |
| D11 | Valor em R$ / medição | **Fora por agora**: só quantidade e unidade de medida |

## 10.2 Glossário

| Termo | Significado |
|---|---|
| **Grupo de serviço** | Agrupador do catálogo (ex.: Ligações, Manutenção de rede, Pavimento) |
| **Serviço** | Item do catálogo (ex.: "Desobstrução de ramal"), com unidade de medida, tempo padrão, prazos e parâmetros |
| **Versão do serviço** | Fotografia do serviço numa data; a OS guarda a versão com que nasceu |
| **Parâmetro** | Campo que a equipe preenche ao encerrar (ex.: extensão do ramal em metros, foto depois) |
| **Motivo** | Justificativa configurável de paralisação, devolução ou não execução |
| **Logradouro** | Rua, avenida, travessa… com nome, tipo e bairro, vindo da concessionária |
| **Trecho** | Parte de um logradouro (linha no mapa, de esquina a esquina ou por faixa de numeração) |
| **Alvo da OS** | Onde o serviço acontece: um **imóvel** (matrícula) ou um **trecho** |
| **Lote** | Conjunto de OS por imóvel gerado de uma vez a partir de um logradouro |
| **Programação** | Atribuição de OS a uma equipe numa data |
| **Rota** | Sequência otimizada das OS de uma equipe num dia, com tempos e caminho |
| **Jornada** | Período de trabalho da equipe no dia, aberto e fechado no app |
| **Evento** | Registro de uma ação de campo (iniciar, paralisar…), criado no celular e enviado pela fila |

## 10.3 Perfis e permissões

| Permissão | Libera | Perfis do sistema |
|---|---|---|
| `servico:gerenciar` (nova) | Grupos, serviços, versões, parâmetros, motivos, tipos de veículo, habilidades | **Gestor de serviços**, Administrador |
| `logradouro:gerenciar` (nova) | Importar e corrigir logradouros, trechos e vínculo matrícula → endereço | **Gestor de serviços**, Administrador |
| `os:programar` (nova) | OS avulsa, lote por logradouro, programar, otimizar e publicar rotas, tratar devoluções e conflitos | **Gestor de serviços**, Gestor comercial |
| `campanha:gerenciar` (existe) | Campanhas de ligação (agora usando o catálogo) | Gestor comercial, Administrador |
| `os:executar` (existe) | App de campo: jornada, iniciar, paralisar, retomar, devolver, encerrar | Equipe de campo |
| `dispositivo:gerenciar` (nova) | Ver e revogar celulares cadastrados | Administrador, Gestor de serviços |

O novo perfil **Gestor de serviços** é criado também nos clientes existentes, como foi feito com "Equipe de campo"
na V28. O escopo por unidade (RN-13) continua valendo: cada perfil só enxerga e programa OS das suas unidades.

## 10.4 Histórias de usuário (épico J — Serviços de campo)

### J1 · Catálogo de serviços (P0)
**Como** gestor de serviços, **quero** cadastrar grupos e serviços com unidade de medida, tempo padrão, prazos por
prioridade, tipo de veículo e habilidade exigidos, **para** padronizar o que as equipes executam.
- Serviço tem código único no cliente, nome, grupo, unidade (un, m, m², m³), alvo permitido (imóvel, trecho ou
  ambos), **tempo padrão** (fixo ou por unidade, ex.: 4 min/m de rede), prazo por prioridade, tipo de veículo,
  habilidade, se é conferido pela carga (RN-19) e se está ativo.
- Toda alteração gera nova **versão**; OS existentes não mudam. Inativar não apaga.
- Os quatro tipos atuais (vistoria, ligação nova, religação, notificação) entram como serviços pré-cadastrados.

### J2 · Parâmetros e motivos do serviço (P0)
**Como** gestor de serviços, **quero** definir os campos que a equipe preenche ao encerrar e os motivos aceitos,
**para** coletar o dado certo de cada serviço.
- Tipos de parâmetro: número, decimal com unidade, texto curto, lista (uma ou várias opções), sim/não, data/hora,
  foto (com quantidade mínima e máxima), assinatura fica fora (10.13).
- Por parâmetro: rótulo, ajuda, obrigatório, mínimo/máximo, ordem, e **condição** simples ("só se *Executado* = sim").
- Motivos por tipo (**paralisação**, **devolução**, **não execução**), globais ou por serviço; "Outro" exige
  observação. Os motivos atuais de recusa/inviabilidade viram motivos de não execução.
- Pré-visualização do formulário como aparecerá no app.

### J3 · Logradouros e trechos (P0)
**Como** gestor de serviços, **quero** importar os logradouros e trechos da concessionária e o vínculo de cada
matrícula com logradouro e número, **para** programar por rua e levar o agente à porta certa.
- Importação por arquivo com validação, prévia de erros e relatório (formato em 10.14); reimportar atualiza sem
  duplicar (chave = código da concessionária).
- Tela de consulta com mapa; correção manual auditada.
- Imóvel sem vínculo ou com ponto de precisão "bairro" fica marcado como **localização aproximada** (RN-09).

### J4 · OS avulsa por ligação (P0)
**Como** gestor de serviços, **quero** criar OS de qualquer serviço do catálogo para um ou vários imóveis, com
prioridade e observação, **para** atender demandas fora de campanhas.
- Busca por matrícula, por logradouro + número ou seleção no mapa.
- Prazo calculado pelo serviço e pela prioridade (RN-21); um imóvel não pode ter duas OS abertas **do mesmo serviço**.

### J5 · OS por logradouro: trecho ou lote (P0)
**Como** gestor de serviços, **quero** escolher um logradouro (inteiro, trechos ou faixa de numeração) e gerar
**uma OS para o trecho** ou **um lote com uma OS por imóvel**, **para** programar serviços de rede e ações de rua.
- **Trecho:** serviço com alvo "trecho" (ex.: limpeza de rede); quantidade prevista = extensão em metros quando a
  unidade for metro; o agente percorre o trecho.
- **Lote:** serviço com alvo "imóvel"; prévia de quantos imóveis entram e quantos já têm OS aberta do serviço;
  as OS saem ordenadas pela numeração (lado par e ímpar).

### J6 · Programação e roteirização (P0)
**Como** gestor de serviços, **quero** escolher as OS e as equipes de um dia e pedir a **rota otimizada**, **para**
que cada agente se desloque o mínimo e as urgências sejam atendidas primeiro.
- Resultado: mapa com a rota de cada equipe, sequência, horários estimados, km e horas de deslocamento, e a lista
  das OS que **não couberam** no dia (com o motivo: jornada, habilidade, veículo).
- Ajuste manual (arrastar OS entre equipes ou na sequência; **fixar** OS); recalcular respeita o que foi fixado.
- **Publicar** envia a programação ao app. A rota definitiva é calculada no "Iniciar jornada" (J8).
- Programar sem otimizar também é possível (só equipe e data); a ordem então vem da otimização no app.

### J7 · Reotimização durante o dia (P1)
**Como** gestor de serviços, **quero** que a rota seja recalculada quando entra uma emergência ou uma OS é
paralisada ou devolvida, **para** que a equipe não perca tempo.
- Recalcula a partir da **última posição** de cada equipe; OS em execução ficam fixas.
- O agente vê a nova ordem e confirma; sem sinal, segue a ordem que já tem.

### J8 · Jornada no app (P0)
**Como** agente de campo, **quero** iniciar a jornada e receber no celular a rota do dia, **para** trabalhar mesmo
sem sinal.
- "Iniciar jornada" (com sinal): envia a posição, o servidor otimiza a rota a partir dela e o app baixa o pacote do
  dia (10.9). Sem sinal no início: o app usa a última programação baixada e a rota prévia.
- Mapa com a sequência numerada, lista, distância da próxima parada e botão **Navegar** (abre Waze ou Google Maps).
- "Encerrar jornada" exige não ter OS em execução e mostra o que ainda está na fila para enviar.

### J9 · Executar a OS (P0)
**Como** agente de campo, **quero** iniciar, paralisar, retomar, devolver e encerrar a OS, **para** registrar o que
aconteceu, com ou sem sinal.
- Cada ação registra hora, GPS e, quando exigido, motivo e observação (RN-22).
- Encerrar: **executada** (formulário de parâmetros do serviço, quantidade executada, fotos) ou **não executada**
  (motivo).
- No primeiro encerramento em um imóvel, o GPS da execução atualiza a localização do imóvel (origem `CAMPO`) se a
  atual for aproximada.

### J10 · Fila e sincronização (P0)
**Como** agente de campo, **quero** que tudo o que eu registrar fique guardado e seja enviado sozinho quando houver
conexão, **para** não perder trabalho nem repetir nada.
- Indicador sempre visível: "x ações pendentes · último envio hh:mm"; botão "Enviar agora".
- Fechar o app, reiniciar o celular ou ficar sem bateria não perde a fila.

### J11 · Devoluções e conflitos (P0)
**Como** gestor de serviços, **quero** uma caixa de **devolvidas** e de **conflitos de sincronização**, **para**
decidir rapidamente o que reprogramar.
- Devolvida: motivo, observação, foto opcional, quem e quando; ações: reprogramar (data/equipe), cancelar.
- Conflito (10.9.5): o que o campo fez × o que a programação mudou, com a decisão registrada e auditada.

### J12 · Acompanhamento e indicadores (P1)
**Como** gestor, **quero** ver em tempo quase real a posição das equipes e o andamento das rotas, e indicadores de
prazo, tempo e produtividade, **para** gerir a operação e o contrato.
- Mapa do dia: equipes (última posição e hora), OS por situação, atraso estimado.
- Indicadores: OS no prazo × atrasadas por serviço, tempo médio de deslocamento e de execução × tempo padrão,
  paralisações e devoluções por motivo, produtividade por equipe, km por OS.
- O tempo real medido alimenta a sugestão de ajuste do tempo padrão do serviço.

### J13 · Celulares e segurança do app (P0)
**Como** administrador, **quero** ver os celulares em uso e revogar um aparelho perdido, **para** proteger os dados.
- Cadastro do aparelho no primeiro login; revogar bloqueia o acesso e apaga os dados locais na próxima conexão.

## 10.5 Catálogo inicial sugerido

Base para revisão; o gestor ajusta no cliente. Tempos são estimativas iniciais.

| Grupo | Serviço | Alvo | Unid. | Tempo padrão | Parâmetros principais | Carga |
|---|---|---|---|---|---|---|
| Ligações | Vistoria de viabilidade | Imóvel | un | 20 min | Viável (s/n), cota abaixo da rede (s/n), distância até a rede (m), foto | — |
| Ligações | Ligação nova de esgoto | Imóvel | un | 120 min | Extensão do ramal (m), diâmetro (mm), profundidade (m), caixa de inspeção instalada (s/n), fotos antes/depois | Sim |
| Ligações | Religação | Imóvel | un | 40 min | Tipo de religação (lista), foto | Sim |
| Ligações | Notificação | Imóvel | un | 10 min | Entregue a (lista: morador, vizinho, não entregue), foto do aviso | — |
| Manutenção de rede | Desobstrução de ramal | Imóvel | un | 60 min | Causa (lista: gordura, raiz, resíduo…), equipamento (lista), foto | — |
| Manutenção de rede | Desobstrução de rede coletora | Trecho | m | 3 min/m | Extensão desobstruída (m), causa, equipamento (hidrojato…), fotos | — |
| Manutenção de rede | Limpeza de rede | Trecho | m | 2 min/m | Extensão limpa (m), volume retirado (m³), fotos | — |
| Manutenção de rede | Limpeza de PV | Trecho | un | 30 min | Quantidade de PVs, estado da tampa (lista), foto | — |
| Manutenção de rede | Reparo de rede | Trecho | m | 180 min | Extensão (m), diâmetro (mm), material (lista), fotos | — |
| Manutenção de rede | Inspeção de rede (CFTV) | Trecho | m | 2 min/m | Extensão inspecionada (m), anomalias (lista múltipla), arquivo de vídeo fora (10.13) | — |
| Pavimento | Reposição de pavimento | Imóvel ou trecho | m² | 15 min/m² | Área (m²), tipo (lista: asfalto, bloquete, concreto, calçada), fotos | — |
| Comercial | Verificação de lançamento irregular | Imóvel | un | 30 min | Irregularidade (lista), teste com corante (s/n), foto | — |

**Tipos de veículo sugeridos:** moto, carro leve, caminhonete, caminhão hidrojato, caminhão de vácuo (sucção).
**Motivos de paralisação sugeridos:** chuva, falta de material, aguardando outra equipe, trânsito interditado,
acesso negado temporariamente, intervalo, quebra de equipamento, outro.
**Motivos de devolução sugeridos:** endereço não encontrado, serviço de outra equipe/veículo, falta de material,
área de risco, imóvel fechado, serviço já executado, outro.

## 10.6 Ciclo de vida da OS

```
                   programar                 iniciar
  PLANEJADA ───────────────────▶ PROGRAMADA ─────────▶ EM_EXECUÇÃO ──── encerrar ───▶ ENCERRADA
     ▲  │                           │   ▲                │     ▲                   (executada | não executada)
     │  │ cancelar                  │   │ reprogramar      │     │ retomar
     │  ▼                           │   │                  ▼     │
     │ CANCELADA ◀── cancelar ──────┤   │              PARALISADA
     │                              │   │                  │
     │                     devolver │   │                  │ devolver
     │                              ▼   │                  ▼
     └──────── reprogramar ───── DEVOLVIDA ◀───────────────┘
```

| De | Ação | Para | Quem | Exige |
|---|---|---|---|---|
| — | criar | PLANEJADA | `os:programar` / `campanha:gerenciar` | serviço, alvo, prioridade |
| PLANEJADA | programar | PROGRAMADA | `os:programar` | equipe compatível (RN-23), data |
| PROGRAMADA | reprogramar | PROGRAMADA | `os:programar` | nova equipe e/ou data |
| PROGRAMADA | iniciar | EM_EXECUÇÃO | `os:executar` (equipe da OS) | jornada aberta, GPS |
| EM_EXECUÇÃO | paralisar | PARALISADA | `os:executar` | motivo de paralisação |
| PARALISADA | retomar | EM_EXECUÇÃO | `os:executar` | — |
| PROGRAMADA, EM_EXECUÇÃO, PARALISADA | devolver | DEVOLVIDA | `os:executar` | motivo de devolução |
| DEVOLVIDA | reprogramar | PROGRAMADA | `os:programar` | equipe e data |
| EM_EXECUÇÃO | encerrar | ENCERRADA | `os:executar` | resultado + parâmetros obrigatórios ou motivo |
| PLANEJADA, PROGRAMADA, DEVOLVIDA | cancelar | CANCELADA | `os:programar` | motivo |

- PARALISADA **não** encerra direto: precisa retomar (o tempo de paralisação fica separado do de execução).
- Uma OS só fica EM_EXECUÇÃO para uma equipe por vez; a equipe pode ter **uma** OS em execução por vez (a outra
  precisa ser paralisada antes — evita tempo contado em dobro).
- Execução parcial (parte do trecho): encerra como executada com a quantidade feita; o gestor cria nova OS do
  restante (a tela sugere).
- Mapeamento do que existe hoje (V28): `EM_CAMPO` → PROGRAMADA; `REAGENDADA` → PROGRAMADA com nova data;
  `EXECUTADA` → ENCERRADA/executada; `RECUSADA` e `INVIAVEL` → ENCERRADA/não executada com o motivo atual.
  A conferência pela carga (RN-19) continua igual para serviços marcados como "conferidos pela carga".

## 10.7 Regras de negócio (propostas RN-20 a RN-27)

**RN-20 · Catálogo e versões.** Código do serviço único por cliente. Alterar serviço, parâmetro ou motivo cria nova
versão; a OS guarda a versão de criação e o app usa os parâmetros dessa versão. Serviço inativo não aceita OS nova,
mas as abertas seguem.

**RN-21 · Prazo (SLA).** Prazo = criação + prazo do serviço para a prioridade (emergencial, alta, média, baixa), em
horas corridas para emergencial e em dias úteis para as demais (calendário de feriados por unidade — 10.14).
Atrasada = aberta após o prazo. Paralisação **não** suspende o prazo (o contrato mede o atendimento).

**RN-22 · Ações de campo.** Toda ação grava hora do celular, hora de recebimento no servidor, GPS (com precisão) e
usuário. Paralisar e devolver exigem motivo; "Outro" exige observação (até 500 caracteres). Encerrar exige os
parâmetros obrigatórios da versão, a quantidade executada (> 0 se executada) e as fotos mínimas. Iniciar a mais de
**300 m** do alvo é permitido, mas marcado como "início fora do local" (valor configurável por cliente).

**RN-23 · Compatibilidade de equipe.** Uma OS só pode ir para equipe que atua na unidade do alvo (ou acima, como
RN-19), tem a **habilidade** exigida pelo serviço e um **veículo** do tipo exigido. A otimização nunca quebra isso.

**RN-24 · Programação por logradouro.** OS de trecho só para serviços com alvo "trecho". Lote gera uma OS por imóvel
vinculado ao logradouro/faixa, ignorando (e informando) os que já têm OS aberta do mesmo serviço; máximo de 5.000
OS por lote (mesmo limite das campanhas).

**RN-25 · Roteirização.** Rota de um dia por equipe, partindo da última posição do agente no "Iniciar jornada" (ou
da base da equipe na prévia). Regras obrigatórias: RN-23, fim estimado dentro da jornada, OS fixadas mantidas.
Objetivo, nesta ordem: (1) emergenciais primeiro; (2) menos OS que estouram o prazo; (3) mais OS de prioridade
alta no dia; (4) menor tempo total de deslocamento; (5) carga equilibrada entre equipes. Duração da parada = tempo
padrão do serviço (× quantidade prevista quando é por unidade). Resultado sempre rotulado como **estimativa**.

**RN-26 · Fila e sincronização.** Cada evento tem identificador gerado no celular; o servidor aplica cada um **uma
única vez** e na ordem de sequência da OS. Relógio do celular com diferença maior que 5 min do servidor gera
aviso no evento (a hora do celular é a usada nos indicadores; a do servidor fica para auditoria). O que foi feito
em campo nunca é descartado (10.9.5).

**RN-27 · Localização da equipe.** Posição enviada **só com a jornada aberta**, para roteirizar e acompanhar a
execução; o agente vê no app quando está sendo coletada. Guarda de **90 dias** (proposta, 10.14), depois apagada;
indicadores agregados permanecem. Acesso pela gestão do escopo da equipe, auditado.

## 10.8 Roteirização — arquitetura

```
 Programador (web) ──┐                        ┌──────────────── Valhalla (MIT) ────────────────┐
                     │  otimizar / publicar   │  mapas OpenStreetMap da região, perfis carro,   │
 App (Iniciar        ├──────▶ Back-end ───────┤  moto e caminhão (altura, peso)                 │
 jornada, posição) ──┘   módulo roteirizacao  │  · matriz de tempos/distâncias (muitos × muitos)│
                          │                   │  · geometria da rota para o mapa                │
                          ▼                   └─────────────────────────────────────────────────┘
                    Timefold Solver (Apache 2.0, no back-end Java)
                    · decide equipe e ordem de cada OS (RN-25)
                    · limite de tempo por cálculo (ex.: 30 s na programação, 10 s na reotimização)
```

- **Valhalla** roda como contêiner próprio na infraestrutura do cliente, com o recorte do OSM da área da
  concessão (atualização mensal). Sem Google Maps: sem custo por chamada e nenhum endereço sai do ambiente.
  Perfil de caminhão importa para hidrojato e vácuo (vias estreitas, limites de peso/altura quando o OSM tiver).
- **Timefold Solver** (modelo de roteamento de veículos): equipes = veículos com início na posição atual e fim em
  aberto; OS = paradas com duração, habilidade e veículo exigidos. Restrições duras e flexíveis da RN-25.
- OS de **trecho**: a parada é o início do trecho; a duração inclui o percurso dele; o fim do trecho é o ponto de
  saída para a próxima.
- **Cache** da matriz por par de pontos e perfil, para a reotimização ficar rápida.
- **Gatilhos de reotimização (J7):** OS emergencial criada na área, OS paralisada por motivo longo, devolvida,
  equipe atrasada além de 30 min do previsto. Sempre com confirmação do programador ou do agente.
- **Escala de referência para dimensionar e testar:** 50 equipes × 1.500 OS no dia num cliente (a confirmar,
  10.14). Matriz grande é calculada em blocos.
- **Navegação:** o app não faz navegação curva a curva; mostra o mapa e entrega a próxima parada ao Waze ou
  Google Maps instalados (só coordenadas, sem dados do cliente).

## 10.9 App de campo (Flutter) e funcionamento sem sinal

### 10.9.1 Telas
1. Login (Keycloak, com PKCE) → cadastro do aparelho → PIN/biometria.
2. **Hoje:** iniciar jornada; mapa + lista na ordem da rota; indicador da fila.
3. **OS:** dados do serviço e do alvo, observações da programação, histórico de eventos, ações permitidas pelo
   estado (10.6).
4. **Encerrar:** formulário montado pelos parâmetros da versão do serviço; câmera; validação local das regras.
5. **Fila:** pendentes, erros e conflitos devolvidos pelo servidor.
6. **Encerrar jornada.**

Visual Material 3 com as cores e a tipografia da Aurora (doc 07); tela legível ao sol e com botões grandes
(uso com luva).

### 10.9.2 Pacote do dia (baixado no "Iniciar jornada")
OS programadas da equipe com alvo, endereço, ponto e observações; versões dos serviços com parâmetros e motivos;
rota com geometria; mapa de fundo da área do dia (10.14: fonte de mapa offline). O pacote tem versão; atualizações
durante o dia chegam como diferença.

### 10.9.3 Fila local (outbox)
- Banco local **SQLite criptografado**; cada ação vira um evento **antes** de mudar a tela.
- Evento: `id` (UUID do celular), `os_id`, `sequencia` (por OS), `tipo`, `motivo_id`, `observacao`, `valores`,
  `ocorrido_em` (relógio do celular), `posicao` (lat, long, precisão), `versao_pacote`.
- Envio em lote, em ordem, em segundo plano, com novas tentativas em intervalos crescentes e ao recuperar sinal.
- **Fotos:** comprimidas no celular (ex.: lado maior 1.600 px), enviadas depois dos eventos, em partes, retomando de
  onde parou; o evento referencia a foto pelo identificador.
- Posições da jornada entram na mesma fila (amostra a cada 2 min ou 100 m; menos em bateria baixa).

### 10.9.4 Servidor
- `GET  /api/v1/campo/jornada` — pacote do dia (ou diferença a partir de uma versão).
- `POST /api/v1/campo/jornada/inicio` — posição inicial; dispara a otimização (D6).
- `POST /api/v1/campo/eventos` — lote de eventos; resposta por evento: aplicado, já aplicado (repetido), conflito
  ou rejeitado (com motivo).
- `PUT  /api/v1/campo/anexos/{id}` — envio de foto em partes.
- `POST /api/v1/campo/posicoes` — posições em lote.
- Idempotência pelo `id` do evento; sequência fora de ordem fica aguardando o anterior.

### 10.9.5 Conflitos (programação mudou enquanto o agente estava sem sinal)

| Situação no servidor | O campo já… | Resultado |
|---|---|---|
| OS cancelada ou passada para outra equipe | tinha iniciado ou encerrado | **Vale o campo**: eventos aplicados, OS fica com a equipe que executou, conflito aberto para o programador (J11) |
| OS cancelada ou passada para outra equipe | não tinha iniciado | Vale a programação; o app remove a OS e avisa o agente |
| OS encerrada por outra equipe | também encerrou | As duas execuções ficam registradas; conflito para o programador |
| Versão do serviço mudou | preencheu com a versão antiga | Aceita com a versão antiga (é a da OS) |

### 10.9.6 Segurança do aparelho
- Banco local criptografado com chave no Keystore/Keychain; desbloqueio por PIN ou biometria.
- Sessão sem rede válida por até **7 dias** (proposta, 10.14); depois exige login com sinal.
- Aparelho revogado (J13): acesso negado e dados locais apagados na próxima conexão; a fila pendente é enviada
  antes de apagar quando possível e marcada como "de aparelho revogado".
- Só dados das OS da equipe no dia ficam no celular; pacote antigo é apagado ao encerrar a jornada.
- Pacotes Flutter a confirmar (licença verificada antes de usar, como no front): banco local criptografado,
  tarefas em segundo plano, localização, câmera, mapa MapLibre, login OIDC, biometria.

## 10.10 Modelo de dados (proposta)

Todas as tabelas com `tenant_id`, RLS por escopo de unidade (como V28) e auditoria.

| Tabela | Campos principais |
|---|---|
| `grupo_servico` | id, codigo, nome, ativo |
| `servico` | id, grupo_id, codigo, ativo, versao_atual |
| `servico_versao` | id, servico_id, versao, nome, unidade_medida, alvo (IMOVEL/TRECHO/AMBOS), tempo_padrao_min, tempo_por_unidade (bool), prazos (jsonb por prioridade), tipo_veiculo_id, habilidade_id, conferido_pela_carga, criada_em, criada_por |
| `parametro_servico` | id, servico_versao_id, chave, rotulo, tipo, obrigatorio, minimo, maximo, opcoes (jsonb), condicao (jsonb), ordem |
| `motivo` | id, tipo (PARALISACAO/DEVOLUCAO/NAO_EXECUCAO/CANCELAMENTO), codigo, nome, exige_observacao, servico_id (nulo = global), ativo |
| `tipo_veiculo`, `habilidade` | id, codigo, nome, perfil de rota (carro, moto, caminhão) |
| `equipe` (alterada) | + base (Point), jornada_inicio, jornada_fim, tipo_veiculo_id; `equipe_habilidade` |
| `logradouro` | id, codigo_concessionaria, tipo, nome, bairro, unidade_id |
| `trecho` | id, logradouro_id, codigo_concessionaria, geom (LineString 4674), numero_inicial, numero_final, lado |
| `imovel_endereco` | codigo_origem, logradouro_id, trecho_id, numero, complemento — acesso restrito (10.11) |
| `lote_os` | id, logradouro_id, faixa, servico_versao_id, criado_por |
| `ordem_servico` (alterada) | + servico_versao_id, alvo_tipo, trecho_id, lote_id; campanha_id e codigo_origem passam a opcionais; data_programada, sequencia_rota, quantidade_prevista, quantidade_executada, resultado (EXECUTADA/NAO_EXECUTADA), motivo_id; novos estados (10.6) |
| `os_evento` | id (do celular), os_id, sequencia, tipo, motivo_id, observacao, ocorrido_em, recebido_em, posicao (Point), precisao_m, dispositivo_id, usuario_id, aviso_relogio |
| `os_valor_parametro` | os_id, parametro_id, valor (jsonb) |
| `os_anexo` | id, os_id, parametro_id, tipo, tamanho, hash, armazenamento, verificado_antivirus (ADR 0033) |
| `os_conflito` | id, os_id, descricao, decisao, decidido_por, decidido_em |
| `rota` | id, equipe_id, data, versao, origem (PREVIA/JORNADA/REOTIMIZACAO), km, minutos_deslocamento, geom (MultiLineString), calculada_em |
| `rota_parada` | rota_id, os_id, ordem, chegada_prevista, saida_prevista, fixada |
| `jornada` | id, equipe_id, usuario_id, dispositivo_id, inicio, fim, posicao_inicio |
| `posicao_equipe` | jornada_id, momento, posicao, precisao_m — particionada por mês, apagada após o prazo (RN-27) |
| `dispositivo` | id, usuario_id, modelo, sistema, versao_app, cadastrado_em, ultimo_contato, revogado_em |

Migração dos dados da V28: serviços pré-cadastrados (J1), OS existentes ganham `servico_versao_id` e os estados
mapeados (10.6).

## 10.11 Privacidade (LGPD) — conflito com a RN-09

A RN-09 diz que **o endereço não sai da concessionária** e a Aurora guarda só matrícula + ponto. Programar por
logradouro e levar o agente à porta exige logradouro e número na Aurora. Proposta (a aprovar, 10.14):

- **Logradouros e trechos** não são dados pessoais: podem ser importados inteiros.
- **Vínculo matrícula → número** fica em `imovel_endereco`, com acesso só por `os:programar`, `os:executar` (só das
  OS da equipe) e `logradouro:gerenciar`, consulta auditada, e **nunca** com nome do morador.
- Alternativa mais restrita: o agente da concessionária envia o número só dos imóveis que têm OS aberta.
- Localização do funcionário (RN-27) e dados no celular (10.9.6) entram no **RIPD**, que já está pendente.

## 10.12 Marcos de entrega

Cada marco: branch própria, ADR, testes (unidade, API, E2E, axe nas telas web) e revisão do autor antes do merge.

| # | Marco | Conteúdo | Depende de |
|---|---|---|---|
| M1 | Catálogo ✅ ADR 0042 | J1, J2, perfil Gestor de serviços, telas de cadastro, serviços pré-cadastrados | — |
| M2 | Logradouros ✅ ADR 0043 | J3, importação (após definir formato), mapa de trechos, vínculo de imóveis | formato (10.14) |
| M3 | OS genérica ✅ ADR 0044 | J4, J5, migração da V28, campanhas usando o catálogo, estados novos | M1, M2 |
| M4 | Execução na API ✅ ADR 0045 | Eventos, fila idempotente, conflitos, devoluções (J11), anexos, jornada, aparelhos (J13) | M3 |
| M5 | Roteirização | Valhalla no compose, Timefold, J6, J7 | M3 |
| M6 | App Flutter | J8, J9, J10, fila offline, segurança do aparelho, publicação interna | M4, M5 |
| M7 | Indicadores | J12, mapa do dia | M4–M6 |

## 10.13 Fora do escopo (por agora)

Valor em R$ e boletim de medição (D11); janelas de horário (D7); navegação curva a curva dentro do app; assinatura
do morador; vídeo (CFTV) e arquivos grandes; OS criada pelo agente em campo; materiais e estoque; integração das OS
com o sistema comercial; versão iOS publicada em loja (o código Flutter já nasce compatível).

## 10.14 Pendências desta especificação

| # | Pergunta | Proposta |
|---|---|---|
| P1 | Formato dos arquivos da concessionária (logradouros, trechos, matrícula → endereço) | GeoPackage para logradouros/trechos e CSV (`matricula;codigo_logradouro;numero;complemento`) para o vínculo, até a concessionária definir. **No M2 os trechos vieram em GeoJSON** (texto lido em fluxo, sem GDAL; ADR 0043) |
| P2 | Endereço na Aurora × RN-09 (10.11) | Importar logradouros inteiros e o número com acesso restrito e auditado |
| P3 | Mapa de fundo offline no app (os servidores públicos do OSM não permitem baixar áreas) | Gerar tiles próprios da área da concessão (arquivo PMTiles servido pela Aurora) |
| P4 | Guarda das posições das equipes | 90 dias |
| P5 | Sessão do app sem rede | 7 dias |
| P6 | Calendário de feriados para prazo em dias úteis | Feriados nacionais automáticos + municipais cadastrados por unidade |
| P7 | Volume esperado (equipes e OS por dia por cliente) | 50 equipes × 1.500 OS/dia como referência de teste |
| P8 | Distância para "início fora do local" | 300 m, configurável |
| P9 | Android apenas ou também iOS? | Android primeiro (aparelhos corporativos), iOS depois |
| P10 | Aparelhos gerenciados (MDM) ou celular pessoal do agente? | ✅ **Celular da empresa** (18/11/2026): o app pode exigir bloqueio de tela, impedir captura de tela e apagar tudo ao bloquear o celular, sem preocupação com dados pessoais do agente no aparelho |
