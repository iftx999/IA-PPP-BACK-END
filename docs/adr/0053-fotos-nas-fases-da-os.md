# 0053 · Foto e observação nas fases da OS (iniciar, paralisar, retomar, devolver)

**Contexto.** Pedido do cliente em 27/11/2026: além do encerramento, o agente precisa fotografar e escrever uma
observação nas outras fases da OS. A foto do início prova como o local estava antes do serviço; a da paralisação e
a da devolução comprovam o motivo (chuva, portão fechado, local sem acesso); a da retomada mostra que o impedimento
acabou. A observação conta o que a foto não mostra. Até aqui, só o encerramento tinha foto, como parâmetro do
serviço (RN-22), e a observação só era obrigatória para alguns motivos ("Outro").

**Decisão.**
- **Por serviço, versionado:** cada versão do serviço tem `fases_com_foto` e `fases_com_observacao` (V40), cada uma
  um subconjunto de INICIAR, PARALISAR, RETOMAR e DEVOLVER. O padrão, para serviço novo e para as versões já
  existentes, é **todas**; o gestor de serviços tira a exigência onde não faz sentido (tela do serviço no catálogo).
  Mudar as fases cria nova versão (RN-20); repetidas e fora de ordem contam como a mesma escolha.
- **App:** o pacote do dia diz, por OS, as fases com foto (`fasesComFoto`) e com observação (`fasesComObservacao`).
  Nessas fases a ação só é registrada com ao menos uma foto (até 10) e com a observação escrita: Iniciar e Retomar
  abrem uma tela com a câmera e a observação; Paralisar e Devolver pedem as duas junto do motivo (a observação
  obrigatória do motivo continua valendo). As fotos usam o mesmo caminho das do encerramento (ADR 0045 e 0051):
  cifradas no celular, enviadas em partes pela fila depois do evento, sem parâmetro (`os_anexo.parametro` nulo). O
  evento leva os ids (`os_evento.fotos`) e a observação no campo que já existia.
- **Servidor:** não recusa a ação sem foto ou sem observação. Iniciar ou paralisar é um fato do campo, e recusar
  desfaria a sequência da OS no celular (RN-26: vale o que o campo fez). O evento é aplicado com os avisos
  `SEM_FOTO` e `SEM_OBSERVACAO`, que o programador vê no histórico, como `FORA_DO_LOCAL`. Isso cobre o app antigo e
  o envio fora do app. A mesma foto repetida na ação é recusada; no encerramento, fotos fora dos parâmetros também.
- **Web:** o histórico da OS mostra as fotos e a observação de cada ação.

**Consequências.**
- Migração V40 (colunas com padrão; sem cópia de dados).
- Aparelhos com o app antigo continuam funcionando, mas geram `SEM_FOTO` e `SEM_OBSERVACAO` em toda ação: publicar a
  versão nova do app (Android Enterprise) junto com o servidor.
- Mais fotos por OS: até 4 fases × 10 fotos além das do encerramento. Armazenamento e envio crescem; o limite de 5 MB
  por foto e a redução no celular continuam.
- A observação pode ter dado pessoal (nome do morador, telefone): o app orienta a descrever o local e o serviço, e
  a observação segue as regras de acesso e retenção da OS (RIPD).
- Testes: API (pacote com as fases, evento com fotos e histórico, `SEM_FOTO`, `SEM_OBSERVACAO`, foto repetida, fases
  versionadas no catálogo), app (exigência por fase, fila) e front (catálogo e histórico).
