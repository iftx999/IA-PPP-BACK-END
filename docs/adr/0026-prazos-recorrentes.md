# 0026 · Prazos e obrigações recorrentes: agenda no item aprovado e cumprimento por vencimento

**Contexto.** O ADR 0024 acompanhava só itens `PRAZO` com `data_limite`, com um único "cumprido". Nos contratos
lidos, o que se repete são **obrigações** ("relatório trimestral", "demonstrações anuais", "revisão quadrienal"),
e nenhuma delas tem data: a IA registra só o texto da periodicidade. Em multas e índices ("multa diária", "reajuste
anual") a periodicidade descreve um cálculo, não um vencimento. Itens aprovados não podem ser editados (o conteúdo
aprovado é a referência do contrato).

**Decisão.**
- **Agenda no item aprovado**, separada do conteúdo: `primeiro_vencimento` (nulo = a data limite aprovada) e
  `recorrencia` (`MENSAL`, `BIMESTRAL`, `TRIMESTRAL`, `QUADRIMESTRAL`, `SEMESTRAL`, `ANUAL`, `QUADRIENAL`,
  `QUINQUENAL`). Vale para `PRAZO` e `OBRIGACAO` aprovados (`PUT /contratos/itens/{id}/agenda`,
  `contrato:gerenciar`, auditado como `AGENDA_DE_PRAZO_DEFINIDA`). O texto livre da periodicidade continua guardado;
  a API devolve uma `recorrenciaSugerida` a partir dele ("mensalmente", "a cada 4 anos"...) para a tela preencher.
- **Vencimentos:** o primeiro mais N períodos, sempre contados a partir do primeiro (31/01 → 28/02 → 31/03), sem
  passar do fim da vigência do contrato. São cumpridos em ordem: o próximo é o primeiro vencimento depois do último
  cumprido. Obrigação aprovada com data limite passa a ser acompanhada como o prazo.
- **Cumprimento por vencimento** (`item_contratual_cumprimento`, V22, com quem e quando): `POST .../cumprimento`
  cumpre o vencimento em aberto e devolve a agenda com o próximo; `DELETE` desfaz o último. Os cumprimentos da V20
  foram migrados (a cópia suspende o RLS forçado só para o dono, dentro da transação da migração).
- **Alertas:** item recorrente tem um alerta por vencimento (`PRAZO:<item>:<data>`), então cada vencimento gera seu
  aviso e e-mail; o de vencimento único mantém `PRAZO:<item>`. A antecedência é a do ADR 0024 (60 dias, crítico a
  15), limitada a metade e um quarto do período: mensal avisa 15 dias antes (crítico a 7) e não fica em alerta o mês
  inteiro.
- **Tela:** seção "Vencimentos" no item (próximo vencimento com a situação, repetição, histórico, definir/alterar,
  cumprir e desfazer sem fechar o diálogo) e aviso no contrato com as obrigações recorrentes ainda sem data.

**Alternativas descartadas.** Gerar uma linha por ocorrência futura: muitas linhas (mensal por 30 anos) e
recálculo a cada mudança de agenda. Normalizar a periodicidade na extração e editar a data do item aprovado:
mistura o texto do contrato com a operação e exigiria reaprovar.

**Consequências.** Obrigações sem data continuam sem alerta até alguém definir a agenda (a tela mostra quantas). Não
há cumprimento fora de ordem (pular um vencimento exige cumprir o anterior). Dias úteis ("até o 5º dia útil") não são
calculados: a pessoa informa a data de referência.
