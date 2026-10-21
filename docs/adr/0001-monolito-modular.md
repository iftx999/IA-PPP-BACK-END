# 0001 · Monólito modular com Spring Modulith e camadas por módulo

**Contexto.** O domínio tem fronteiras claras (acesso, unidades, adesão, contratos, alertas, assistente, GIS),
mas o volume e a equipe não justificam o custo operacional de microsserviços (doc 09, 9.10).

**Decisão.** Um único deployable (`backend/`), com um módulo por pacote direto de `dev.aurora`. Cada módulo se
divide em `controller` (HTTP e DTOs de resposta), `service` (casos de uso e regras), `repository` (SQL) e `model`
(records e regras puras), mais `config` quando preciso. O Spring Modulith verifica no teste que nenhum módulo
usa os pacotes internos de outro. Erros compartilhados ficam em `comum`.

**Consequências.** Um build e um deploy só; fronteiras verificadas automaticamente. A separação em serviços,
se um dia for necessária, segue os módulos já existentes. Controllers nunca acessam o banco diretamente.
