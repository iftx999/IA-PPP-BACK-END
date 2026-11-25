# 0038 · Situação mensal dos imóveis particionada por ano

**Contexto.** `imovel_situacao_mensal` recebe uma linha por imóvel por mês, em todos os municípios do cliente: com
1 milhão de imóveis são 12 milhões de linhas por ano e por cliente, e a tabela só cresce (o histórico é a base da
adesão e das metas). Numa tabela única os índices crescem sem limite, o autovacuum demora a recalcular as
estatísticas depois de uma carga grande (foi o que levou o planejador ao laço aninhado de `docs/desempenho.md`) e
arquivar anos antigos exigiria um `DELETE` enorme.

**Decisão.**
- Particionada por `RANGE (competencia)`, **uma partição por ano** (`imovel_situacao_<ano>`), de 2000 a 2050,
  criadas de antemão pela migração V29. Ano e não mês: as consultas de uma competência leem só a partição do ano,
  e o histórico de um imóvel (todas as competências) continua tocando poucas partições.
- **Sem DDL em tempo de execução** e sem partição `DEFAULT`: competência fora da faixa falha na hora, em vez de
  cair numa partição que depois impediria criar a do ano. Antes de 2050, uma migração acrescenta os anos seguintes.
- RLS e permissões só na tabela-mãe. A aplicação não tem permissão nas partições: só chega aos dados pela mãe,
  com o RLS (o teste de isolamento do banco confere toda tabela com `tenant_id`).
- A carga distinguia inserção de atualização por `xmax = 0`, coluna de sistema que não existe em tabela
  particionada. Passou a usar `RETURNING old` do PostgreSQL 18 (`old` vazio = linha nova), que é a forma suportada;
  a gravação de localizações seguiu o mesmo padrão.

**Consequências.**
- Arquivar ou expurgar um ano é `DETACH PARTITION` (instantâneo), sem `DELETE` nem inchaço da tabela.
- O autovacuum analisa cada partição separadamente: depois da carga mensal, só a partição do ano corrente precisa de
  estatísticas novas.
- A migração copia os dados da tabela antiga dentro da transação do Flyway. Para a base de demonstração leva
  segundos. Numa base de produção já grande, a troca deve ser feita em janela de manutenção.
