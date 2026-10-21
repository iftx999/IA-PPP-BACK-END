# 0002 · Multi-tenant em banco compartilhado com Row-Level Security

**Contexto.** Vazamento de dados entre clientes é o risco mais grave do produto (doc 06). Schema ou banco por
tenant multiplicariam migrações e custo (doc 08, 8.3).

**Decisão.** Toda tabela de negócio tem `tenant_id` e política RLS com `FORCE ROW LEVEL SECURITY`. A API conecta
como `aurora_app` (sem `BYPASSRLS`) e, a cada conexão retirada do pool, define `app.tenant_id`, `app.usuario_id`
e `app.escopos` (caminhos `ltree` das unidades do usuário) a partir do token. Sem contexto, `app_tenant()` falha
(fechado por padrão). Migrações rodam como `aurora_owner`; extensões são criadas pelo superusuário na
inicialização do banco. Recurso de outro tenant ou fora do escopo responde 404.

**Consequências.** O isolamento vale mesmo com bug na aplicação. Testes automatizados conferem que toda tabela
com `tenant_id` acessível pela aplicação tem RLS forçado, e que a API não devolve dados alheios. Migrações que
leem tabelas com RLS precisam desligar o FORCE temporariamente (ver V7). Partições não recebem grants diretos.
