# 0015 · Alertas por sincronização de condições e tarefas agendadas com trava no banco

**Contexto.** RN-07/RN-08/F1–F3 pedem alertas de adesão e de fonte atrasada avaliados a cada 15 minutos, sem
duplicar, resolvidos sozinhos quando a condição passa, com reconhecimento. A API pode rodar em várias instâncias
e as tabelas têm RLS forçado por tenant.

**Decisão.**
- **Avaliação como sincronização:** as regras (puras, `RegrasDeAlerta`) produzem as condições que devem ter alerta
  agora, cada uma com uma chave estável (`ADESAO:<unidade>`, `FONTE:<fonte>`, `TRILHA` — as do sistema herdado).
  O avaliador abre as que não têm alerta aberto, atualiza texto e severidade das que mudaram e resolve as que
  sumiram. O índice único parcial `(tenant_id, chave) WHERE resolvido_em IS NULL` garante "não duplicar" mesmo
  em corrida (`ON CONFLICT DO NOTHING`). Reconhecer não fecha o alerta; ele só se resolve quando a condição passa.
- **Fontes da verdade:** adesão e meta vigente (própria ou herdada) vêm do módulo de metas
  (`Metas.situacoes()`), fontes atrasadas do módulo de integrações (`Fontes.atrasadas()`), a trilha do módulo de
  auditoria (`VerificacaoDaTrilha`). Nenhuma regra de cálculo é duplicada.
- **Contexto do sistema:** tarefas rodam por tenant num contexto sem usuário, com todas as permissões nas
  unidades raiz (`ContextosDoSistema`); os tenants vêm de `tenant_emissor` (sem RLS). Eventos de auditoria
  gerados assim aparecem como "Sistema".
- **Agendamento:** `@Scheduled` (15 min para alertas, 24 h para a verificação da trilha) com trava em
  `tarefa_agendada` (UPDATE condicional com prazo de expiração): só uma instância executa por vez e uma queda não
  trava a tarefa para sempre. Desligável por `aurora.tarefas.habilitadas` (os testes chamam o avaliador direto).
  Quem gere metas pode reavaliar na hora (`POST /alertas/avaliacao`).
- **Permissões:** ver e reconhecer exigem `indicador:ler` (o catálogo herdado não tem permissões de alerta); o
  RLS mostra os alertas das unidades do escopo e os sem unidade (fonte, trilha). Reconhecer é auditado.

**Consequências.** Alertas de ritmo (META_FORA_DO_RITMO) e de prazo contratual existem no schema, mas ainda não
são gerados. A notificação por e-mail (`notificado_em`) fica para depois.
