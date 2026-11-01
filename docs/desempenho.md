# Desempenho medido (doc 09, 9.9)

Como medir: [`infra/carga/README.md`](../infra/carga/README.md). Ambiente das medições abaixo: notebook de
desenvolvimento (Linux, PostgreSQL 18 + PostGIS em Docker, API com `spring-boot:run`), dados de demonstração.

## 01/11/2026

| Fluxo | Meta | Medido (p95) | Situação |
|-------|------|--------------|----------|
| API do painel | < 300 ms | 61 ms | ✅ |
| Troca de competência no painel | < 400 ms | 68 ms | ✅ |
| Lista "sem ligação" (50 linhas, com filtros) | < 250 ms | 108 ms | ✅ |
| Tile de mapa, sem cache | < 500 ms | 71 ms | ✅ |
| Carga mensal (~1 milhão de imóveis) | < 10 min | 72,6 s (13,8 mil imóveis/s) | ✅ |
| Recalcular a adesão após a carga | < 2 min | 2,2 s (400 setores) | ✅ |

| Core Web Vitals do painel: LCP | < 2,5 s | 0,51 s | ✅ |
| Core Web Vitals do painel: CLS | < 0,1 | 0,007 | ✅ |
| Core Web Vitals do painel: INP (TBT como substituto em laboratório) | < 200 ms | 131 ms | ✅ |

Front: Lighthouse (configuração desktop) no build de produção, três execuções por tela, mediana; Sem ligação: LCP
0,60 s, CLS 0,007, TBT 87 ms. Bundle inicial de 904 kB (205 kB comprimidos), com limites no `angular.json` logo
acima do medido. Medir com a máquina ocupada distorce o TBT (a primeira rodada, com os testes do back-end em
paralelo, deu 667 ms).

Leituras: k6 a 40 requisições/s por 2 min (4.817 requisições, 0% de erro), 13 unidades e 14 competências de
demonstração (~140 mil linhas de situação mensal, 11,6 mil imóveis no mapa).

**Correção encontrada pela medição:** a primeira rodada da carga de 1 milhão levou 727 s, acima da meta. O recálculo
da adesão percorria todos os imóveis da competência uma vez para cada unidade (~400 × 500 mil linhas quando o
planejador escolhia laço aninhado, típico logo após uma carga grande, com estatísticas desatualizadas). Passou a
contar uma vez por unidade e somar nos níveis acima pela hierarquia `ltree`: 203 s → 2 s no pior caso, com
resultado idêntico ao anterior (conferido nas 104 medições de demonstração). A mesma rodada mostrou que as tarefas
agendadas rodavam durante os testes (propriedade registrada tarde demais); agora ficam desligadas por
`src/test/resources/config/application.yaml`.

Ainda não medidos: primeiro token do assistente (< 2 s) e extração de contrato de 50 páginas (< 2 min), que
dependem de chave real de IA; disponibilidade (99,5%), que só se mede em produção.
