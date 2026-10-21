# 9. Engenharia e tecnologia (stack e boas práticas)

> Escopo da stack: **Java, Spring (incluindo Spring Security), Angular e PostgreSQL**, com as melhores práticas e o
> ecossistema ao redor de cada um, pensando em **experiência do usuário** e **processamento**.
> Versões verificadas em **21/10/2026** nas fontes oficiais (listadas no fim). Itens marcados com **(confirmar)**
> vêm de fonte secundária ou de memória e devem ser conferidos nas notas oficiais antes de congelar.

## 9.1 Princípios

1. **Simples primeiro, escalável depois:** monólito modular bem fronteirizado; só separar serviço com motivo medido.
2. **Segurança e isolamento por padrão:** multi-tenant com RLS, acesso por perfil e escopo (documento 8).
3. **Dados no lugar certo:** cálculo pesado no PostgreSQL (SQL/PostGIS), regra de negócio no domínio Java, apresentação no Angular.
4. **Medir antes de otimizar:** metas de desempenho (9.9) e observabilidade desde o primeiro dia.
5. **Tudo automatizado:** build, testes, qualidade, segurança e deploy por pipeline.
6. **Decisões registradas:** cada escolha relevante vira um ADR em `docs/adr/`.

## 9.2 Versões recomendadas

| Peça | Escolha | Situação verificada |
|------|---------|---------------------|
| Java | **Java 25 (LTS)** | Suportado pelo Spring Boot 4.0 e 4.1; Java 21 também funciona |
| Spring Boot | **4.1.x** (Spring Framework 7) | 4.1.0 lançado em 10/06/2026; suporte OSS estimado até jul/2027. **3.5 e anteriores estão em fim de vida** (3.5 acabou em 30/06/2026) |
| Spring Security | Versão que acompanha o Boot 4.1 (linha 7) | Usar a gerenciada pelo BOM do Boot (confirmar número exato) |
| Angular | **Angular 22** | Lançado em 03/06/2026; suporte ativo até jun/2027, LTS até jun/2028 |
| PostgreSQL | **PostgreSQL 18** | Suportado até nov/2030; a 19 está em RC (não usar ainda) |
| PostGIS | Linha **3.6** ou mais nova compatível com o PG 18 | Confirmar a última estável |
| MapLibre GL JS | **6.x** (v6.1.0 verificada) | Globo e 3D com correções recentes |
| Build | Maven (ou Gradle) com **Maven Wrapper** e BOM do Spring | |
| Node/TypeScript | LTS de Node compatível com Angular 22, TypeScript em modo estrito | Confirmar no guia de atualização |

Regra: fixar versões no repositório (`.sdkmanrc`/toolchains, `.nvmrc`, `package-lock.json`) e atualizar por **Renovate/Dependabot**.

## 9.3 Backend — Java e Spring

### Arquitetura
- **Monólito modular** com **Spring Modulith**: módulos `tenant`, `acesso`, `contratos`, `metas`, `adesao`, `imoveis`, `mapa`, `alertas`, `assistente`, `auditoria`, `integracoes`. Cada módulo expõe uma API pública (pacote) e comunica por **eventos de aplicação**; o Modulith **verifica** as fronteiras nos testes.
- **Hexagonal** dentro de cada módulo: `domain` (regras puras, sem Spring), `application` (casos de uso), `adapters` (web, JPA/JDBC, IA, arquivos). **ArchUnit** impede dependências proibidas.
- **DDD leve:** entidades e *value objects* para `Adesao`, `Meta`, `Situacao`, `Competencia`, `Matricula`; regras RN-01 a RN-07 vivem no domínio e são testadas sem banco.
- **CQRS leve:** escrita por casos de uso; leitura de painéis e mapa por **consultas otimizadas** (read models/SQL), sem forçar tudo pelo ORM.

### Java moderno (21–25)
- **Virtual threads** (`spring.threads.virtual.enabled=true`) para I/O (banco, IA, arquivos) sem complicar com programação reativa.
- **Records** para DTOs e *value objects*; **sealed interfaces** + **pattern matching** (`switch`) para tipos de item de extração e estados.
- **Structured concurrency/scoped values:** avaliar conforme o status na versão adotada (confirmar se ainda é preview).
- Nulidade explícita com **JSpecify** (anotações `@NullMarked`), `Optional` só em retornos.

### Web e API
- **REST + OpenAPI 3.1** com **contrato primeiro** (`springdoc` ou geração a partir do contrato); cliente TypeScript do Angular **gerado** do mesmo contrato (OpenAPI Generator).
- Erros no padrão **Problem Details (RFC 9457)**, validação com **Jakarta Validation**, mapeamento com **MapStruct**.
- **Paginação por cursor (keyset)** nas listas grandes (imóveis sem ligação); **ETag/Cache-Control** nas leituras; compressão **Brotli/gzip**; HTTP/2.
- **Versionamento** de API (`/api/v1`) e *deprecation* explícita.
- Tempo real (alertas, progresso de extração): **Server-Sent Events** (mais simples que WebSocket e suficiente).

### Spring Security (núcleo da segurança)
- **Resource Server OAuth2/JWT** (`spring-boot-starter-oauth2-resource-server`) com validação de assinatura, `iss`, `aud`, `exp`.
- **Provedor de identidade:** começar com **Spring Authorization Server** (tudo em Java) ou **Keycloak** (tem recurso de organizações, útil para multi-tenant — confirmar). Decisão por ADR; ambos falam OIDC.
- **Autorização em camadas:**
  1. `SecurityFilterChain` para rotas;
  2. **method security** (`@PreAuthorize("hasAuthority('contratos.aprovar')")`) nos casos de uso;
  3. **escopo por unidade** em um `AuthorizationManager` próprio + filtro nas consultas;
  4. **RLS no PostgreSQL** como última barreira (documento 8).
- **Tenant e escopo no `SecurityContext`** a partir do token; um `TenantContext` define `SET LOCAL app.tenant_id` na transação.
- **MFA:** TOTP e **passkeys/WebAuthn** (suporte no Spring Security 6.4+; confirmar na versão adotada) e *one-time token* para convites e recuperação.
- Senhas com **Argon2id** (`Argon2PasswordEncoder`) ou bcrypt; **CSRF** ligado se houver cookie de sessão; **CORS** restrito; **CSP**, HSTS e demais cabeçalhos; **rate limiting** (Bucket4j) em login e exportações.
- **Tokens de acesso curtos** + *refresh* com rotação; revogação por sessão; cookies `HttpOnly`/`Secure`/`SameSite` se usar BFF.
- **Padrão BFF** (Backend-for-Frontend) como opção: o Angular conversa por cookie seguro e o servidor guarda os tokens, evitando tokens no navegador.
- Auditoria por eventos do Spring Security (`AuthenticationSuccess/Failure`) e por `ApplicationEvent`s de domínio.
- Verificação contra **OWASP ASVS nível 2** e **OWASP Top 10**; segredos fora do repositório.

### Dados e persistência
- **Flyway** para migrações versionadas (SQL puro), inclusive criação de políticas RLS.
- **Spring Data JPA/Hibernate** para escrita e agregados; **Hibernate Spatial** para geometria simples.
- **jOOQ** (ou `JdbcClient`) para **consultas analíticas e espaciais** de painel e mapa, com SQL explícito e tipado. Evitar N+1; usar *projections*.
- **HikariCP** dimensionado (não superdimensionar com virtual threads) e **PgBouncer** em produção.
- **Testcontainers** com imagem PostGIS para testes de integração (inclui RLS).
- Idempotência e **transações** claras; *outbox* se um dia houver mensageria.

### Processamento, jobs e cache
- **Spring Batch** para as **cargas mensais** (leitura em lote, *chunks*, reinício, relatório de erro) ou `COPY` direto para volumes grandes.
- **Agendamento** com `@Scheduled` + **ShedLock** (evita rodar duas vezes) para alertas a cada 15 min e verificação de fontes atrasadas.
- **Cache:** **Caffeine** (local) para catálogos e perfis; **Redis/Valkey** apenas se houver mais de uma instância e necessidade medida.
- **Mensageria (Kafka):** fora do MVP. Entra se houver integrações assíncronas pesadas (cargas contínuas, múltiplos consumidores). Antes disso, eventos internos do Modulith e *outbox*.
- **Extração de contrato** como tarefa assíncrona: fila em tabela (`SKIP LOCKED`) ou Spring Batch, com status e reprocessamento.

### IA no backend
- **Claude API** (SDK Java oficial da Anthropic — confirmar disponibilidade e versão) ou, alternativamente, **Spring AI** como camada de abstração.
- **Saída estruturada** com esquema JSON e **uso de ferramentas** (*tool use*) para o assistente consultar o banco com segurança.
- **Prompt caching** do texto do contrato para reduzir custo/latência em perguntas e reprocessamentos; **Batch API** para lotes.
- **Modelo configurável por propriedade** (nunca fixo no código); modelo mais barato para extração simples e mais forte para casos difíceis.
- **Avaliação contínua (evals):** conjunto de contratos de referência com respostas esperadas, rodado no CI quando prompt ou modelo mudar.
- Registrar `modelo`, `versao_prompt`, tokens e custo por chamada (observabilidade e governança).
- **LGPD:** não enviar dados pessoais à IA; contratos e métricas agregadas apenas; contrato de processamento com o provedor.

### Observabilidade e resiliência
- **Spring Boot Actuator** + **Micrometer** + **OpenTelemetry** (traces, métricas e logs correlacionados com `tenant_id`, sem dados pessoais).
- Painéis em **Grafana**, métricas em **Prometheus**, traços em **Tempo/Jaeger**, logs em **Loki** (ou equivalente do provedor).
- **Resilience4j** (timeout, retry, circuit breaker) nas chamadas à IA e a integrações.
- *Health checks* de liveness/readiness; **graceful shutdown**.

### Qualidade
- **JUnit 5**, **AssertJ**, **Mockito** (pouco), **Testcontainers**, **ArchUnit**, **Spring Modulith tests**, **REST Assured** ou `MockMvcTester`.
- **PIT** (mutation testing) nas regras de negócio críticas (adesão, situação, projeção).
- **Spotless** (formatação), **Checkstyle/PMD**, **SpotBugs/Error Prone**, **SonarQube/SonarCloud**, **JaCoCo** (cobertura como sinal, não como meta cega).
- Teste de carga com **k6** ou **Gatling**.

## 9.4 Banco — PostgreSQL 18 e PostGIS

### Modelagem e integridade
- **Chaves UUID v7** (ordenáveis por tempo; `uuidv7()` nativo no PostgreSQL 18 — confirmar) para entidades expostas; `bigint` identity para tabelas internas muito grandes.
- Restrições fortes: FKs, `CHECK`, `NOT NULL`, **exclusion constraints** (`btree_gist`) para evitar vigências de meta sobrepostas por unidade.
- Tipos corretos: `numeric` para percentuais, `date`/`timestamptz`, `jsonb` só para dados semiestruturados (valor original da IA), **enums** de domínio via tabelas ou `CHECK`.
- **`ltree`** (ou caminho materializado) para a hierarquia de unidades e resolução rápida de descendentes/escopo.
- **Row-Level Security** com `FORCE ROW LEVEL SECURITY` e usuário de aplicação sem `BYPASSRLS` (documento 8).

### Desempenho
- **Pré-agregação** de `adesao_mensal` (tabela atualizada na carga, ou **materialized view** com refresh incremental) para o painel responder em milissegundos.
- **Particionamento** declarativo de `imovel_situacao_mensal` por competência (e/ou por tenant em escala maior), com *partition pruning*.
- **Índices:** B-tree compostos começando por `tenant_id`; **GiST** (e **SP-GiST** quando fizer sentido) em geometria; **BRIN** em colunas temporais grandes e ordenadas; índices parciais (alertas abertos); *covering indexes* (`INCLUDE`).
- **Cargas** com `COPY` (ou `COPY` via driver) em tabelas de *staging* e `INSERT … SELECT`/`MERGE`.
- **Paginação por cursor** (keyset) e `EXPLAIN (ANALYZE, BUFFERS)` como hábito; `pg_stat_statements` ligado.
- **I/O assíncrono** e outras melhorias do PostgreSQL 18 beneficiam leituras sequenciais grandes (confirmar nas notas da versão).
- **PgBouncer** em modo transação; `work_mem`, `shared_buffers` e `max_connections` dimensionados por medição.

### PostGIS (mapa)
- Geometrias em **SRID 4326** para armazenamento e **SRID projetado local (SIRGAS 2000 / UTM)** para medições; coluna `geometry` com restrição de tipo e SRID.
- **Vector tiles direto do banco** com `ST_AsMVT`/`ST_AsMVTGeom` (ou servidor de tiles como **Martin** / **pg_tileserv** se a carga exigir), com **cache HTTP** e *tile cache*.
- **Simplificação** de geometrias por nível de zoom (`ST_Simplify`/`ST_SimplifyPreserveTopology`) e **clusterização** (`ST_ClusterDBSCAN`/*clustering* no cliente).
- Índices **GiST** + `ST_DWithin`/`&&` para consultas por viewport.
- **pgRouting** apenas se houver necessidade de rotas de campo (fora do escopo atual).
- Dados abertos: **CNEFE/IBGE** (geocodificação), **OpenStreetMap** (base e rede de apoio), com **atribuição visível** no mapa.

### Operação
- **Backups:** `pgBackRest` (ou serviço gerenciado) com **PITR**; teste de restauração mensal documentado.
- **Alta disponibilidade:** réplica de leitura/streaming quando o volume exigir; começar simples.
- **Migrações seguras:** *expand/contract*, sem bloqueio longo (`CREATE INDEX CONCURRENTLY`).
- **Extensões úteis:** `pg_stat_statements`, `pg_trgm` (busca por matrícula/unidade), `btree_gist`, `ltree`, `pgcrypto` (quando necessário), `pgaudit` (auditoria).
- **Segurança:** TLS, `scram-sha-256`, papéis mínimos (migração ≠ aplicação ≠ leitura), segredos fora do código.

## 9.5 Frontend — Angular 22

### Base moderna
- **Standalone components** (sem NgModules), **signals** para estado local e derivado, `computed`/`effect`, **`input()`/`output()`/`model()`**.
- **Zoneless** (sem Zone.js) e **OnPush como padrão** — melhor desempenho e menos código de detecção de mudanças (confirmar o estado exato na v22 nas notas oficiais).
- **Novo *control flow*** (`@if`, `@for`, `@switch`) e **`@defer`** para carregar mapa, gráficos e telas pesadas sob demanda.
- **Signal Forms** (formulários tipados baseados em signals) para metas, usuários, perfis; **Reactive Forms** só onde já existir necessidade (confirmar maturidade na v22).
- **`httpResource`/`resource`** para dados remotos reativos, com estados de carregamento e erro padronizados.
- **TypeScript estrito** (`strict`, `noUncheckedIndexedAccess`), **ESLint** com `angular-eslint`, **Prettier**.
- **Roteamento** com *lazy loading* por rota, *guards* funcionais e **resolvers**; título e foco gerenciados por rota.
- **i18n** pt-BR (`LOCALE_ID`, `Intl`), formatação de números, datas e moeda no padrão brasileiro.

### Estado e dados
- Estado local com **signals**; estado compartilhado complexo com **NgRx SignalStore** (leve) — evitar Redux completo sem necessidade.
- Cliente de API **gerado do OpenAPI**; interceptors para token/BFF, tenant, erros (Problem Details) e *retry* de leitura.
- **Cache de leitura** simples por chave (competência + escopo) com invalidação em eventos.

### Interface e experiência
- **Design system próprio** (documento 7) sobre **Angular CDK** (overlay, a11y, virtual scroll, drag-drop, listbox); **Angular Material** só como apoio, sempre tematizado pelos tokens.
- **Tabelas grandes:** `cdk-virtual-scroll` ou **AG Grid Community** para as listas de imóveis, com ordenação, filtro e exportação.
- **Gráficos:** **Apache ECharts** (bom desempenho, tema escuro, acessibilidade) para a série de adesão; tabela alternativa para leitores de tela.
- **Mapa:** **MapLibre GL JS 6** com tiles vetoriais do PostGIS, vista 3D, camadas e clusterização; **deck.gl** como camada opcional se houver centenas de milhares de pontos.
- **Paleta de comandos (Ctrl+K)** e atalho do assistente (Ctrl+J), **estados vazios, de carregamento (skeleton) e de erro** bem desenhados, **desfazer** em ações destrutivas, confirmação clara em ações sensíveis.
- **Resposta imediata:** atualização otimista quando seguro, *skeletons* em vez de *spinners*, **streaming** das respostas do assistente.
- **Acessibilidade WCAG 2.2 AA:** foco visível, contraste, navegação por teclado, `aria` correto, `prefers-reduced-motion`, `lang="pt-BR"`, testes com **axe-core**.
- **Responsivo** (desktop primeiro; tablet para o time de campo) e **tema claro/escuro** pelos tokens.
- **Entrega:** SSR/hidratação incremental só para páginas públicas (login/landing), se houver; o painel autenticado é SPA otimizada. **Service Worker** opcional para cache de ativos.

### Desempenho do front
- **Orçamentos de desempenho** no `angular.json` (tamanho de bundle) e **Lighthouse CI**.
- Metas de **Core Web Vitals:** LCP < 2,5 s, INP < 200 ms, CLS < 0,1 no painel.
- Carregamento preguiçoso de mapa e gráficos; *tree-shaking*; imagens e fontes otimizadas (`font-display: swap`, subconjuntos).
- Evitar renderizar milhares de linhas: virtualização e paginação por cursor.

### Testes do front
- **Vitest** (testes de unidade/componente), **Testing Library**, **Playwright** (E2E com dois tenants para provar isolamento), **Storybook** (catálogo do design system) com testes visuais, **axe** em CI.
- **MSW** para simular API em testes e desenvolvimento.

## 9.6 Plataforma, entrega e operação

- **Monorepo** (`backend/`, `frontend/`, `docs/`, `infra/`) ou repositórios separados — decisão por ADR; começar com **monorepo**.
- **Docker** com *multi-stage builds*, imagens mínimas (JRE *distroless*/Temurin), **Compose** para desenvolvimento (Postgres/PostGIS, IdP, Mailpit, Grafana stack).
- **CI/CD (GitHub Actions):** build, testes, qualidade (Sonar), segurança (**CodeQL**, **Trivy**, **OWASP Dependency-Check**, **Gitleaks**), SBOM (CycloneDX), publicação de imagem e **deploy automatizado**.
- **Git:** *trunk-based* com ramos curtos, **Conventional Commits**, *releases* semânticas, proteção de ramo e revisão obrigatória.
- **Ambientes:** local → homologação → produção, com configuração por variáveis e **feature flags** simples.
- **Hospedagem:** começar com **um único host/VPS com Docker Compose** (barato e suficiente para um projeto de estudo) e evoluir para **Kubernetes** ou serviço gerenciado quando a carga justificar; PostgreSQL gerenciado quando possível.
- **IaC:** **Terraform/OpenTofu** quando sair do host único.
- **Segredos:** variáveis de ambiente + cofre (ex.: Vault/Secrets Manager); nada no Git.
- **Backups e recuperação:** metas de RPO/RTO documentadas e **teste de restauração** periódico.

## 9.7 Segurança e privacidade (LGPD)

- **Minimização:** a Aurora guarda matrícula e ponto; sem nome nem endereço (RN-09). Matrícula é dado pessoal indireto: tratar com **base legal, finalidade, retenção** e **RIPD** quando aplicável.
- **Criptografia:** TLS em trânsito; **criptografia em repouso** (disco/volume/banco) e para campos sensíveis se surgirem.
- **Controle de acesso** em quatro camadas (rota, método, escopo, RLS) e **auditoria** de ações sensíveis.
- **Upload seguro** de PDF: validar tipo real (magic bytes), tamanho, antivírus, nome aleatório, armazenamento fora do webroot.
- **Cadeia de suprimentos:** dependências travadas, atualização contínua, SBOM e varredura de vulnerabilidades.
- **Resposta a incidentes:** runbook básico, contato do responsável e registro de incidentes.

## 9.8 Processo e qualidade

- **Definição de pronto:** código revisado, testes passando (unidade, integração, isolamento de tenant), sem vulnerabilidade crítica, documentação e ADR atualizados, migração testada, métricas/alertas criados.
- **Pirâmide de testes:** muitos de unidade (domínio), vários de integração (Testcontainers), poucos E2E (fluxos críticos: aprovar contrato, painel, mapa, exportar, isolamento de tenant).
- **Teste de arquitetura** (ArchUnit + Modulith) e **testes de contrato** da API (OpenAPI).
- **ADRs** curtos em `docs/adr/` (template: contexto, decisão, consequências).
- **Revisão de código** com checklist (segurança, desempenho, acessibilidade, testes).
- **Dados de demonstração** gerados por script reproduzível, claramente marcados como fictícios.

## 9.9 Metas de desempenho (SLOs) [Proposta, ajustar após medir]

| Fluxo | Meta |
|-------|------|
| API do painel (adesão do nível) | p95 < 300 ms |
| Lista "sem ligação" (50 linhas, com filtros) | p95 < 250 ms |
| Tile de mapa (com cache) | p95 < 150 ms; sem cache < 500 ms |
| Troca de competência no painel | < 400 ms percebidos |
| Carga mensal (~1 milhão de imóveis) | < 10 min, com relatório de erros |
| Recalcular adesão após a carga | < 2 min |
| Assistente: primeiro token | < 2 s |
| Extração de contrato de 50 páginas | < 2 min |
| Disponibilidade da API | 99,5% (projeto de estudo) |

## 9.10 Decisões e alternativas (ADRs iniciais)

| Decisão | Escolha | Por quê | Alternativas descartadas |
|---------|---------|---------|--------------------------|
| Estilo arquitetural | Monólito modular | Menos custo operacional, fronteiras verificadas | Microsserviços (complexidade sem necessidade) |
| Concorrência | Virtual threads | Escala I/O com código simples | WebFlux/reativo (curva e depuração mais difíceis) |
| API | REST + OpenAPI | Contrato, geração de cliente, cache HTTP | GraphQL (complexidade para o caso de uso) |
| Persistência | JPA para escrita + jOOQ para leitura analítica | Produtividade + controle do SQL | Só JPA (consultas analíticas ruins) |
| Multi-tenant | Banco compartilhado + RLS | Simples, seguro, barato | Schema/banco por tenant |
| Mapa | MapLibre + MVT do PostGIS | Sem custo de licença, desempenho | Google/Mapbox (custo, dados fora) |
| Front | Angular 22 (signals, zoneless) | Estrutura, tipagem, ecossistema e perfil do autor | React/Vue (fora da stack escolhida) |
| Identidade | OIDC (Authorization Server ou Keycloak) | Padrão, MFA, passkeys, SSO | Autenticação caseira |
| Jobs | Spring Batch + ShedLock | Robustez, reinício, simplicidade | Kafka/Quartz no início |

## 9.11 Fontes verificadas (21/10/2026)

- Spring Boot 4.1 / 4.0 / 3.5 (datas, suporte e Java): HeroDevs — *Spring Boot versions, EOL dates and latest releases* (julho/2026).
- Angular 22 (data e suporte): [angular.dev/reference/releases](https://angular.dev/reference/releases). Recursos da v22 (signals, zoneless, Signal Forms, Vitest, hidratação incremental): artigo secundário (CMARIX), **confirmar nas notas oficiais**.
- PostgreSQL 18 e datas de suporte: [postgresql.org/support/versioning](https://www.postgresql.org/support/versioning/).
- MapLibre GL JS 6.1.0: notas de versão no GitHub (maplibre/maplibre-gl-js).
- Itens marcados **(confirmar)** não foram verificados em fonte oficial nesta sessão.
