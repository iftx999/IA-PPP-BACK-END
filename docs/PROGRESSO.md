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
| Trilha de auditoria encadeada por HMAC (verificação de adulteração) | ✅ | ✅ |
| Perfis e permissões (H6) | ✅ | ✅ |
| Usuários: convite, ativar/desativar, escopo (H2, H7, H8) | ✅ | ✅ |
| Cadastro de unidades (H1) | ✅ | ✅ |

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
1. ~~Usuários~~ (feito em 22/10).
2. ~~Unidades~~ (feito em 22/10).
3. ~~Permissão por unidade~~ (feito em 22/10, ADR 0013).
4. ~~Auditoria (S8)~~ (feito em 22/10). Marco 1 concluído.
5. ~~Marco 2~~ (feito em 22/10; falta validar com a chave real da API e OCR).
6. Próximo: Marco 3 — Operação (alertas a cada 15 min, integrações/cargas e atraso de fonte, RN-07/RN-08).

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

### Usuários (H2, H7, H8) — mesma sessão
- **API** `/api/v1/usuarios`: listar, detalhar, convidar, reenviar convite, substituir concessões
  (perfil × unidade, validade opcional), ativar/desativar, enviar link de nova senha. Regras: último
  administrador, não desativar a si mesmo, e-mail único, unidade/perfil do tenant. Eventos auditados só com IDs.
- **Keycloak** pela Admin API (ADR 0011), porta `ProvedorIdentidade`; falha do provedor → 503 sem gravar nada.
  Migração V14 (`usuario.convidado_em`).
- **Tela** `/admin/usuarios`: busca, filtro por situação, paginação, diálogos de convite/acesso/desativação.
- Correção transversal: diálogos do Material cortavam em 560px (Perfis inclusive) → `MAT_DIALOG_DEFAULT_OPTIONS`.
- Testes: back-end 75 (inclui `UsuariosApiTest` e `KeycloakProvedorIdentidadeTest`); front 16 unitários e 10 E2E.

### Keycloak: client de administração
Client `aurora-api-admin` (conta de serviço com `manage-users`, `view-users`, `query-users`; segredo de dev
`api-admin-dev`) no `realm-aurora.json` e no Keycloak local. Precisa de `fullScopeAllowed: true`; sem isso o
token não leva os papéis de `realm-management` e a Admin API responde 403. Fluxo validado no E2E
`e2e/convite.spec.ts`: convite → e-mail no Mailpit → criação de senha → primeiro acesso → desativação.

### Unidades (H1) — mesma sessão
- **API**: `GET /unidades/arvore` (com inativas, filhas ativas e concessões de acesso), `GET /unidades/tipos`
  (hierarquia), `POST /unidades`, `PUT /unidades/{id}`, `PUT /unidades/{id}/pai` (mover), `PUT /unidades/{id}/situacao`.
  Regras e caminho ltree no ADR 0012. Tudo dentro do escopo de quem opera.
- **Tela** `/admin/unidades`: árvore com busca e teclado, painel de detalhes, diálogos de nova/editar/mover/inativar.
- Testes: back-end 83; front 19 unitários e 14 E2E.

### Permissão por unidade (RN-13) — mesma sessão
- Corrigida falha de segurança: permissões de todas as concessões se somavam em todo o escopo. Agora cada
  permissão vale só nas unidades onde foi concedida: `@ExigePermissao` (substitui `@PreAuthorize`) restringe o
  escopo da requisição e, com ele, `app_escopos()`/RLS. Matrícula sem máscara exige `dados_pessoais:ler` na
  unidade consultada. ADR 0013; teste `PermissaoPorUnidadeApiTest`. Back-end: 90 testes.
- **Novos endpoints devem usar `@ExigePermissao`**; sem a anotação, a requisição usa a união das concessões.

### Auditoria (S8) — mesma sessão
- **API** `/api/v1/auditoria`: `eventos` (filtros pessoa/tipos/período/unidade com subárvore, cursor cifrado,
  nomes legíveis), `eventos/exportacao.csv` (auditada, exige também `relatorio:exportar`), `tipos`, `autores`,
  `verificacao`. Exige `auditoria:ler` na unidade raiz. Índices V15.
- **Tela** `/admin/auditoria` (menu Administração). No banco herdado só o perfil **Auditor** tem `auditoria:ler`
  (o administrador não consulta a trilha): segregação de funções mantida; o E2E usa `auditoria@exemplo.dev`.
- Correção: diálogo "Encerrar meta" recarregava a página ao enviar (form sem `[formGroup]`). O `npm run lint`
  (e o CI) agora falha em qualquer `<form>` sem `[formGroup]`.
- Testes: back-end 93; front 22 unitários e 16 E2E. **Marco 1 concluído.**

### Marco 2 — Contratos + IA (A1–A4) — mesma sessão
- **Back-end** (módulo `contratos`, ADR 0014, V16): contratos; envio de PDF (validação, SHA-256, PDFBox);
  processamento assíncrono no contexto de quem enviou (texto por página → cláusulas → Claude → validação do trecho
  literal → itens `PROPOSTO`); reprocessar; itens manuais; editar preservando a proposta da IA; aprovar (META vira
  meta oficial com origem "Contrato N, cláusula X, p. Y" via `Metas.definirDoContrato`); rejeitar com motivo.
- **IA**: SDK oficial `anthropic-java` 2.34, `claude-opus-5-5`, saída estruturada, esforço `high`, cache do prompt,
  fallback de recusa. **Para ligar:** `export AURORA_IA_CHAVE=<chave da API>` antes de subir a API (sem ela, o
  texto é lido e os itens são cadastrados à mão; a tela avisa). Testado com API simulada (`ExtratorClaudeTest`);
  ainda não rodou contra a API real.
- **Front**: `/contratos` (lista, novo) e `/contratos/:id` (documentos com acompanhamento ao vivo, itens, revisão
  lado a lado com o PDF desenhado pelo pdf.js na página citada e o trecho destacado).
- Contrato fictício: `aurora-frontend/e2e/exemplos/contrato-exemplo.pdf` (gerado por
  `node scripts/gerar-contrato-exemplo.mjs`).
- Testes: back-end 106; front 26 unitários e 18 E2E.
- Pendentes do épico A: OCR de PDF digitalizado (hoje `SEM_TEXTO` com orientação); aditivos (A5) são aceitos como
  tipo de documento, mas a regra de substituir metas por aditivo ainda não existe (pendência do doc 06).

### Observações de teste
- Os E2E de Unidades deixam setores "Setor de Teste Renomeado" (inativos, sob a Cidade A) no banco de dev, como os
  de Perfis deixam perfis "Teste E2E …" se falharem no meio. O de convite cria usuários `convite.e2e.*` (desativados).
- Uma vez, depois do login, o E2E caiu no Painel em vez da rota pedida; não se repetiu em 4 execuções da suíte.
  Se voltar, investigar a restauração de rota do `angular-auth-oidc-client`.

### Pendências conhecidas
- Pendências de produto do doc 06 (regra factível × cortado, aditivos, OCR).
- Cadeia de auditoria importada é "legado" (algoritmo antigo desconhecido); eventos novos são verificáveis.
- Orçamento de bundle do Angular ainda no padrão (avaliar após as telas de administração).
