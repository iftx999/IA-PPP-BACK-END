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
11. ~~Implantação e observabilidade~~ (feito em 23/10, ADR 0022, runbook em `docs/operacao.md`).
12. ~~MFA (H9/S9)~~ (feito em 26/10, ADR 0023).
13. Próximo: SSO corporativo (S10), regra factível × cortado (doc 06, depende do autor), validação da IA com chave
    real, alertas de ritmo e de prazo contratual, notificação por e-mail.
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

### Implantação e observabilidade — ADR 0022
- **Imagens:** API (JRE mínimo via jlink, Tesseract, não-root, porta de gerenciamento 8081, 690 MB) e front (nginx
  não-privilegiado, somente leitura, CSP sem scripts inline, `/configuracao.json` por ambiente, 96 MB). Trivy:
  0 vulnerabilidades altas/críticas corrigíveis (Tomcat 11.0.25 e Jackson corrigidos no `pom.xml`).
- **Produção num host:** `infra/producao/` (compose `aurora-producao`, Caddy com TLS, Keycloak produção com realm
  gerado por `infra/keycloak/gerar-realm-producao.py`, backup diário, Prometheus/Tempo/Grafana), scripts
  `provisionar-cliente.sh` e `restaurar-backup.sh`.
- **Observabilidade:** métricas Prometheus (incl. negócio), traços OTLP, logs ECS com `traceId`/`tenant_id`, painel
  Grafana e 6 regras de alerta ligadas às metas do doc 09.
- **CI:** workflows `imagens.yml` (API e banco) e `imagem.yml` (front): build, Trivy, publicação no GHCR com SBOM.
- **Validações feitas nesta máquina:** E2E completa (30) contra as imagens de produção; pilha de produção inteira com
  HTTPS local (`aurora.localhost`), provisionamento de cliente, primeiro acesso com troca de senha, OCR, traços no
  Tempo, painel no Grafana, perda simulada e restauração.
- Correções que só apareceram em produção: workers `.mjs` com tipo errado, scripts inline bloqueados pela CSP,
  emissor OIDC fixo no código, chaves do Keycloak buscadas pelo endereço público, tiles com 422 em cliente sem carga,
  restauração com `--clean` incompatível com tabelas particionadas, `.env` de produção fora do `.gitignore`.
- **Atenção:** nunca nomear projeto Compose como `aurora` (colide com o volume antigo `aurora_postgres-dados`).
- Testes: back-end 169; front 42 unitários e 30 E2E.

## Sessão de 26/10/2026

### Verificação em duas etapas (H9, S9, RN-17) — ADR 0023
- **Keycloak:** fluxo `aurora-browser` (cópia do `browser` com referência `pwd`/`otp` em cada etapa) e mapeador `amr`
  no client `aurora-web`, nos realms de desenvolvimento e de produção. O código é pedido a quem já cadastrou TOTP.
- **API:** política conferida a cada requisição: obrigatória para quem tem `usuario:gerenciar`
  (`AURORA_SEGUNDO_FATOR_ADMINISTRADORES`, padrão ligado; **desligada no perfil dev**) e para todos quando o tenant
  liga (V19, `tenant.segundo_fator_obrigatorio`). Sem `otp` no `amr` → 403 `SEGUNDO_FATOR_NECESSARIO` e ação
  `CONFIGURE_TOTP` no Keycloak. O login em que o TOTP é cadastrado vale (TOTP criado após o `auth_time`).
  `GET/PUT /seguranca/segundo-fator`, `GET/DELETE /usuarios/{id}/segundo-fator`; eventos
  `POLITICA_SEGUNDO_FATOR_ALTERADA` e `SEGUNDO_FATOR_REMOVIDO`.
- **Front:** aviso "Verificação em duas etapas" com "Entrar com o código" (`prompt=login`, volta à mesma tela),
  interceptador para recusas no meio da sessão, cartão da política em Usuários e diálogo por pessoa (situação e
  remover aplicativo).
- **Validação real** (Keycloak + API locais): política ligada → token só com senha recusado → novo login com
  cadastro do TOTP aceito → login seguinte com `amr` `pwd`+`otp` aceito.
- **Correção:** `infra/postgres/Dockerfile` usava `COPY --chmod` (exige BuildKit); o Testcontainers constrói com o
  builder legado e toda a suíte do back-end falhava desde o commit do gosu. Agora `COPY` + `RUN chmod`.
- Testes: back-end 178 (`SegundoFatorApiTest`, provedor); front 43 unitários e 32 E2E.

### Observações
- Para testar o MFA de verdade no ambiente local: `AURORA_SEGUNDO_FATOR_ADMINISTRADORES=true ./mvnw spring-boot:run`
  ou ligar "Exigir de todos" em Usuários. Depois, remova o OTP de `admin@exemplo.dev` (console do Keycloak, aba
  Credenciais) ou recrie o contêiner do Keycloak; senão os E2E param na tela do código.
- Recriar o contêiner do Keycloak reimporta o realm e apaga usuários criados pelos E2E (os de demonstração têm IDs
  fixos e continuam vinculados).


## Sessão de 31/10/2026

### Alertas de ritmo e de prazo contratual e e-mail (RN-05, RN-07) — ADR 0024
- **Ritmo:** `Medicoes.projecoes()` reaproveita a `Projecao` do painel; alerta `META_FORA_DO_RITMO` quando a meta com
  prazo não sai no ritmo atual (Crítico se a projeção fica abaixo do limite de alerta).
- **Prazo contratual:** itens `PRAZO` aprovados (60 dias antes; Crítico com 15 ou vencido, até ser marcado como
  cumprido) e fim da vigência do contrato (180 dias antes; Crítico com 30). `POST/DELETE
  /contratos/itens/{id}/cumprimento` (auditado). Na tela do contrato: selo "Vence em N dias"/"Venceu"/"Cumprido" e
  botões "Marcar como cumprido"/"Reabrir prazo".
- **E-mail:** tarefa `notificar-alertas` a cada 15 min; um resumo por pessoa com os alertas novos que ela pode ver;
  preferência Nenhum / Só críticos (padrão) / Todos em Alertas (`/alertas/preferencias`). SMTP por `AURORA_SMTP_*`
  (vazio desliga); no ambiente local vai para o Mailpit (http://localhost:8025). V20.
- **Validação real:** prazo vencendo em 5 dias no banco local → alerta crítico → 2 e-mails no Mailpit (administração
  e diretoria), conforme a preferência padrão.
- Testes: back-end 187 (`NotificacaoAlertasApiTest`, `PrazosApiTest`, regras e mensagem); front 44 unitários e 33 E2E.
  Testes do back-end agora usam pool com poucas conexões ociosas (muitos contextos em cache esgotavam o banco).

### Branches
- `feat/verificacao-em-duas-etapas` e `feat/alertas-ritmo-prazo-email` enviadas ao GitHub (o SSH voltou a funcionar).

### SSO corporativo (S10) — ADR 0025 — mesma sessão (branch `feat/sso-corporativo`)
- **API** `GET/PUT/DELETE /seguranca/sso`: um IdP por tenant (apelido `corporativo`), OIDC (descoberta + client ID +
  segredo) ou SAML (metadados), criado no realm do tenant pela Admin API. Segredo só no Keycloak (nunca devolvido;
  vazio mantém). Endereço só HTTPS e público fora do dev. Auditoria `SSO_CONFIGURADO`/`SSO_REMOVIDO` sem dados
  sensíveis. V21 `tenant.sso_confia_segundo_fator`.
- **Keycloak:** fluxos `aurora-sso-primeiro-login` (só vincula contas convidadas) e `aurora-sso-pos-login` (TOTP para
  quem cadastrou, `amr` com `otp`), mapeador `idp`, papéis de IdP na conta de serviço, mensagem de recusa no tema.
  Realm `corporativo-exemplo` no compose de dev. Instalações existentes: `infra/producao/atualizar-realm-sso.sh`.
- **Correção:** o realm de produção gerado removia a conta de serviço da API (convites falhariam em produção).
- **Front:** cartão "Login corporativo (SSO)" em Usuários com diálogo (protocolo, endereços para registrar no IdP com
  copiar, dados do IdP, mostrar botão, confiar no MFA do IdP, remover com confirmação). Rótulos na Auditoria.
- **Validação real:** OIDC e SAML contra o realm de exemplo; convidada entra, sem convite é recusada; segredo mantido
  ao editar; TOTP pedido após o SSO; confiança no MFA do IdP; troca de protocolo; remoção. Script de atualização
  testado num realm da versão anterior (2 execuções).
- Testes: back-end 195; front 46 unitários e 37 E2E (`e2e/sso.spec.ts`).

### Prazos e obrigações recorrentes — ADR 0026 — mesma sessão (branch `feat/prazos-recorrentes`)
- Nos contratos lidos, quem se repete são obrigações sem data (só o texto "trimestral", "anual"...). Agenda no item
  aprovado (`PUT /contratos/itens/{id}/agenda`: primeiro vencimento + recorrência), com sugestão a partir do texto.
- Cumprimento por vencimento (V22 `item_contratual_cumprimento`, cumprimentos da V20 migrados); cumprir avança ao
  próximo, desfazer volta o último; respeita o fim da vigência. Obrigações com data passam a ser acompanhadas.
- Alerta por vencimento (`PRAZO:<item>:<data>`) com antecedência proporcional ao período (mensal: 15/7 dias).
- Front: seção "Vencimentos" no item e aviso no contrato ("15 obrigações recorrentes sem data" no contrato 001/2025).
- Validação: migração da V20 conferida no banco de dev; E2E de contratos cobre definir, cumprir, avançar e desfazer.
- Testes: back-end 218; front 47 unitários e 37 E2E.

### Administração da plataforma (S1) — ADR 0027 — mesma sessão (branch `feat/administracao-plataforma`)
- Realm `plataforma` (usuário de dev `plataforma@exemplo.dev` / `aurora-dev`), área `/plataforma` no front, API
  `/api/v1/plataforma/clientes` (listar, criar, alterar, logo, concluir provisionamento, reenviar convite) só para esse
  emissor, com `otp` obrigatório fora do dev.
- Banco V23: `tenant.codigo` (clientes antigos = nome do realm), limites, logo, contato, `provisionado_em`; contexto de
  plataforma (`app.plataforma`) só em tenant/tenant_emissor/assistente_limite; gatilho impede o cliente de alterar o
  próprio cadastro.
- Criar cliente = realm novo (modelo gerado do realm de produção; conta do master só com `create-realm`), raiz,
  perfis do sistema e convite do 1º administrador pelo fluxo de Usuários; etapas idempotentes e retomáveis.
- Front: tela "Código da empresa" (`/acesso`), `?cliente=` (o link do convite já traz), logo e "Trocar de empresa"
  no menu; limite de usuários conferido ao convidar e reativar.
- Produção: `realm-plataforma-producao.json`, `configurar-plataforma.sh`, `criar-operador-plataforma.sh`,
  `AURORA_VARIOS_CLIENTES`, `AURORA_PLATAFORMA_SEGREDO`; corrigido `provisionar-cliente.sh` (código do cliente).
- Validação real: operador criou "Grupo Saneamento Norte" (realm `grupo-norte`), falha de SMTP deixou o
  provisionamento pendente e "concluir" retomou; E2E cria cliente, a 1ª administradora aceita o convite, entra no realm
  novo e vê a logo.
- Testes: back-end 225; front 53 unitários e 41 E2E (todos os E2E agora passam pela tela do código).

### Pendências
- Próximo: eventos de login na trilha de auditoria (doc 08, 8.7), "sair de todos os dispositivos", retenção da
  auditoria, testes de carga; dependem do autor: regra factível × cortado; da chave real: validação da IA.
- Se o E2E de SSO falhar no meio, o botão "Empresa Exemplo (E2E)" fica no login até remover o SSO em Usuários.

## Sessão de 01/11/2026

### Regra factível × cortado (pendência 5 do doc 06) — definida pelo autor
- **Factível:** a rede pública passa na rua e está à disposição, mas o imóvel (ou economia) ainda não fez a conexão
  física. **Cortado:** tem conexão física, com o serviço interrompido por falta de pagamento ou suspenso a pedido do
  cliente. Nenhum cálculo muda: os dois já contavam em "com rede" e em "sem ligação" (RN-01, RN-02).
- Atualizados: RN-02 (doc 03), doc 06, `integracao-agente.md`, `SituacaoImovel`, instruções do assistente
  (`assistente-v2`, com o glossário das situações), dicas nas abas e no selo de "Sem ligação" e ajuda da carga CSV.
- Ideias em aberto, se o sistema comercial enviar o dado: motivo do corte (inadimplência × a pedido) e aviso na carga
  quando um imóvel que já esteve ligado volta como factível.

### Trilha de acesso e sessões (doc 08, 8.6 e 8.7) — ADR 0028 — branch `feat/sessoes-e-trilha-de-acesso`
- Keycloak grava login, falha, bloqueio por tentativas e logout (30 dias); a tarefa `importar-eventos-de-acesso`
  copia para a trilha de cada tenant (5 min; 20 s no dev), sem duplicar (V24 `evento_acesso_importado`) e sem o nome
  digitado no login. Tipos novos: `LOGIN_FALHOU`, `CONTA_BLOQUEADA`, `LOGOUT`, `SESSOES_ENCERRADAS`.
- "Sair de todos os dispositivos" no menu da conta (`DELETE /api/v1/eu/sessoes`) e "Encerrar sessões" em Usuários
  (`DELETE /api/v1/usuarios/{id}/sessoes`).
- Realms existentes: `infra/producao/atualizar-realm-eventos.sh` uma vez por realm (rodado no dev em `aurora` e
  `grupo-norte`). `realm-aurora.json` e os modelos gerados já trazem a configuração.
- Menu lateral só com as telas que o perfil pode abrir; a entrada (`/`) e o retorno do login vão para a primeira tela
  permitida (o Auditor caía num Painel com erro). Painel, Metas e Sem ligação ganharam o aviso "Sem acesso".
- Testes: `TrilhaDeAcessoApiTest` (6), provedor Keycloak (+2), front +3 unitários, E2E `sessoes.spec.ts` (2).

### Retenção da auditoria (doc 08, 8.7) — ADR 0029 — branch `feat/retencao-da-auditoria`
- Prazo por cliente (12–240 meses, padrão 60) em Usuários › Retenção da auditoria (`usuario:gerenciar`); a Auditoria
  só mostra (botão "Retenção"). Expurgo diário só do começo da cadeia, por função `SECURITY DEFINER` (a API segue sem
  `DELETE`), com âncora conferida pela verificação e evento `TRILHA_EXPURGADA`. Partições anuais criadas com 2 anos
  de antecedência.
- Testes: `RetencaoDaAuditoriaApiTest` (5), front +1 unitário, E2E `retencao.spec.ts`.

### Motivo do corte e aviso de consistência (RN-02) — branch `feat/motivo-do-corte`
- Carga (agente `motivoCorte`, CSV `motivo_corte`): `INADIMPLENCIA` ou `A_PEDIDO`, só para CORTADO (V26, `CHECK` no
  banco). Sem ligação mostra o motivo na lista, a contagem por motivo na dica da aba Cortados e a coluna no CSV.
- Factível que já esteve ligado ou cortado em competência anterior: gravado com aviso (`comAviso`, 100 primeiros em
  `avisos`), visível no resultado da carga e no histórico de execuções.
- Testes: validador (+1), `CargasApiTest` (+1), `SemLigacaoApiTest` (+1), spec do front atualizado.

### Disjuntores (doc 09, 9.3) — ADR 0030 — branch `feat/disjuntores`
- Resilience4j (núcleo + Micrometer) nos clientes de IA (assistente e extração, disjuntor `ia`) e no Keycloak
  (`identidade`). Erros do próprio pedido não contam; aberto, falha na hora com a mensagem de indisponibilidade.
- Alerta `DisjuntorAberto` no Prometheus de produção.
- Testes: `DisjuntorTest` (3), `DisjuntoresTest` (1), provedor Keycloak (+1).

### Arquitetura verificada e testes de mutação (doc 09, 9.3 e 9.8) — ADR 0031 — branch `feat/qualidade-arquitetura`
- `ArquiteturaTest` (ArchUnit, 7 regras de camadas; corrigido o `MapaController` que lia constantes do repositório).
- PIT no perfil `mutacao` (CI): adesão, situação, projeção e alertas com mínimo de 95%; medido 78% → 98% com
  `FronteirasDosAlertasTest` (13) e fronteiras em `RegrasDeAdesaoTest` (+12).

### Análise estática e dependências (doc 09, 9.3 e 9.6) — ADR 0032 — branch `feat/qualidade-arquitetura`
- SpotBugs + FindSecBugs (perfil `analise`, CI): 165 achados triados; corrigidos JSON de erro nos filtros
  (`RespostaDeProblema`), escape da pergunta do assistente, NPE capturado, array estático, nome de arquivo
  temporário e operador sem `sub`; demais excluídos com motivo em `spotbugs-excluir.xml`.
- Trivy nas dependências do `pom.xml` (crítica corrigível bloqueia; SARIF) e CodeQL nos dois repositórios.
- Varredura local em 01/11/2026: nenhuma vulnerabilidade alta ou crítica no back-end nem no front-end.

### Acessibilidade com axe (doc 09, 9.5) — front, branch `feat/qualidade-acessibilidade`
- `e2e/acessibilidade.spec.ts`: axe (WCAG 2.1 A/AA) em 12 telas nos dois temas, mais a tela do código; violação
  séria ou crítica reprova. A primeira rodada achou contraste insuficiente (tokens `--text-3` e `--danger` no escuro;
  `--accent`, `--ok`, `--warn`, `--danger` e `--neutral` no claro), botão da conta com nome acessível diferente do
  texto, ícones sem `aria-hidden`, lista rolável sem foco e link só por cor; tudo corrigido (doc 07 atualizado).
- Os E2E (e o axe) rodam com o ambiente completo, fora do CI do front.

### Antivírus nos envios (doc 09, 9.7) — ADR 0033 — branch `feat/antivirus`
- ClamAV no compose (dev e produção), cliente INSTREAM próprio. PDF de contrato e logo do cliente passam pelo
  antivírus antes de gravar; ameaça recusa (422) e vai para a trilha; obrigatório em produção (503 se fora do ar).
- Testes: `ClamAvAntivirusTest` (4, um contra o ClamAV real com EICAR), `AntivirusNoEnvioApiTest` (3).

### Testes de carga (doc 09, 9.9) — ADR 0034 — branch `feat/testes-de-carga`
- k6 nas leituras (`infra/carga`) e `DesempenhoDaCargaTest` (1 milhão de imóveis). Todas as metas medidas foram
  cumpridas (`docs/desempenho.md`).
- Corrigido: recálculo da adesão quadrático no pior plano (727 s → 72,6 s na carga de 1 milhão), tarefas agendadas
  rodando nos testes, tempos por fase no log da carga.

### Simulador de penalidades (épico I, I1) — ADR 0035 — branch `feat/simulador-de-penalidades`
- Escolhido pelo autor depois de concluídas as histórias A–H, junto com campanhas de ligação e ordens de serviço
  (desenho aprovado; próximo marco, histórias I2–I4 no doc 02).
- **Regra calculável** na penalidade aprovada (V27 `regra_penalidade`): fórmula, valor, base em reais, contagem,
  aferição, teto. Seção "Cálculo da multa" no diálogo do item (sugere fórmula e valor pelo texto), auditada.
- **Simulador** `/simulador` (`GET /api/v1/simulador`): ritmo atual × cenário, mês a mês até 0–60 meses após o prazo,
  multa por aferição, ritmo necessário, atalhos para unidades com regra, link no quadro "No ritmo" do painel. RN-18.
- Validado no ambiente local com a penalidade real do contrato 001/2025 (cláusula 12.1): sem novas ligações,
  R$ 409.383,00 até dez/2027; no ritmo para a meta, R$ 0,00.
- Correções vindas dos testes: o link "Simular" deixava o diálogo do item aberto; link no texto sem sublinhado (axe).
- Testes: back-end `SimuladorTest` (7), `RegraDePenalidadeTest` (9), `SimuladorApiTest` (2); front +2 specs
  unitários, E2E `simulador.spec.ts` (2) e a tela no axe.


### Campanhas de ligação e ordens de serviço (épico I, I2–I4) — ADR 0036 — branch `feat/campanhas-e-ordens-de-servico`
- Simulador (ADR 0035) mergeado na `main` em 02/11 com aprovação do autor.
- **Módulo `campanhas`** (V28: `equipe`, `equipe_membro`, `campanha`, `ordem_servico`, RLS por escopo). Permissões
  `campanha:gerenciar` e `os:executar`; perfil do sistema **Equipe de campo**; gestor comercial e administrador
  recebem as novas permissões (também nos clientes existentes, pela V28). RN-19 no doc 03.
- **Campanha** a partir de Sem ligação, setor ou área do mapa, com prévia e impacto na meta; OS priorizadas pelo
  semáforo; envio às equipes só dentro da área delas; resultado pela equipe ("Minhas OS", feita para celular) ou pelo
  gestor; encerrar/cancelar cancela as abertas.
- **Conferência pela carga** depois de cada carga (antes da reavaliação dos alertas) e ao registrar resultado; alerta
  `ORDEM_DIVERGENTE` por campanha. Painel com funil, efeito na adesão, produtividade por equipe e motivos.
- Telas: `/campanhas`, `/campanhas/:id`, `/equipes`, `/minhas-ordens` (seção "Campo" do menu); atalhos em Sem ligação
  e no Mapa; rótulos dos novos eventos na auditoria (e os da regra de penalidade, que faltavam).
- Testes: back-end `CampanhasApiTest` (fluxo completo com carga real, conferência e alerta), `CampanhaPorAreaApiTest`,
  `RegrasDasOrdensTest`, `RegrasDeCampanhaTest`; `PlataformaApiTest` passa a esperar 5 perfis do sistema. Front:
  `textos.spec.ts`, menu da equipe de campo, E2E `campanhas.spec.ts` e as três telas no axe.
- Fora (ADR 0036): app de campo offline, foto/assinatura, integração das OS com o sistema comercial, roteirização,
  polígono livre no mapa, reabrir resultado registrado.
