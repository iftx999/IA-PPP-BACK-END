# 0044 · OS genérica do catálogo e programação por imóvel, trecho e logradouro

**Contexto.** O terceiro marco dos serviços de campo (doc 10, M3, histórias J4 e J5) tira a OS dos quatro tipos fixos
da V28 (vistoria, ligação nova, religação, notificação), usados só pelas campanhas, e a liga ao catálogo do M1
(ADR 0042): qualquer serviço, num imóvel ou num trecho de rede (ADR 0043), criada avulsa, por lote de logradouro ou
por campanha, com o ciclo de vida do doc 10 (10.6) e o prazo da RN-21. Migração V33 (V29 e V30 em outras branches).

**Decisão.**
- **OS do catálogo** (V33): a `ordem_servico` guarda o serviço e a **versão** com que nasceu (RN-20), o alvo
  (`IMOVEL` com matrícula ou `TRECHO`), o lote de origem, a quantidade prevista e a executada, as instruções de quem
  criou, a data programada, o resultado (`EXECUTADA`/`NAO_EXECUTADA`) e o motivo do catálogo. `campanha_id`, matrícula
  e situação na criação passam a opcionais. **Numeração única no cliente** (`contador_os`, reservada na mesma
  instrução do `INSERT`, pode ter lacunas); antes era por campanha.
- **Ciclo** `PLANEJADA → PROGRAMADA → EM_EXECUCAO ⇄ PARALISADA → ENCERRADA`, com `DEVOLVIDA` (volta para a programação,
  D8) e `CANCELADA`. Neste marco entram criar, programar/reprogramar, devolver, cancelar e encerrar; iniciar, paralisar
  e retomar chegam com o app e os eventos (M4). Encerrar aceita OS programada (registro posterior pela web ou pela
  empreiteira) ou em execução; paralisada precisa ser retomada antes. Cancelar só antes de a equipe começar.
- **Um alvo não tem duas OS abertas do mesmo serviço** (J4, RN-24): índices únicos parciais por imóvel e por trecho;
  antes era uma OS aberta por imóvel, de qualquer tipo. A criação em lote informa quem ficou de fora e por quê.
- **Prazo (RN-21)** com hora (`prazo_em`): emergencial em horas corridas; alta, média e baixa no **fim do dia útil**
  (fuso da operação) contado sem sábados, domingos, feriados nacionais (calculados, com a Sexta-feira Santa e a
  Consciência Negra desde 2024) e os **feriados cadastrados por unidade**, que valem para ela e as de baixo (P6;
  `/api/v1/catalogo/feriados`, auditado). Carnaval e Corpus Christi são pontos facultativos: entram como feriado
  municipal quando decretados. Campanhas mantêm o prazo único escolhido pelo gestor.
- **Criação** (`/api/v1/ordens`, permissão nova `os:programar`): avulsa para até 500 matrículas (unidade e situação
  da competência mais recente); de **trecho** para serviços com alvo trecho, com quantidade prevista = extensão quando
  a unidade é metro; **lote por logradouro** (inteiro ou faixa de numeração, lado par/ímpar/ambos), com prévia, até
  5.000 OS, gerado na ordem da numeração (pares, ímpares, sem número), registrado em `lote_os`.
- **Programação** para uma equipe numa data (hoje a 180 dias), para OS planejadas, programadas ou devolvidas; só as
  de alvo dentro da unidade onde a equipe atua (RN-23, parte de área). A resposta separa as fora da área e as que não
  estavam em situação de programar. A distribuição da campanha passa a usar o mesmo caminho e pede a data.
- **Campanhas** geram OS dos serviços `LIGACAO_NOVA`, `RELIGACAO`, `VISTORIA` e `NOTIFICACAO` do catálogo (ativos);
  a conferência pela carga (RN-19) vale para serviços marcados "conferido pela carga" e encerrados como executados.
  Encerrar ou cancelar a campanha cancela só as OS que não começaram; as em execução seguem (vale o campo). Funil,
  produtividade e motivos trocam recusada/inviável/reagendada por não executada e devolvida.
- **Motivos do catálogo** (RN-22) em devolução, cancelamento e não execução: do tipo certo, ativos, do serviço ou
  gerais; "exige observação" obriga a observação (até 500 caracteres). Os motivos fixos da V28 viram os do catálogo
  com o mesmo código.
- **Migração das OS existentes** (doc 10, 10.6): `EM_CAMPO` → programada no dia do envio; `REAGENDADA` → programada
  na nova data; `EXECUTADA` → encerrada/executada (quantidade 1); `RECUSADA` e `INVIAVEL` → encerrada/não executada
  com o motivo do catálogo; prazo antigo → 23:59:59 do dia; renumeração por cliente na ordem de criação. O RLS forçado
  é suspenso só na transação, inclusive para validar as chaves estrangeiras novas (`MigracaoDoCatalogoTest`).
- **Permissão** `os:programar` para Gestor de serviços, Gestor comercial e Administrador (clientes existentes pela
  V33, novos pelo provisionamento). Quem programa lista as equipes ativas por `/api/v1/ordens/equipes`; o cadastro de
  equipes continua em `campanha:gerenciar`. "Minhas OS" ganha a devolução e os motivos.
- **Dados pessoais:** matrícula, número e complemento do imóvel só com `dados_pessoais:ler`, com a exibição auditada;
  OS de trecho não têm dado pessoal. Auditoria: `ORDENS_CRIADAS`, `ORDENS_PROGRAMADAS`, `ORDEM_DEVOLVIDA`,
  `ORDEM_CANCELADA`, `FERIADO_CADASTRADO`, `FERIADO_REMOVIDO`.

**Consequências.** A API quebra contratos de campanhas e "Minhas OS" (`tipo` → serviço, estados novos, `motivoId` no
lugar do enum, `data` na distribuição); o front da mesma branch acompanha. Ficam para os próximos marcos: habilidade e
veículo da equipe na compatibilidade (RN-23, com a roteirização no M5), parâmetros e fotos obrigatórios no
encerramento e o histórico de eventos da OS (M4), a OS do restante numa execução parcial e a sequência de rota.
