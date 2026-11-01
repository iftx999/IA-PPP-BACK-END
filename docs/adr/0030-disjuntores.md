# 0030 · Disjuntores nas chamadas à IA e ao provedor de identidade

**Contexto.** O doc 09 pede Resilience4j (timeout, retry, circuit breaker) nas chamadas à IA e a integrações. Os SDKs
de IA já têm tempo máximo e novas tentativas (`maxRetries`), e o Keycloak tem tempo de conexão e de leitura. Faltava o
disjuntor: com o provedor fora do ar, cada pergunta ao assistente ou cada envio de convite esperava o tempo máximo
(até 90 s na IA) antes de falhar, prendendo threads e a paciência de quem usa. As cargas são recebidas (o Agente chama
a Aurora), então não têm chamada de saída a proteger.

**Decisão.**
- **Resilience4j** só com `resilience4j-circuitbreaker` e `resilience4j-micrometer` (Apache 2.0, sem o starter do
  Spring Boot), usado por código em `comum.Disjuntores`/`Disjuntor`.
- **Dois disjuntores:** `ia` (assistente e extração de contratos, mesmo provedor) e `identidade` (Admin API do
  Keycloak, inclusive a importação dos eventos de acesso). Janela das últimas 20 chamadas, a partir de 10; abre com
  50% de falhas; fica aberto 30 s (`aurora.disjuntor.espera`) e então deixa passar 3 chamadas de teste.
- **O que conta como falha:** erro do serviço (5xx, rede, tempo esgotado, 401/403/429). Erro do próprio pedido não
  conta: 400/422 nos SDKs de IA; no Keycloak, 4xx como conta inexistente ou conflito.
- **Aberto:** falha na hora com a mesma mensagem de indisponibilidade que o usuário já via ("O serviço de IA está
  instável…", 503 do provedor de identidade); o documento de contrato fica para reprocessar.
- **Observabilidade:** estado e contagens em `resilience4j_circuitbreaker_*` (Prometheus); mudança de estado no log;
  alerta `DisjuntorAberto` (2 min aberto) em `infra/producao/observabilidade/alertas.yml`.

**Alternativas descartadas.** O `resilience4j-spring-boot` (anotações e configuração por propriedades): mais uma
camada de auto-configuração para acompanhar a cada versão do Spring Boot, para dois disjuntores. `@Retryable` do Spring
Framework 7: os SDKs já repetem, e não há disjuntor nele.

**Consequências.** Com o provedor de IA fora do ar, o assistente responde em milissegundos que está instável, em vez
de 90 s. Um surto de erros do próprio pedido não derruba o serviço para os outros. Os testes de unidade usam um
disjuntor isolado por cliente; os de integração, o compartilhado.
