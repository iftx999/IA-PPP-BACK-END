# 0008 · Formatação, cobertura e documentação da API verificadas no build

**Contexto.** O produto será usado por uma grande empresa: qualidade não pode depender de disciplina manual.

**Decisão.** No `mvn verify`:
- **spring-javaformat** valida a formatação (corrigir com `./mvnw spring-javaformat:apply`);
- **JaCoCo** exige cobertura mínima de linhas (`cobertura.minima` no `pom.xml`, hoje 85%);
- testes de isolamento entre tenants e de fronteira de módulos.

Também:
- a API se documenta via **springdoc** (OpenAPI 3.1), desligada no perfil `prod`;
- o perfil `prod` não tem segredos padrão (a aplicação não sobe sem as variáveis) e emite logs estruturados (ECS).

**Consequências.** O CI barra regressões de estilo, cobertura e isolamento. Próximos passos: análise estática
(SpotBugs/Error Prone), varredura de dependências (OWASP/Trivy) e SBOM (CycloneDX).
