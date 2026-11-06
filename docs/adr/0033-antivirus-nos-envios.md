# 0033 · Antivírus nos arquivos enviados (ClamAV)

**Contexto.** O doc 09 (9.7) pede, no upload de PDF, conferir o tipo real, o tamanho, passar por antivírus e
guardar fora do webroot. Os três outros já existiam (assinatura `%PDF-`, limite de 50 MB, arquivo no banco com
SHA-256); faltava o antivírus. Contratos chegam de fora da empresa (concessionárias, poder concedente) e ficam
disponíveis para download por outras pessoas; a logo do cliente aparece na tela de entrada, sem login.

**Decisão.**
- **ClamAV** (`clamav/clamav:1.4`) como contêiner na rede interna, falando com a API pelo protocolo INSTREAM do
  clamd por TCP. O cliente é próprio (`ClamAvAntivirus`, ~80 linhas), sem biblioteca. Como o ClamAV é GPL e roda em
  processo separado, a licença não alcança a Aurora.
- **Onde:** PDF de contrato (A1) e logo do cliente (S1), depois das checagens baratas (tamanho e tipo) e **fora da
  transação** (o envio não segura conexão do banco enquanto o antivírus trabalha). CSV de carga não é gravado como
  arquivo (vira linhas validadas), então não passa pelo antivírus.
- **Ameaça encontrada:** envio recusado (422, com o nome da ameaça) e evento `ARQUIVO_RECUSADO_PELO_ANTIVIRUS` na
  trilha do cliente, gravado numa transação própria (fica mesmo com o envio desfeito), com o SHA-256 e o tamanho.
- **Antivírus fora do ar:** em produção (`aurora.antivirus.obrigatorio=true`) o envio é recusado (503); no
  desenvolvimento e nos testes, segue com aviso no log. Métrica `aurora.antivirus.verificacoes` por resultado.

**Alternativas descartadas.** Biblioteca Java de cliente do clamd: dependência a mais para um protocolo de quatro
passos. Serviço de varredura na nuvem: o contrato sairia da infraestrutura do cliente. Varrer depois de gravar
(assíncrono): o arquivo ficaria disponível para download antes da verificação.

**Consequências.** Produção precisa de ~1,5 GB a mais de memória. O clamd tem limite de 100 MB por arquivo, acima do
limite de 50 MB dos contratos. Na primeira subida, até as assinaturas carregarem, envios ficam recusados.
O clamd não oferece TLS, então o conteúdo enviado trafega sem criptografia entre a API e o antivírus (exceção
`UNENCRYPTED_SOCKET` no SpotBugs): o ClamAV precisa ficar na mesma rede interna da API, sem porta publicada. Em outra
topologia, proteger o trecho com um túnel TLS ou malha de serviços.
