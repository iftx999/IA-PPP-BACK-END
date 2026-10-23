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
6. ~~Marco 3~~ (feito em 22/10; falta a ingestão de cargas).
7. ~~Ingestão de cargas~~ (feito em 22/10).
8. ~~Marco 5 (mapa/GIS)~~ (feito em 23/10).
9. ~~Marco 4 (assistente)~~ (feito em 23/10; falta validar com uma chave real de IA).
10. ~~Marco 6 (refino)~~ (feito em 23/10).
11. Próximo: imagem Docker e pipeline de implantação da API e do front (com Tesseract), observabilidade
    (métricas/tracing) e as pendências restantes do doc 06 (regra factível × cortado, H9 autenticação segura).
- O E2E de integrações grava uma carga idempotente (matrículas `E2E-000x`) na competência mais antiga da lista.

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

### Marco 3 — Operação (F1–F3, H3, RN-07/08) — mesma sessão
- **Back-end** (ADR 0015): módulo `alertas` (adesão crítico/atenção, fonte atrasada, trilha alterada; avaliação a
  cada 15 min e verificação da trilha a cada 24 h por tenant, num contexto de sistema, com trava em
  `tarefa_agendada`; abre/atualiza/resolve sem duplicar; reconhecer auditado; `POST /alertas/avaliacao` reavalia
  na hora) e módulo `integracoes` (fontes com atraso máximo, última carga, histórico; credenciais do Agente Aurora
  com SHA-256, mostradas uma vez, revogáveis). `aurora.tarefas.habilitadas=false` desliga o agendamento.
- **Front**: `/alertas`, sino com contador, contador no menu, rodapé "Dados de <mês> · N fontes atrasadas",
  `/admin/integracoes`.
- **Correção importante**: após o login a pessoa podia cair no Painel em vez da tela pedida (a biblioteca OIDC
  reescrevia o endereço para "/" e navegava para `postLoginRoute` "/"). Retorno agora em `/entrar`, com o endereço
  pedido capturado no carregamento e guard próprio (`exigirLogin`). Os E2E conferem a rota após o login.
- Testes: back-end 115; front 29 unitários e 20 E2E (3 execuções seguidas sem falha).
- Ainda falta: alertas de ritmo e de prazo contratual, notificação por e-mail.

### Ingestão de cargas (H3, RN-01/08/09) — mesma sessão
- API do Agente Aurora (`/api/agente/v1/situacao` e `/cargas`, cadeia de segurança própria, credencial SHA-256,
  60 req/min) e carga por CSV na tela de Integrações. Validação por linha com recusas detalhadas, gravação em lote,
  recálculo das medições de adesão por subárvore, evento `CargaConcluida` → alertas reavaliados. ADR 0016;
  guia para quem instala o agente em `docs/integracao-agente.md`.
- Correção: alertas e resumo do sino quebravam em cliente ainda sem indicador de adesão.
- Testes: back-end 121; front 30 unitários e 20 E2E.

### Observações de teste
- Os E2E de Unidades deixam setores "Setor de Teste Renomeado" (inativos, sob a Cidade A) no banco de dev, como os
  de Perfis deixam perfis "Teste E2E …" se falharem no meio. O de convite cria usuários `convite.e2e.*` (desativados).
- Os E2E de integrações deixam fontes "Agente E2E …" inativas (não geram alerta).

### Pendências conhecidas
- Pendências de produto do doc 06 (regra factível × cortado, aditivos, OCR).
- Cadeia de auditoria importada é "legado" (algoritmo antigo desconhecido); eventos novos são verificáveis.
- Orçamento de bundle do Angular ainda no padrão (avaliar após as telas de administração).

## Sessão de 23/10/2026

### Marco 5 — Mapa/GIS (E1–E6) — ADR 0017
- **Back-end** (módulo `mapa`): `GET /mapa/camadas` (municípios com a malha do IBGE e o semáforo da adesão via
  `Medicoes.situacaoDas`, setores, ETE/EEE, competências, enquadramento); tiles vetoriais
  `GET /mapa/tiles/{z}/{x}/{y}.mvt` (`ST_AsMVT`; agrupamento numa grade 3×3 por tile até o zoom 14, um ponto por
  imóvel a partir do 15, rede a partir do 12; filtros de situação e de localização aproximada na URL; sem
  matrícula no tile); `GET /mapa/imoveis` (clique: até 10 imóveis perto, matrícula mascarada sem
  `dados_pessoais:ler`, exibição completa auditada); marcações (`mapa:marcar`, unidade mais específica que contém o
  ponto, só o autor remove, trilha só com IDs); `POST /mapa/exportacao` CSV/PDF do que está visível (PDF com a
  imagem do mapa, conferida pelo cabeçalho do PNG; até 50 mil imóveis; auditoria com SHA-256).
- **Agente (E6):** `POST /api/agente/v1/localizacoes` (matrícula, unidade, ponto, precisão); ponto corrigido em
  campo não é substituído. Guia em `docs/integracao-agente.md`.
- `Matricula` (máscara LGPD) passou para `dev.aurora.comum`.
- **Front** `/mapa`: MapLibre 6.12 (BSD-3) com fundo OSM escurecido no tema escuro; municípios coloridos pela
  adesão (destaque ao passar o mouse), setores, rede confirmada/presumida, casas coloridas por situação (contorno
  pontilhado = aproximada), agrupamentos com contagem, ETE/EEE, marcações; painel de camadas; vista 3D; marcar local
  (Esc cancela); exportar área (CSV/PDF); detalhe ao clicar; link direto `/mapa?lon=&lat=&zoom=`. Fundo configurável
  pelo token `FUNDO_DO_MAPA` (produção não pode usar os tiles públicos do OSM).
- Testes: back-end 138 (inclui `MapaApiTest`, localizações no `CargasApiTest`); front 37 unitários e 24 E2E.

### Observações
- Nas capturas do Playwright aparece um bloco escuro fixo no canto inferior direito do mapa. Os pixels do canvas
  ali estão corretos (conferido); é o compositor do Chromium headless. Conferir num navegador real.
- O E2E de marcação cria e remove a própria marcação ("Visita E2E …").

### Marco 4 — Assistente com fontes (G1–G6) — ADR 0018
- **Back-end** (módulo `assistente`): `POST /assistente/perguntas`, `GET /assistente/conversas` (busca),
  `GET /assistente/conversas/{id}`, `PUT /assistente/mensagens/{id}/avaliacao`, `GET /assistente/situacao`.
  Ferramentas fixas e estritas (`consultar_adesao`, `listar_unidades`, `listar_competencias`,
  `listar_alertas_abertos`) no escopo onde valem `assistente:usar` **e** `indicador:ler`
  (`ContextoAcesso.restritoATodas`). Fontes numeradas pelo servidor; verificação dos números (G6) grava
  `numeros_sem_fonte`. Conversas por pessoa (RLS), custo e limite mensal (429), auditoria só com IDs (`PERGUNTA_IA`).
  `Medicoes.detalhar` na API de adesão (sem duplicar RN-03/RN-06). V17.
- **Provedor configurável (ADR 0019):** `AURORA_IA_PROVEDOR=claude|openai`. Claude: `AURORA_IA_CHAVE`. OpenAI:
  `AURORA_IA_OPENAI_CHAVE`, `AURORA_IA_OPENAI_MODELO` (obrigatório), opcionais `AURORA_IA_OPENAI_ESFORCO` e
  `AURORA_IA_OPENAI_PRECO_ENTRADA/SAIDA/CACHE_LIDO`. Vale para o assistente e para a extração de contratos.
- **Front** `/assistente`: histórico com busca, chips de fonte e linha de dados, aviso de números sem fonte,
  copiar, avaliação; Ctrl+J; link "Perguntar ao assistente sobre …" no painel (G5).
- Testes: back-end 157 (Claude e OpenAI simulados); front 40 unitários e 27 E2E.

### Observações
- A chave da OpenAI colada no chat em 23/10 deve ser revogada; a nova vai só em variável de ambiente.
- Para ligar a IA no ambiente local: exportar as variáveis acima antes de `./mvnw spring-boot:run`.
- O E2E do assistente simula a resposta no navegador (o ambiente local roda sem chave); o caminho no servidor é
  coberto por `AssistenteApiTest` e `AssistenteComOpenAiApiTest`.

### Marco 6 — Refino (A5, B6, OCR) — ADRs 0020 e 0021
- **A5 aditivos:** aprovar META de documento ADITIVO encerra na véspera (ou substitui, se retroativo) as metas de
  contrato sobrepostas; versões anteriores em `meta_historico` (V18); item antigo `SUBSTITUIDO` com
  `substituido_por`; meta manual sobreposta → 409. `GET /metas/unidades/{id}/historico`; diálogo "Histórico de
  metas" na tela de Metas (também para quem só lê). Pendência 6 do doc 06 resolvida.
- **OCR:** Tesseract local em português nas páginas sem texto (300 DPI), `texto_por_ocr`, evento
  `TEXTO_RECONHECIDO_POR_OCR`; trecho da IA conferido com tolerância de 5% só no texto de OCR. Sem Tesseract, fica
  como antes (`SEM_TEXTO`). `AURORA_OCR_COMANDO` (vazio desliga). CI instala `tesseract-ocr-por`. Pendência 10
  resolvida. Exemplo: `aurora-frontend/e2e/exemplos/contrato-digitalizado.pdf` (`scripts/gerar-contrato-digitalizado.py`).
- **B6:** `GET /painel/exportacao.csv|pdf` (auditado), menu Exportar no painel. `RelatorioPdf` em `comum` (o PDF
  do mapa passou a usá-lo).
- CSV: número puro (ex.: `-0,30`) não é mais tratado como fórmula.
- Testes: back-end 166; front 40 unitários e 30 E2E.
