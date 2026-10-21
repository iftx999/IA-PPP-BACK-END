# Registros de decisão de arquitetura (ADR)

Formato curto: **Contexto**, **Decisão**, **Consequências**. Um ADR aceito não é editado; uma mudança de rumo
gera um novo ADR que o substitui.

| # | Decisão | Status |
|---|---------|--------|
| [0001](0001-monolito-modular.md) | Monólito modular com Spring Modulith e camadas por módulo | Aceito |
| [0002](0002-multitenant-rls.md) | Multi-tenant em banco compartilhado com Row-Level Security | Aceito |
| [0003](0003-identidade-keycloak.md) | Identidade no Keycloak, um emissor por tenant | Aceito |
| [0004](0004-schema-herdado.md) | Schema reconstruído a partir do banco da versão anterior | Aceito |
| [0005](0005-java-21.md) | Java 21 LTS em vez de Java 25 | Aceito |
| [0006](0006-acesso-a-dados.md) | JdbcClient com SQL explícito para leituras analíticas | Aceito |
| [0007](0007-repositorios-separados.md) | Repositórios separados para back-end e front-end | Aceito |
| [0008](0008-qualidade-no-build.md) | Formatação, cobertura e documentação da API verificadas no build | Aceito |
