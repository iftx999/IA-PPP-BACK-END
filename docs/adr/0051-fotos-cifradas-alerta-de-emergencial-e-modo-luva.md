# 0051 · Fotos cifradas no celular, alerta de OS emergencial e modo luva

**Contexto.** Ideias do protótipo do app (doc 10, M6) aprovadas pelo cliente em 21/11/2026: criptografar as fotos à
parte, alertar a OS emergencial que entra durante a jornada e o "modo luva" (botões maiores e ações por confirmação
longa).

**Decisão.**
- **Fotos cifradas:** AES-256-GCM com a chave no **Android Keystore** (gerada no primeiro uso, não exportável), pelo
  canal nativo do app (sem dependência nova). O arquivo no celular (`.jpg.cifrada`) traz o vetor de 12 bytes e a
  etiqueta do GCM; a miniatura e o envio em partes decifram só na memória (a última foto fica em cache para as
  partes seguintes). Tamanho e SHA-256 continuam os da foto (o servidor confere a foto, não o arquivo). Fotos
  guardadas antes continuam legíveis. Revogação e troca de empresa apagam as fotos e a chave.
- **Alerta de OS emergencial:** quando o pacote da equipe traz uma emergencial aberta que não estava no primeiro
  pacote da jornada, o app mostra um aviso do Android no canal "OS emergencial" (prioridade alta, vibração longa,
  categoria alarme; som e volume ajustáveis pelo agente no Android) e um cartão vermelho na tela Hoje ("Ver OS",
  "Ok, vi"). Cada emergencial toca uma vez (lista guardada no SQLCipher, apagada ao encerrar a jornada). A
  conferência a cada 2 min (ADR 0050) passou a buscar também as diferenças do pacote, então a emergencial chega sem
  o agente puxar a tela.
- **Modo luva:** opção no menu, guardada no cofre. Botões de 72 dp e texto maior no app inteiro; as ações que mudam
  a OS (iniciar, retomar, paralisar, devolver, salvar o encerramento) só saem segurando o botão até a barra encher
  (0,9 s), com vibração ao confirmar; um toque rápido mostra a dica. Leitor de tela: o mesmo botão responde ao
  "tocar duas vezes e segurar".

**Consequências.**
- Testes do app: arquivos cifrados (sem a foto em claro no disco, partes, fotos antigas, revogação), emergencial
  (primeiro pacote não toca, a que entra toca uma vez, pela conferência periódica, encerrar), modo luva (menu,
  tamanho, toque rápido × segurar) e acessibilidade nos dois temas.
- Não testado em aparelho: Keystore, canal de notificação da emergencial e vibração.
