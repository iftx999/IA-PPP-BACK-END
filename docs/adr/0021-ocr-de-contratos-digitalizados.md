# 0021 · OCR local de contratos digitalizados com Tesseract

**Contexto.** Contratos antigos chegam escaneados, sem camada de texto: a extração ficava em `SEM_TEXTO` e os itens
eram cadastrados à mão (pendência 10 do doc 06). A versão anterior já reconhecia texto (evento herdado
`TEXTO_RECONHECIDO_POR_OCR`, coluna `texto_por_ocr`). O conteúdo de contratos não deve sair para serviços externos
sem necessidade (LGPD, sigilo comercial).

**Decisão.**
- **Tesseract** (Apache-2.0) com o idioma português, rodando na máquina da API. Chamado como processo com
  argumentos fixos, sem shell; a imagem entra pela entrada padrão; tempo máximo por página
  (`aurora.ocr.segundos-por-pagina`). Sem o binário ou sem `por`, o OCR se desliga sozinho (com aviso) e o
  comportamento anterior continua.
- Só as páginas sem texto (menos de 20 caracteres) são desenhadas a 300 DPI em tons de cinza e reconhecidas; as
  demais ficam como o PDF trouxe. Até 300 páginas digitalizadas por arquivo.
- O documento fica marcado (`texto_por_ocr`) e a trilha registra páginas e tempo; a tela avisa para conferir os
  trechos com atenção.
- **Conferência do trecho com tolerância** só para texto de OCR: o trecho citado pela IA é aceito se aparecer com até
  5% de caracteres diferentes (mínimo 2), por distância de edição (o OCR troca, por exemplo, "A1" por "Aí"). Texto de
  PDF continua com conferência literal. Revisão humana segue obrigatória.

**Consequências.** A imagem de produção da API precisa dos pacotes `tesseract-ocr` e `tesseract-ocr-por` (o CI já os
instala). OCR leva alguns segundos por página e roda no processamento assíncrono do documento. Digitalizações ruins
podem dar texto pobre: aí o documento fica `SEM_TEXTO` com a orientação de cadastrar à mão.
