# 0014 · Extração de itens contratuais com o Claude e revisão humana obrigatória

**Contexto.** Analistas precisam transformar contratos de concessão (dezenas a centenas de páginas) em itens
acompanháveis, sobretudo metas de adesão, com rastro até a cláusula e a página (épico A, RN-10, doc 05, 5.3).
A IA erra e pode inventar cláusulas; nada pode virar meta oficial sem conferência.

**Decisão.**
- **Pipeline no back-end (monólito, módulo `contratos`):** envio valida o PDF (assinatura, tamanho, abertura
  pelo PDFBox), guarda o arquivo com SHA-256 (único por contrato) e agenda o processamento depois do commit.
  O processamento roda em thread virtual no contexto de acesso de quem enviou, com transação curta por etapa e
  a chamada à IA fora de transação: texto por página (PDFBox) → segmentos por cláusula → IA → validação →
  itens `PROPOSTO`. Documento parado há mais de 15 min, com falha ou sem texto pode ser reprocessado; propostas
  não revisadas viram `SUBSTITUIDO` (histórico preservado).
- **IA:** SDK oficial `anthropic-java`, modelo `claude-opus-5-5`, esforço `high`, saída estruturada com esquema
  estrito derivado de um record, prompt de sistema em cache, texto do contrato tratado como dado (não
  instrução), lotes de ~60 mil caracteres, fallback do servidor para recusas por classificador. Chave em
  `AURORA_IA_CHAVE` (cofre); sem chave o extrator fica desligado e os itens são cadastrados à mão. Só o texto
  do contrato e os nomes das unidades vão à IA — nenhum dado pessoal.
- **Salvaguardas:** item só entra com tipo conhecido, página existente e **trecho literal conferido no texto do
  PDF** (normalizado; pode continuar na página seguinte); percentuais fora de 0–100 e alerta maior que a meta
  são limpos; a unidade alvo só é aceita se casar com uma unidade coberta pelo contrato. A proposta original da
  IA fica em `proposta_ia` (jsonb) junto com modelo, versão do prompt e confiança.
- **Revisão (RN-10):** editar só itens pendentes; aprovar exige cláusula e, para META, unidade, meta, alerta e
  início de vigência; a aprovação cria a meta pelo módulo de metas (mesmas regras de escopo e sobreposição) com
  origem "Contrato N, cláusula X, p. Y"; aprovação e rejeição (com motivo) vão para a auditoria, indicando se o
  item foi editado em relação à IA.
- PDFs continuam no banco (`documento_arquivo`, como no sistema herdado): RLS e backup únicos. Rever se o volume
  crescer (armazenamento de objetos com link assinado).

**Consequências.** OCR de PDF digitalizado fica pendente (status `SEM_TEXTO` com orientação). O processamento
perdido numa queda da API depende de reprocessar manualmente. O custo da IA é proporcional ao tamanho do
contrato e ocorre uma vez por envio ou reprocessamento.
