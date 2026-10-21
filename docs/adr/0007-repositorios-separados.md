# 0007 · Repositórios separados para back-end e front-end

**Contexto.** O doc 09 sugeria monorepo; o projeto foi publicado em dois repositórios no GitHub.

**Decisão.** `IA-PPP-BACK-END` contém API, infraestrutura e documentação; `IA-PPP-FRONT-END-` contém a SPA
Angular. O contrato entre eles é a OpenAPI publicada pela API (`/v3/api-docs`).

**Consequências.** Pipelines e versões independentes. Mudanças de contrato precisam ser coordenadas; a geração
do cliente TypeScript a partir da OpenAPI deixa a quebra visível no build do front.
