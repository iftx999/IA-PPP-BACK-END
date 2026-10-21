# Aurora — Dados e IA para concessões de saneamento

> Projeto de estudo (sem fins comerciais). Todos os dados de demonstração são **fictícios**.

**Rede pronta. Imóvel ligado. Meta cumprida.** Acompanha a adesão à rede de esgoto em concessões (PPPs),
liga cada meta à cláusula do contrato e responde perguntas mostrando a fonte de cada número.
Especificação completa em [`docs/`](docs/).

## Estrutura

| Pasta | Conteúdo |
|-------|----------|
| `backend/` | API em Java 21 + Spring Boot 4.1 (Spring Security, Flyway, Spring Modulith) |
| `frontend/` | SPA em Angular 22 |
| `infra/` | Docker Compose: PostgreSQL 18 + PostGIS + pgvector, Keycloak, Mailpit |
| `docs/` | Visão, histórias, regras de negócio, modelo de dados, arquitetura, identidade visual, ADRs |

## Rodando localmente

```bash
cp infra/.env.example infra/.env
docker compose -f infra/compose.yaml up -d --build   # Postgres :5433, Keycloak :8180, Mailpit :8025
cd backend && ./mvnw spring-boot:run                 # API :8080 (aplica as migrações)
```

Usuários de desenvolvimento no Keycloak (realm `aurora`): `admin@exemplo.dev`, `diretoria@exemplo.dev`,
`comercial.cidadea@exemplo.dev`, `auditoria@exemplo.dev`, senha `aurora-dev`.

Dados fictícios da versão anterior: `infra/scripts/importar-banco-antigo.sh` (ver o cabeçalho do script).

## Testes

```bash
cd backend && ./mvnw verify   # requer Docker (Testcontainers)
```

Inclui testes obrigatórios de **isolamento entre tenants** (RLS no banco e na API).

## Isolamento multi-tenant

Banco compartilhado com `tenant_id` + Row-Level Security. A API conecta como `aurora_app` (sem `BYPASSRLS`) e,
a cada conexão, define `app.tenant_id`, `app.usuario_id` e `app.escopos` a partir do token — nunca da requisição.
Recurso de outro tenant ou fora do escopo responde **404**.
