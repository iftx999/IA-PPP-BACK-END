# 0006 · JdbcClient com SQL explícito para leituras analíticas

**Contexto.** Painel, mapa e listas fazem agregações, consultas hierárquicas (`ltree`) e espaciais (PostGIS), em
que ORM gera SQL ineficiente ou não expressa a consulta (doc 09: "CQRS leve").

**Decisão.** Os repositórios usam `JdbcClient` com SQL explícito e mapeamento para records. JPA fica disponível
para escrita de agregados quando trouxer ganho. Funções de contexto (`app_escopos()`) são usadas direto no SQL.

**Consequências.** As consultas são revisáveis e otimizáveis com `EXPLAIN`. A cobertura vem de testes de
integração com Testcontainers na mesma imagem do banco de produção.
