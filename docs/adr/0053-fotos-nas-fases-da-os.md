# 0053 · Foto nas fases da OS (iniciar, paralisar, retomar, devolver)

**Contexto.** Pedido do cliente em 27/11/2026: além do encerramento, o agente precisa fotografar nas outras fases da
OS. A foto do início prova como o local estava antes do serviço; a da paralisação e a da devolução comprovam o
motivo (chuva, portão fechado, local sem acesso); a da retomada mostra que o impedimento acabou. Até aqui, só o
encerramento tinha foto, como parâmetro do serviço (RN-22).

**Decisão.**
- **Por serviço, versionado:** cada versão do serviço tem `fases_com_foto` (V40), um subconjunto de INICIAR,
  PARALISAR, RETOMAR e DEVOLVER. O padrão, para serviço novo e para as versões já existentes, é **todas**; o gestor de
  serviços tira a exigência onde não faz sentido (tela do serviço no catálogo). Mudar as fases cria nova versão
  (RN-20); repetidas e fora de ordem contam como a mesma escolha.
- **App:** o pacote do dia diz, por OS, as fases com foto (`fasesComFoto`). Nessas fases a ação só é registrada com
  ao menos uma foto (até 10): Iniciar e Retomar abrem uma tela com a câmera; Paralisar e Devolver pedem a foto junto
  do motivo. As fotos usam o mesmo caminho das do encerramento (ADR 0045 e 0051): cifradas no celular, enviadas em
  partes pela fila depois do evento, sem parâmetro (`os_anexo.parametro` nulo). O evento leva os ids
  (`os_evento.fotos`).
- **Servidor:** não recusa a ação sem foto. Iniciar ou paralisar é um fato do campo, e recusar desfaria a sequência
  da OS no celular (RN-26: vale o que o campo fez). O evento é aplicado com o aviso `SEM_FOTO`, que o programador vê
  no histórico, como `FORA_DO_LOCAL`. Isso cobre o app antigo e o envio fora do app. A mesma foto repetida na ação é
  recusada; no encerramento, fotos fora dos parâmetros também.
- **Web:** o histórico da OS mostra as fotos de cada ação.

**Consequências.**
- Migração V40 (colunas com padrão; sem cópia de dados).
- Aparelhos com o app antigo continuam funcionando, mas geram `SEM_FOTO` em toda ação: publicar a versão nova do app
  (Android Enterprise) junto com o servidor.
- Mais fotos por OS: até 4 fases × 10 fotos além das do encerramento. Armazenamento e envio crescem; o limite de 5 MB
  por foto e a redução no celular continuam.
- Testes: API (pacote com as fases, evento com fotos e histórico, `SEM_FOTO`, foto repetida, fases versionadas no
  catálogo), app (exigência por fase, fila) e front (catálogo e histórico).
