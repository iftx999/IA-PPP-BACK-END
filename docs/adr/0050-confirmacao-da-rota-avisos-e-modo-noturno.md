# 0050 · Confirmação da nova rota pelo agente, avisos da operação e modo noturno do app

**Contexto.** Depois do M7 (ADR 0049), o cliente decidiu em 21/11/2026: o agente **confirma** no app a próxima rota
(J7: "o agente vê a nova ordem e confirma; sem sinal, segue a ordem que já tem"); as sugestões de reotimização
continuam aprovadas pelo programador, como na ADR 0049; **notificação** das novidades; **modo noturno** no app; sem
marca d'água nas fotos.

**Decisão.**
- **Quando pede confirmação:** a rota publicada no meio da jornada (publicada depois do início dela) e que não seja
  a calculada no próprio início (`JORNADA`). `GET /campo/rota` passa a trazer `exigeConfirmacao` e `confirmadaEm`;
  a regra fica no servidor (`RoteirizacaoService.exigeConfirmacao`). Nova API pública `JornadasDoCampo.jornadaAberta`
  (equipe e início da jornada).
- **Registro:** `POST /api/v1/campo/rota/confirmacao {roteirizacaoId, confirmadaEm}` grava `confirmada_em` e
  `confirmada_por` na rota da equipe (V37). Vale só para a publicada mais recente da equipe (uma já substituída dá
  409); repetir não muda a primeira confirmação; hora do celular, nunca no futuro, em microssegundos. Auditoria
  `ROTA_CONFIRMADA_NO_CAMPO`.
- **App:** a rota nova fica pendente (SQLCipher, `rota_nova`) e a lista e o mapa seguem a rota que vale até o agente
  conferir. A tela "Nova ordem das OS" mostra cada parada com o que mudou (subiu, desceu, nova, saiu da rota), km e
  fim previsto; "Confirmar nova ordem" troca a lista e envia a confirmação, que fica guardada sem sinal e vai na
  próxima atualização (409 é esquecida). Sem rota anterior não há o que comparar: vale direto e é confirmada. Em
  jornada, o app confere a rota a cada 2 min (só o ETag, sem erro na tela) e, ao chegar uma nova ordem, mostra um
  **aviso do Android** (canal "Rota do dia", toque abre o app), uma vez por rota.
- **Web:** a tela "Operação do dia" mostra, por equipe, "Nova rota aguardando o agente confirmar" ou a hora da
  confirmação. Os **avisos da operação** (`GET /api/v1/operacao/sugestoes/resumo`, consultado a cada minuto por
  quem tem `os:programar`) põem o contador de sugestões abertas no menu e, a cada sugestão nova, um aviso na tela
  com "Ver"; com a permissão do navegador (botão "Ativar avisos" na tela), também a notificação do navegador quando
  a Aurora está em outra aba. O sino de alertas (avaliado a cada 15 min, com e-mail) continua para os alertas do
  contrato; a operação do dia precisa de minutos, não de quartos de hora.
- **Modo noturno (app):** padrão "Automático": tema escuro entre o pôr e o nascer do sol no lugar do trabalho
  (partida da rota; algoritmo da NOAA, sem dependência nova), e das 18h às 6h enquanto o lugar não é conhecido;
  reavaliado a cada minuto. No menu, "Tema da tela": automático, sempre claro ou sempre escuro (guardado no cofre).
- **Fotos sem marca d'água** (decisão do cliente): nada muda; hora e GPS seguem nos dados do evento.

**Consequências.**
- Testes: API (rota pede confirmação, confirmação recusada para rota substituída, idempotente, situação no mapa do
  dia, resumo); app (pendente sem trocar a lista, aviso uma vez, confirmar com e sem sinal, 409, primeira rota,
  conferência a cada 2 min, encerrar; telas e acessibilidade nos dois temas; tema pelo sol e pela escolha); web
  (serviço de avisos, E2E e axe).
- Não testado em aparelho: o aviso do Android (canal, permissão do Android 13+, toque abrindo o app).
- Em aberto: criptografar as fotos à parte; alerta sonoro de emergencial; "Estou chegando"; modo luva.
