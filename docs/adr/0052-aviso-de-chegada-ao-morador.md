# 0052 · "Estou chegando": aviso de chegada ao morador por SMS

**Contexto.** Ideia do protótipo aprovada pelo cliente em 21/11/2026: o agente avisa o morador de que está a
caminho, para não encontrar o imóvel fechado. Exige o telefone do morador, um dado pessoal que a Aurora não tinha.

**Decisão.**
- **Origem do telefone:** a concessionária, em CSV `matricula;telefone` (`POST /api/v1/contatos-dos-imoveis`, com a
  mesma permissão do vínculo matrícula → endereço, `logradouro:gerenciar`; tela "Importar celulares" em
  Logradouros). Só celular brasileiro com DDD; só imóveis com endereço importado; telefone em branco tira o imóvel
  do aviso.
- **Proteção:** o número fica cifrado em `imovel_contato` (V38) com AES-256-GCM e a chave `AURORA_CHAVE_CONTATOS`,
  fora do banco; em claro só os 4 últimos dígitos. Ele nunca vai para o app, para a web, para os logs nem para a
  auditoria; o servidor decifra só no envio. RLS pelo escopo do endereço. Seção 2 do RIPD.
- **App:** o pacote diz, por OS, se há celular (`avisoDeChegada`) e quando avisou (`avisadoEm`). Na OS programada,
  o botão "Estou chegando" pede o tempo (5 a 30 min) e chama `POST /api/v1/campo/ordens/{id}/aviso-de-chegada`
  (id gerado no celular, idempotente). Só com sinal: um aviso atrasado não serve, então ele não entra na fila.
- **Regras:** OS da equipe em jornada, programada e de imóvel com celular; um aviso por OS (o que falhou pode ser
  repetido). Texto sem nome de ninguém: "<remetente>: a equipe está a caminho do seu imóvel (<serviço>) e deve
  chegar em cerca de N minutos. Não responda esta mensagem." (`aurora.sms.remetente`).
- **Envio:** porta `EnvioDeSms`. O provedor é escolhido e contratado pela empresa; até lá vale o adaptador
  `registro` (`aurora.sms.provedor=registro`, padrão), que não envia nada: o aviso fica `REGISTRADO` e o app diz
  que o envio de SMS ainda não está ligado. Falha do provedor fica `FALHOU` e pode ser repetida.

**Consequências.**
- Variável nova obrigatória em produção: `AURORA_CHAVE_CONTATOS` (32+ caracteres); `AURORA_SMS_PROVEDOR`.
- Testes: API (importação com recusas, número cifrado e nunca devolvido, pacote, aviso enviado/idempotente/repetido,
  falha do provedor, auditoria sem o número), unidade (celular em E.164, cifra), app (botão, mensagens, sem sinal,
  acessibilidade) e E2E da importação.
- Pendente com o cliente: base legal e texto do SMS, contrato com o provedor (suboperador) e o adaptador dele,
  canal de opt-out do morador.
