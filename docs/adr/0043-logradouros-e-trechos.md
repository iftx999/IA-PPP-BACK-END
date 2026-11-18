# 0043 · Logradouros e trechos da concessionária e o endereço dos imóveis

**Contexto.** O segundo marco dos serviços de campo (doc 10, M2, história J3) precisa de logradouros, trechos e do
número de cada imóvel: sem eles não há OS de trecho, lote por rua (J5) nem agente levado à porta certa. A Aurora só
tinha matrícula + ponto (RN-09). O autor aprovou (17/11/2026) a proposta P2 do doc 10 (10.11): logradouros e trechos
entram inteiros (não são dados pessoais) e o número do imóvel fica na Aurora com acesso restrito e auditado, sem nome
do morador. O formato dos arquivos da concessionária continua indefinido (P1). Migração V32 (V29 a V31 em outras
branches ou no M1).

**Decisão.**
- **Módulo `logradouros`** (V32): `logradouro` (código da concessionária único no município, tipo, nome, bairro,
  nome sem acento para a busca com `pg_trgm`), `trecho` (LineString em SIRGAS 2000, EPSG:4674, extensão em metros
  calculada no banco, faixa de numeração e lado par/ímpar/ambos), `imovel_endereco` (matrícula → logradouro, trecho,
  número, parte inteira do número, complemento) e `importacao_logradouro` (histórico com o SHA-256 do arquivo, que não
  é guardado).
- **Formatos provisórios** até a concessionária definir os seus (P1): **GeoJSON** para trechos, uma feição por trecho
  (`codigo_logradouro`, `nome`, `codigo_trecho` e, opcionais, `tipo`, `bairro`, `numero_inicial`, `numero_final`,
  `lado`), em vez do GeoPackage proposto no doc 10: é texto, lido em fluxo feição a feição, sem dependência nativa
  (GDAL), e qualquer SIG o exporta. **CSV** `matricula;codigo_logradouro;numero;complemento` para os endereços. Os
  leitores ficam isolados (`Leitor*`): trocar o formato não mexe no resto.
- **Validação com prévia:** toda importação pode ser **simulada** (mesmo relatório, transação desfeita) antes de
  gravar. Rejeições por linha/feição (até 200 no relatório): código ausente ou repetido, geometria inválida, ponto fora
  do retângulo do Brasil (pega latitude/longitude trocadas e coordenadas em UTM), faixa invertida, matrícula
  desconhecida ou fora do escopo, logradouro inexistente. Limites: 50 MB, 200 mil feições. O arquivo passa pelo
  antivírus (ADR 0033).
- **Reimportação** atualiza pela chave (código da concessionária) sem duplicar e conta novos, atualizados e
  inalterados; trechos que não vieram no arquivo são **informados (ausentes), não apagados**, porque OS futuras os
  referenciam.
- **Trecho do imóvel** escolhido pela faixa de numeração e pelo lado; sem faixa que cubra o número, pelo trecho do
  mesmo logradouro mais próximo do ponto do imóvel. Corrigir a faixa de um trecho escolhe de novo o trecho dos
  endereços do município. Imóvel sem endereço, sem trecho ou com ponto de precisão "bairro" conta como **localização
  aproximada** na cobertura.
- **Privacidade:** RLS por escopo de unidade nas três tabelas (o trecho pelo logradouro). A lista de imóveis do
  logradouro mostra a matrícula inteira só a quem tem `dados_pessoais:ler` na unidade do imóvel, com a exibição
  auditada; para os demais, mascarada. Nunca há nome do morador.
- **Permissão** `logradouro:gerenciar` para o **Gestor de serviços** e o **Administrador** (clientes existentes pela
  V32, novos pelo provisionamento). Auditoria: `LOGRADOUROS_IMPORTADOS`, `ENDERECOS_IMPORTADOS`,
  `LOGRADOURO_CORRIGIDO`, `TRECHO_CORRIGIDO`.
- **API** `/api/v1/logradouros` (busca paginada por unidade, nome ou código; cobertura; detalhe com trechos; imóveis
  na ordem da numeração; correção de logradouro e de trecho; importações de trechos e de endereços; histórico).
- **Telas** `/logradouros` (busca com filtros na URL, cobertura do município, importação com prévia e relatório,
  histórico) e `/logradouros/:id` (trechos no mapa com destaque, correção auditada do nome e das faixas, imóveis na
  ordem da numeração). Preparação do MapLibre (estilos e worker) extraída para `nucleo/maplibre.ts`, usada também
  pelo mapa principal.

**Consequências.** O M3 já pode gerar OS de trecho e lotes por logradouro/faixa ordenados pela numeração. Enquanto a
concessionária não define o formato, ela precisa converter os dados para GeoJSON e CSV; um leitor novo (GeoPackage,
shapefile, API) entra ao lado dos atuais. A exceção à RN-09 entra no RIPD, que continua pendente. Ficam de fora:
desenho e edição de geometria na tela (a correção manual é de atributos e faixas), exclusão de trechos ausentes e
geocodificação de imóveis sem ponto a partir do endereço.
