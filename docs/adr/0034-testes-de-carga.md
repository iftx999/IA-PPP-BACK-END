# 0034 · Testes de carga: k6 nas leituras e JUnit na carga mensal

**Contexto.** O doc 09 (9.9) define metas de desempenho "a ajustar após medir", mas nada tinha sido medido. As
leituras dependem da API inteira no ar (token, RLS, PostGIS); a carga mensal de ~1 milhão de imóveis precisa de um
banco descartável, que não suje os dados de demonstração.

**Decisão.**
- **Leituras com k6** (`infra/carga/leitura.js`, via contêiner `grafana/k6`): taxa constante com a mistura de uso
  (painel, troca de competência, sem ligação, tiles) e limites de p95 por fluxo iguais às metas. Token por password
  grant num client `aurora-carga` criado por script **só no Keycloak local** (o script recusa outro servidor).
- **Escrita com JUnit + Testcontainers** (`DesempenhoDaCargaTest`, tag `desempenho`, fora do `verify`): 1 milhão de
  imóveis em 400 setores, em dois lotes de 500 mil pelo mesmo serviço do agente, medindo a carga e o recálculo.
  Roda no workflow "Desempenho" (manual e semanal).
- A carga registra no log o tempo de cada fase (validação, consistência, gravação, recálculo).
- Resultados versionados em `docs/desempenho.md`.

**Consequências.** A primeira medição reprovou a carga (727 s) e levou à reescrita do recálculo da adesão (agrega uma
vez por unidade e soma pela hierarquia): 72,6 s. Ficam sem medição as metas que dependem da IA real e a
disponibilidade.
