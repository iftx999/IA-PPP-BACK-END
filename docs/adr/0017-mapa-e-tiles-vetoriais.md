# 0017 · Mapa: tiles vetoriais do PostGIS, camadas leves em GeoJSON e exportação do visível

**Contexto.** O mapa (épico E) mostra do polígono do município até cada imóvel (E1), com camadas que ligam e
desligam (E2), marcações de visita (E4) e exportação do que está visível (E5, RN-12). A base do demo tem 11,6 mil
imóveis localizados, mas um cliente grande passa de centenas de milhares. Matrícula é dado pessoal indireto
(LGPD, doc 09) e o endereço nunca sai da rede da concessionária (RN-09, E6).

**Decisão.**
- **Duas formas de entrega.** Camadas leves (municípios coloridos pela adesão, setores, ETE/EEE) vão de uma vez em
  GeoJSON por `GET /api/v1/mapa/camadas`. Imóveis e rede vão por **tiles vetoriais** (`ST_AsMVT`) em
  `/api/v1/mapa/tiles/{z}/{x}/{y}.mvt`: o volume depende só da área vista, e o filtro de tenant/escopo é o mesmo
  RLS das telas (H5). O MapLibre manda o token em cada tile (`transformRequest`).
- **Agrupamento no servidor.** Até o zoom 14, os imóveis vêm agrupados numa grade de 3×3 células por tile (~85 px, maiores que o círculo do agrupamento)
  (`ST_SnapToGrid` em Web Mercator), com contagem total, ligados e sem ligação. A partir do zoom 15, um ponto por
  imóvel. Rede de esgoto a partir do zoom 12 (presumida tracejada, confirmada contínua).
- **Tiles sem matrícula.** O ponto leva só situação e se a localização é aproximada. O detalhe vem do clique
  (`GET /api/v1/mapa/imoveis?longitude&latitude&raio`, até 10 imóveis): matrícula completa só onde vale
  `dados_pessoais:ler` (RN-13, ADR 0013), e essa exibição é auditada (`ACESSO_DADO_PESSOAL`).
- **Situações no mapa:** ligado, factível sem ligação, cortado e sem rede (situação `POTENCIAL` do sistema
  comercial); "localização aproximada" é um filtro à parte (precisão diferente de `ENDERECO`).
- **Marcações (E4):** a unidade é a mais específica, entre as que a pessoa pode marcar (`mapa:marcar`), cujo
  polígono contém o ponto (setor, depois malha do município). Fora delas: 422. Só quem criou remove. A trilha
  guarda só identificadores (o texto pode ter dado pessoal).
- **Exportação (E5):** `POST /api/v1/mapa/exportacao` com a área, as camadas ligadas e, no PDF, a imagem do mapa
  como a pessoa o via (PNG conferido pelo cabeçalho: até 6 MB e 4096 px por lado, antes de decodificar). CSV com
  imóveis e marcações; PDF A4 deitado com imagem, resumo por situação e listas. Até 50 mil imóveis por emissão.
  Auditoria com área, camadas, linhas, matrículas completas e SHA-256 do arquivo (`RELATORIO_EXPORTADO`).
- **Localização pelo agente (E6):** `POST /api/agente/v1/localizacoes` (matrícula, unidade, longitude, latitude,
  precisão `ENDERECO`/`LOGRADOURO`/`BAIRRO`), gravado em lote. Ponto corrigido em campo ou desenhado não é
  substituído pelo geocódigo (conta como "preservado"). Evento `IMOVEIS_GEOCODIFICADOS` (nome herdado).
- **Fundo do mapa:** OpenStreetMap em desenvolvimento, com atribuição. Em produção os tiles de fundo vêm do token
  `FUNDO_DO_MAPA` (front, `app.config`): a política de uso dos tiles públicos do OSM não admite uso pesado, então a
  empresa deve contratar um provedor ou servir os próprios tiles.
- **Front:** MapLibre GL JS 6.12 (BSD-3), fixado na versão; worker e CSS servidos à parte (só a rota do mapa os
  carrega). Ícones (casa, casa pontilhada para localização aproximada, pino, estação, agrupamento) desenhados em
  canvas a partir dos tokens do tema: sem sprites nem fontes de terceiros, e o mapa acompanha o claro/escuro.
  Vista inicial por link (`/mapa?lon=&lat=&zoom=`).

**Consequências.** Tiles de imóveis custam uma consulta por tile com índice GiST; o cache do navegador
(`private, max-age=300`) evita repetir ao navegar. Se a base crescer muito, o próximo passo é materializar os
pontos em Web Mercator por competência. A malha dos municípios é a do IBGE em qualidade mínima (leve); setores
"GERADO" são retângulos de demonstração até virem os polígonos reais.
