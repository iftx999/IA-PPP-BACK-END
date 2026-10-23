# 0022 · Implantação em contêineres, host único e observabilidade

**Contexto.** O doc 09 (9.3, 9.6, 9.9) pede imagens mínimas, pipeline com varredura e SBOM, começar num host único
com Compose, saúde/métricas/traços correlacionados por tenant e metas de desempenho. A Aurora será usada por uma
empresa grande: a mesma imagem precisa servir homologação e produção, e a implantação não pode depender de passos
manuais frágeis.

**Decisão.**
- **Imagens.** API: multi-stage, JRE mínimo gerado com `jlink` (módulos do `jdeps` + `jdk.localedata` para pt-BR,
  `jdk.crypto.ec`, `java.naming`, `jdk.zipfs`, `jdk.charsets`) sobre Ubuntu com Tesseract (ADR 0021), usuário
  não-root, camadas do Spring Boot, encerramento gracioso. Front: nginx não-privilegiado, somente leitura, com CSP
  sem scripts inline, HSTS, `nosniff`, Referrer/Permissions-Policy, cache por hash e `/api` na mesma origem (sem
  CORS). `.mjs` servido como JavaScript (workers do mapa e do PDF). Distroless foi descartado: o OCR precisa do
  Tesseract.
- **Configuração por ambiente.** O front lê `/configuracao.json` (emissor OIDC, cliente, fundo do mapa) antes de
  iniciar; o nginx o gera das variáveis do contêiner. A API recebe tudo por variável de ambiente (perfil `prod`
  sem valores padrão de segredo).
- **Porta de gerenciamento.** Saúde, métricas Prometheus, info e SBOM na porta 8081, fora do proxy público, com
  cadeia de segurança própria (`@ConditionalOnManagementPort(DIFFERENT)`); demais endpoints negados. Na borda,
  `/actuator` responde 404.
- **Identidade pela rede interna.** O emissor validado no token é o público; chaves (JWKS) e Admin API do Keycloak
  são buscadas por `AURORA_IDENTIDADE_URL_INTERNA` (ex.: `http://keycloak:8080`), sem sair para a internet.
- **Produção num host** (`infra/producao/compose.yaml`, projeto `aurora-producao`): Caddy (TLS automático, console
  do Keycloak bloqueado na borda), front, API, PostgreSQL/PostGIS, Keycloak em modo produção com banco próprio e
  realm gerado do de desenvolvimento sem usuários e com segredos/endereços por `${VAR}` (política de senha de 12
  caracteres, bloqueio por tentativas, TOTP disponível), backup diário e, no perfil `observabilidade`, Prometheus
  (regras de alerta ligadas às metas do 9.9), Tempo e Grafana provisionado.
- **Observabilidade.** Micrometer → Prometheus; traços OpenTelemetry (OTLP) com amostragem configurável; logs JSON
  (ECS) com `traceId`, `spanId` e `tenant_id` (também no processamento assíncrono de contratos) — sem e-mail, nome
  ou matrícula. Métricas de negócio de baixa cardinalidade: perguntas do assistente por resultado, tokens e custo de
  IA, páginas e tempo de OCR, registros e duração das cargas.
- **Pipeline.** A cada push no `main`: imagens construídas, varridas pelo Trivy (crítica corrigível bloqueia,
  relatório na aba Security) e publicadas no GHCR com tag do commit, SBOM e proveniência. SBOM CycloneDX das
  dependências Java dentro do JAR (`/actuator/sbom`). Versões corrigidas acima das do Spring Boot ficam explícitas no
  `pom.xml` (ex.: Tomcat 11.0.25, Jackson 3.1.7/2.21.7, em 23/10/2026).
- **Novo cliente** por `provisionar-cliente.sh` (idempotente): tenant, emissor, unidade raiz, perfis do sistema e
  primeiro administrador, com convite por e-mail; sem SMTP, senha temporária entregue por canal seguro.
- **Backup** diário (Aurora e Keycloak, formato custom, retenção 14 dias) e restauração que preserva o banco anterior
  (renomeado) — `--clean` não serve porque o schema tem tabelas particionadas.

**Consequências.** Tudo foi validado nesta máquina: a suíte E2E completa (30) passou contra as imagens de produção,
e a pilha de produção inteira subiu com HTTPS local, provisionamento, primeiro acesso, OCR, traços, métricas,
painel e uma restauração após perda simulada. RPO 24 h e RTO de poucos minutos para a base atual; copiar
`infra/producao/backups` para fora do host é responsabilidade da operação. Kubernetes e Terraform ficam para quando
a carga justificar (doc 09).
