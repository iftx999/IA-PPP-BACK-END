# 0004 · Schema reconstruído a partir do banco da versão anterior

**Contexto.** O código anterior foi perdido, mas o volume `aurora_postgres-dados` sobreviveu, com 22 migrações
aplicadas e dados de demonstração. O doc 04 era uma proposta a validar com esse banco.

**Decisão.** O schema real (`docs/referencia-schema-antigo.sql`) é a fonte de verdade em vez do doc 04. As
migrações V1–V11 o reproduzem com os mesmos nomes de tabelas e colunas, agrupadas por módulo. O script
`infra/scripts/importar-banco-antigo.sh` importa os dados de uma cópia do volume, nunca do original.

**Consequências.** Os dados de demonstração conferem com os números dos vídeos (ex.: 76,85% em ago/2026).
Diferenças em relação ao doc 04 (ex.: `medicao`/`indicador` em vez de `adesao_mensal`, `item_contratual` em vez de
`extracao_item`) seguem o banco real.
