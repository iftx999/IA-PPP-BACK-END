# 0036 · Campanhas de ligação e ordens de serviço, conferidas pela carga mensal

**Contexto.** A Aurora mostra quantos imóveis estão sem ligação, quanto falta para a meta e quanto custa ficar abaixo
dela (ADR 0035), mas o trabalho que resolve o problema (visitar, notificar, ligar, religar) acontece fora dela, sem
rastro. O autor aprovou (01/11/2026) campanhas de ligação que geram ordens de serviço (OS) para equipes próprias ou
terceirizadas, conferidas pela carga mensal do sistema comercial (histórias I2–I4).

**Decisão.**
- **Módulo `campanhas`** (V28) com quatro tabelas: `equipe`, `equipe_membro`, `campanha` e `ordem_servico`, todas com
  RLS pelo tenant e pelo escopo da unidade (a da equipe, a da campanha e a do imóvel da OS).
- **Equipe:** nome, própria ou terceirizada (com o nome da empresa), unidade onde atua e pessoas (usuários do tenant).
- **Campanha:** nasce de um recorte dos imóveis sem ligação da competência mais recente (RN-02/RN-14), por três
  origens: a lista de sem ligação (unidade, situação, motivo do corte), uma área do mapa (retângulo visível) ou um
  setor comercial. Antes de criar, a pré-visualização mostra quantos imóveis entram, quantos já têm OS aberta (ficam
  de fora: um imóvel tem no máximo uma OS aberta) e o **impacto na meta**: a adesão da unidade da campanha se todas as
  OS virarem ligação e quantas das ligações que faltam para a meta a campanha cobre. A ação escolhida define o tipo das
  OS: `LIGACAO` (factível → ligação nova; cortado → religação), `VISTORIA` ou `NOTIFICACAO`. Prazo obrigatório.
- **Prioridade da OS** (RN-19): pelo semáforo da unidade do imóvel (RN-03): abaixo do alerta = alta, em atenção =
  média, demais = baixa. As OS são listadas por prioridade e prazo.
- **Fluxo da OS:** `PLANEJADA` → (distribuída a uma equipe) `EM_CAMPO` → resultado `EXECUTADA`, `RECUSADA` (motivo),
  `REAGENDADA` (nova data; continua em campo) ou `INVIAVEL` (motivo). Encerrar (trabalho concluído) ou cancelar
  (desistência) a campanha cancela as OS ainda em aberto; a tela pede confirmação. Nada é apagado.
- **Conferência pela carga** (RN-19): OS `EXECUTADA` de ligação nova ou religação é **confirmada** quando alguma
  competência a partir do mês da execução mostra o imóvel `LIGADO`; fica **divergente** quando a competência seguinte
  à da execução já chegou e o imóvel continua sem ligação (um mês de tolerância para o sistema comercial registrar).
  Vistoria e notificação não são conferidas. A conferência roda depois de cada carga e ao registrar um resultado, é
  idempotente e uma divergência volta a ser confirmada se uma carga posterior mostrar a ligação.
- **Alerta `ORDEM_DIVERGENTE`** (RN-07): um por campanha com OS divergentes, aberto e resolvido pelo avaliador de
  alertas; enviado a quem tem `campanha:gerenciar`.
- **Painel da campanha:** funil planejadas → em campo → executadas → confirmadas, produtividade por equipe, OS
  atrasadas (em aberto depois do prazo), motivos de recusa e inviabilidade, e o efeito real: ligações confirmadas e a
  adesão atual da unidade comparada com a da criação.
- **Permissões:** `campanha:gerenciar` (equipes, campanhas, distribuição, painel) e `os:executar` (ver e registrar o
  resultado das OS das equipes de que a pessoa faz parte). Novo perfil do sistema **Equipe de campo** (`os:executar`,
  `dados_pessoais:ler` — a matrícula identifica o imóvel no campo — e `unidade:ler`); o gestor comercial e o
  administrador recebem `campanha:gerenciar`; o administrador também `os:executar`.
- **Auditoria:** criar, encerrar e cancelar campanha; criar e alterar equipe; distribuir OS; registrar resultado. A
  conferência automática não é auditada (é derivada da carga, que já é).

**Consequências.** O esforço de campo passa a aparecer na Aurora e é cobrado pela carga, o que separa "fizemos" de
"virou ligação". Ficam fora: aplicativo de campo offline, foto e assinatura no resultado, integração das OS com o
sistema comercial, roteirização e polígonos livres no mapa (a área é o retângulo visível, como na exportação, ADR
0017).
