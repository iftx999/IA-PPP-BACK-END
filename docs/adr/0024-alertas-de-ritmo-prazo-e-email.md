# 0024 · Alertas de ritmo e de prazo contratual e notificação por e-mail

**Contexto.** O schema herdado prevê os tipos `META_FORA_DO_RITMO` e `PRAZO_CONTRATUAL` e a coluna
`alerta.notificado_em`, mas o ADR 0015 só gerava alertas de adesão, fonte e trilha. O gestor quer saber antes do
prazo se a meta sai (B2, RN-05) e não perder prazos do contrato. Também quer ser avisado sem precisar abrir a
Aurora. As regras exatas não estão no doc 03: as faixas abaixo são decisão de projeto e podem ser ajustadas.

**Decisão.**
- **Ritmo (RN-05).** O módulo de adesão expõe `Medicoes.projecoes()`, que reaproveita a `Projecao` do painel (sem
  duplicar a regra) em cada unidade com medição na competência mais recente e meta vigente com fim de vigência
  (própria ou herdada). Gera alerta quando a projeção no prazo não atinge a meta: **Crítico** se ela fica abaixo
  do limite de alerta, senão **Atenção**. Ficam de fora: unidade já na meta, mês do prazo (aí vale o alerta de
  adesão) e ETE/EEE. Chave `RITMO:<unidade>`.
- **Prazo contratual.** O módulo de contratos expõe `PrazosContratuais`: itens `PRAZO` aprovados com data limite e
  ainda não cumpridos, e contratos com fim de vigência. Item: alerta a partir de 60 dias antes, **Crítico** com 15
  dias ou menos ou vencido. Continua aberto depois de vencer até alguém marcar o item como **cumprido**
  (`POST/DELETE /contratos/itens/{id}/cumprimento`, `contrato:gerenciar`, auditado como
  `PRAZO_CONTRATUAL_CUMPRIDO`/`REABERTO`; V20 `cumprido_em/por`). Fim da vigência do contrato: a partir de 180 dias,
  Crítico com 30, até a data. Chaves `PRAZO:<item>` e `CONTRATO:<contrato>`. A unidade é a do item ou a do contrato.
- **E-mail.** Depois de cada avaliação (15 min), a tarefa `notificar-alertas` (mesma trava do ADR 0015) envia **um
  resumo por pessoa** com os alertas novos, não reconhecidos e criados nas últimas 24 h (após uma parada longa, não
  chega uma enxurrada de e-mails antigos). Destinatários: usuários ativos com a permissão da tela do alerta
  (`indicador:ler`; `contrato:ler` para prazo; `integracao:gerenciar` para fonte; `auditoria:ler` para trilha)
  numa concessão vigente que cobre a unidade (`Destinatarios`, módulo de acesso). Cada pessoa escolhe
  `NENHUM`, `CRITICOS` (padrão) ou `TODOS` (`/alertas/preferencias`, V20 `alerta_preferencia`).
- **Entrega.** Um alerta só sai da fila (`notificado_em`) quando todos os destinatários receberam. Com falha de
  SMTP, volta na execução seguinte (quem já recebeu pode receber de novo: preferimos repetir a perder um aviso
  crítico). Métrica `aurora.alertas.emails{resultado}`. O log identifica a pessoa pelo id, nunca pelo e-mail.
- **SMTP** em `aurora.email.*` (`AURORA_SMTP_HOST`, `_PORTA`, `_USUARIO`, `_SENHA`, `_REMETENTE`, `_TLS`), as mesmas
  variáveis do Keycloak em produção. Não usamos `spring.mail.*`: com host vazio, o envio fica desligado (a tela
  avisa) em vez de falhar a cada ciclo. Em desenvolvimento vai para o Mailpit.
- **Conteúdo (LGPD).** O e-mail traz só o que a tela de alertas mostra (unidade, percentuais, prazos, link). Nada de
  matrícula ou dado pessoal. Texto escapado no HTML.

**Consequências.** Prazos recorrentes: ver ADR 0026 (agenda, cumprimento por vencimento e obrigações).
Antecedências fixas no código; tornar configuráveis por tenant se os clientes pedirem. Sem fila externa: se a
mensageria entrar (doc 09), a notificação é a primeira candidata a consumir eventos.
