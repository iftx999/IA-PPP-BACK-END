# Progresso da reconstrução

> Diário de bordo para retomar o trabalho. Atualizado ao fim de cada sessão.

## Sessão de 21/10/2026

### Repositórios
- **Back-end, infra e docs:** `~/Iapp/aurora` → github.com/iftx999/IA-PPP-BACK-END
- **Front-end:** `~/Iapp/aurora-frontend` → github.com/iftx999/IA-PPP-FRONT-END-

### Entregue

**Marco 0 — Fundação**
- Docker Compose: PostgreSQL 18 + PostGIS + pgvector (porta 5433), Keycloak 26 (8180), Mailpit (8025).
- Schema reconstruído do banco antigo (migrações V1–V13), RLS forçado por tenant e escopo (ltree).
- Dados fictícios importados do volume antigo (`infra/scripts/importar-banco-antigo.sh`, só de cópia do volume).
- Qualidade: spring-javaformat, JaCoCo (piso 85%, atual ~92%), Spring Modulith, OpenAPI 3.1 com
  obrigatório/anulável exato, ESLint, `npm audit` no CI, Playwright E2E. ADRs 0001–0010.

**Marco 1 — Acesso e núcleo (parcial)**
| Item | Back-end | Front-end |
|------|----------|-----------|
| Login OIDC (Keycloak, tema próprio da Aurora) | ✅ | ✅ |
| Painel de adesão (B1–B5, B7) com projeção no ritmo | ✅ | ✅ |
| Imóveis sem ligação (D1–D3), matrícula mascarada (LGPD), CSV auditado | ✅ | ✅ |
| Metas (C1–C4): árvore, herança, meta própria, encerramento | ✅ | ✅ |
| Trilha de auditoria encadeada por HMAC (verificação de adulteração) | ✅ | — |
| Perfis e permissões (H6) | ✅ | ✅ |
| Usuários: convite, ativar/desativar, escopo (H2, H7, H8) | ⏳ | ⏳ |
| Cadastro de unidades (H1) | ⏳ | ⏳ |

**Interface (pedido do usuário):** tela de login refeita (tema Keycloak `infra/keycloak/temas/aurora`) e
componentes modernos com Angular Material tematizado (ADR 0010). PrimeNG descartado por exigir licença paga.

### Bugs encontrados pelos testes e corrigidos
- Login com `offline_access` recusado pelo Keycloak (SPA usa refresh token comum).
- Corrida no primeiro login (vínculo do usuário por e-mail agora é idempotente).
- Colisão de nomes de DTO na OpenAPI (agora falha na subida).
- Ciclo entre módulos acesso ↔ auditoria (ContextoAcesso movido para o módulo `contexto`).

### Como subir o ambiente
```bash
docker compose -f ~/Iapp/aurora/infra/compose.yaml up -d
cd ~/Iapp/aurora/backend && ./mvnw spring-boot:run          # API :8080 (Swagger: /swagger-ui.html)
cd ~/Iapp/aurora-frontend && npx ng serve --port 4200       # http://localhost:4200
```
Usuários (senha `aurora-dev`): `admin@exemplo.dev`, `diretoria@exemplo.dev`, `comercial.cidadea@exemplo.dev`,
`auditoria@exemplo.dev`. Console do Keycloak: `admin` / `admin-dev`.

Se o `ng serve` mostrar tela antiga ou erro 500 do Vite depois de trocar dependências: pare o processo da porta
4200, apague `.angular/cache` e suba de novo.

### Próximos passos (ordem sugerida)
1. **Usuários**: listar, convidar (Keycloak admin API + e-mail via Mailpit), ativar/desativar, concessões
   perfil+unidade, regra do último administrador (já existe a consulta em `PerfilRepository`).
2. **Unidades**: árvore, criar/editar/mover/inativar com recálculo do caminho ltree.
3. Permissão por unidade (hoje as permissões valem em todo o escopo do usuário; o banco concede perfil por unidade).
4. Marco 2: contratos + IA (upload, extração com Claude, revisão e aprovação).

## Sessão de 22/10/2026

### Entregue
- **Tela de Perfis (H6)** em `/admin/perfis` (`src/app/paginas/perfis/`):
  - Matriz perfis × permissões agrupada por área (cabeçalho e 1ª coluna fixos), com contagem por área.
  - Perfis do sistema abrem só para leitura, com "Duplicar" (caminho para personalizar).
  - Editor com caixa por área (todas/algumas/nenhuma) e por permissão; criar, editar, duplicar, remover
    (remoção bloqueada com usuários vinculados). Erros 409 da API (último administrador, nome repetido) aparecem
    no diálogo. Depois de salvar, recarrega `/eu` (a pessoa pode ter mudado o próprio perfil).
  - Sem `usuario:gerenciar`, a tela explica e não chama a API.
- Tipos da OpenAPI regenerados (`npm run api:gerar`) com os DTOs de perfis.
- Testes: unitários `perfis.spec.ts` (agrupamento e matriz) e E2E `e2e/perfis.spec.ts` (ciclo completo e
  acesso negado). Suíte: 13 unitários e 8 E2E passando.

### Observação
- Se um E2E de perfis falhar no meio, pode sobrar um perfil "Teste E2E …" no banco de desenvolvimento.

### Pendências conhecidas
- Pendências de produto do doc 06 (regra factível × cortado, aditivos, OCR).
- Cadeia de auditoria importada é "legado" (algoritmo antigo desconhecido); eventos novos são verificáveis.
- Orçamento de bundle do Angular ainda no padrão (avaliar após as telas de administração).
