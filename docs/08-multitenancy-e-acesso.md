# 8. Multi-tenant, perfis e controle de acesso

> **Status:** o autor confirmou que a Aurora era **multi-tenant** e tinha uma área de **configuração de perfis e acesso**.
> Os detalhes de implementação não foram vistos nos vídeos; o que está aqui combina o que apareceu na interface
> **[Visto]** com uma **[Proposta]** para a reconstrução. Validar com o `aurora-schema.sql` do banco antigo.

## 8.1 O que aparece nos vídeos [Visto]

- Seletor **"Seu escopo: Grupo Saneamento Exemplo"** (avatar "GS") no topo da barra lateral: é o contexto do cliente.
- Menu **Administração** com **Unidades, Usuários, Perfis e Integrações**.
- Usuário logado "Administração Dev · admin@exemplo.dev".
- **Auditoria** de emissões (exportações) e dados filtrados pelo escopo.
- Dados "fictícios de demonstração" por escopo.

## 8.2 Conceitos

| Conceito | Definição |
|----------|-----------|
| **Tenant** | Cliente isolado da plataforma (ex.: um grupo de concessionárias). Dono dos seus dados, usuários, perfis e contratos. |
| **Unidade** | Nó da hierarquia dentro do tenant: Concessão/SPE → Município → Setor comercial. |
| **Usuário** | Pessoa com login; pode pertencer a um ou mais tenants (a confirmar). |
| **Perfil (papel)** | Conjunto de **permissões** (o que pode fazer). |
| **Permissão** | Ação atômica (`contratos.aprovar`, `exportar.pdf`...). |
| **Escopo do usuário** | Conjunto de unidades que o usuário enxerga (inclui descendentes). |
| **Administrador da plataforma** | Papel interno que cria tenants; **não** vê dados dos tenants [Proposta]. |

**Regra de ouro:** acesso permitido = `tenant igual` **E** `perfil tem a permissão` **E** `unidade do dado está no escopo`.

## 8.3 Isolamento entre tenants [Proposta]

| Estratégia | Prós | Contras | Decisão |
|------------|------|---------|---------|
| **Banco compartilhado + `tenant_id` + Row-Level Security (RLS)** | Simples de operar, barato, escala bem, RLS impede vazamento no próprio banco | Exige disciplina em todas as tabelas | **Escolhida** |
| Schema por tenant | Isolamento forte | Migrações em N schemas, complexidade | Alternativa |
| Banco por tenant | Isolamento máximo | Custo e operação altos | Só se exigido por cliente |

Implementação:
1. Toda tabela de negócio tem `tenant_id NOT NULL` (FK para `tenant`).
2. A API define `SET LOCAL app.tenant_id = '<uuid>'` no início de cada transação (a partir do token, **nunca** do corpo da requisição).
3. Política RLS em cada tabela: `USING (tenant_id = current_setting('app.tenant_id')::uuid)`; usuário de aplicação **sem** `BYPASSRLS`.
4. Índices começam por `tenant_id` nas tabelas grandes (`imovel_situacao_mensal`, `adesao_mensal`).
5. **Arquivos** (PDFs de contrato): caminho `tenant/<id>/contratos/...`, acesso por link assinado e verificação do tenant.
6. **Cache, filas e jobs**: chaves e tópicos com `tenant_id`; jobs de alerta e cargas executam por tenant.
7. **Logs e métricas** com `tenant_id` como etiqueta, sem dados pessoais.
8. **IA/assistente**: as ferramentas recebem tenant e escopo do **contexto de segurança**, nunca do texto da pergunta (ver 8.9).
9. **Testes de isolamento** automatizados: criar 2 tenants e provar que nenhuma rota, exportação, busca ou ferramenta de IA devolve dados do outro.

Exemplo de política:

```sql
ALTER TABLE meta ENABLE ROW LEVEL SECURITY;
ALTER TABLE meta FORCE ROW LEVEL SECURITY;
CREATE POLICY meta_tenant ON meta
  USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

## 8.4 Perfis e permissões [Proposta]

### Catálogo de permissões

| Área | Permissões |
|------|-----------|
| Painel e adesão | `painel.ver`, `metas.ver`, `metas.editar` |
| Contratos | `contratos.ver`, `contratos.enviar`, `contratos.aprovar` |
| Imóveis | `imoveis.ver` (lista sem ligação) |
| Mapa | `mapa.ver`, `mapa.marcar` |
| Exportação | `exportar.csv`, `exportar.pdf` |
| Alertas | `alertas.ver`, `alertas.reconhecer` |
| Assistente | `assistente.usar` |
| Administração | `unidades.gerir`, `usuarios.gerir`, `perfis.gerir`, `integracoes.gerir` |
| Auditoria | `auditoria.ver` |

### Perfis iniciais (do sistema, não editáveis; o tenant pode criar perfis próprios)

| Permissão | Administrador | Gestor | Analista de contratos | Analista comercial | Campo | Leitura |
|-----------|:---:|:---:|:---:|:---:|:---:|:---:|
| painel.ver, metas.ver | ✔ | ✔ | ✔ | ✔ | — | ✔ |
| metas.editar | ✔ | ✔ | — | — | — | — |
| contratos.ver | ✔ | ✔ | ✔ | — | — | ✔ |
| contratos.enviar | ✔ | — | ✔ | — | — | — |
| contratos.aprovar | ✔ | ✔ | ✔ | — | — | — |
| imoveis.ver | ✔ | ✔ | — | ✔ | ✔ | ✔ |
| mapa.ver | ✔ | ✔ | — | ✔ | ✔ | ✔ |
| mapa.marcar | ✔ | ✔ | — | ✔ | ✔ | — |
| exportar.csv / exportar.pdf | ✔ | ✔ | — | ✔ | PDF | — |
| alertas.ver / reconhecer | ✔ | ✔ | — | ✔ | — | ver |
| assistente.usar | ✔ | ✔ | ✔ | ✔ | — | ✔ |
| unidades / usuarios / perfis / integracoes | ✔ | — | — | — | — | — |
| auditoria.ver | ✔ | ✔ | — | — | — | — |

### Escopo

- O usuário recebe **uma ou mais unidades** (ex.: só "Cidade A"); acesso inclui as descendentes (municípios e setores).
- Quem tem a concessão vê tudo abaixo; quem tem um setor só vê o setor.
- Indicadores agregados respeitam o escopo: o usuário vê apenas o que está nas suas unidades (os totais de um nível alto **não** vazam dados de unidades fora do escopo).
- A **adesão de uma unidade** só é exibida se o usuário tem acesso à unidade (regra RN-13).

## 8.5 Telas de administração

| Tela | Conteúdo |
|------|----------|
| **Unidades** | Árvore Concessão → Município → Setor; criar, editar, mover, inativar; importar geometria; ver metas e fontes ligadas |
| **Usuários** | Lista com perfil, escopo, status e último acesso; convidar por e-mail; ativar/desativar; redefinir senha; atribuir perfis e unidades |
| **Perfis** | Lista de perfis (sistema e do tenant); matriz de permissões; criar perfil personalizado; duplicar; ver quem usa; impedir remover o último administrador |
| **Integrações** | Fontes de dados (sistema comercial, rede, CNEFE), **atraso máximo**, última carga, status, chave/credencial do **Agente Aurora**, histórico de cargas |
| **Tenant** (administrador da plataforma) | Criar tenant, nome, logo, limites, status, primeiro administrador |
| **Auditoria** | Consulta de eventos com filtros por usuário, ação, período e unidade; exportação |

## 8.6 Autenticação e sessão [Proposta]

- Login por e-mail e senha com hash forte (Argon2id/bcrypt), **bloqueio progressivo** após tentativas falhas e recuperação de senha por e-mail.
- **MFA** (TOTP) opcional por tenant e obrigatório para administradores.
- **SSO** por OIDC/SAML (ex.: Entra ID) por tenant, em fase posterior.
- **Convite por e-mail** com link de uso único e expiração.
- Token de acesso de curta duração com `user_id`, `tenant_id` e perfis; **escopo resolvido no servidor** a cada requisição (não confiar no token para unidades).
- Troca de tenant (se o usuário pertence a vários) gera novo token; evento auditado.
- Sessões revogáveis; logout em todos os dispositivos.

## 8.7 Auditoria

Eventos obrigatórios (com `tenant_id`, usuário, data/hora, IP, detalhes):

- login, falha de login, bloqueio, troca de tenant;
- criação/edição de usuário, perfil, permissão, escopo e convite;
- **exportações** (CSV/PDF do painel, sem ligação e mapa), com filtros e quantidade de linhas;
- upload, **aprovação e rejeição** de itens de contrato;
- criação/edição/encerramento de metas;
- alteração de integrações e cargas.

A auditoria é **somente leitura**, retida por prazo configurável e protegida contra edição.

## 8.8 Histórias de usuário

| ID | História | Prioridade |
|----|----------|-----------|
| S1 | Como **administrador da plataforma**, quero criar um tenant com seu primeiro administrador, para onboarding de um novo cliente. | P0 |
| S2 | Como **administrador do tenant**, quero convidar usuários por e-mail e definir perfil e escopo. | P0 |
| S3 | Como administrador, quero ver e criar **perfis** com uma matriz de permissões. | P0 |
| S4 | Como administrador, quero limitar um usuário a **unidades específicas**. | P0 |
| S5 | Como usuário, quero ver **apenas os dados do meu tenant e do meu escopo**, em telas, exportações, mapa e assistente. | P0 |
| S6 | Como usuário com vários tenants, quero **trocar de escopo** pelo seletor do topo. | P1 |
| S7 | Como administrador, quero **desativar** um usuário sem apagar o histórico dele. | P0 |
| S8 | Como administrador, quero **consultar a auditoria** (quem exportou, aprovou, alterou). | P0 |
| S9 | Como administrador, quero configurar **MFA** obrigatório para o tenant. | P1 |
| S10 | Como administrador, quero **integrar SSO** corporativo. | P2 |
| S11 | Como administrador, quero cadastrar e acompanhar **integrações** e cargas. | P0 |

Critérios de aceite transversais:
- Tentar acessar um dado de outro tenant (por ID, URL ou exportação) retorna **404**, não 403 (não revela existência).
- Mudança de perfil vale na próxima requisição.
- Não é possível remover o último administrador do tenant.
- Perfil do sistema não pode ser editado nem apagado.

## 8.9 Assistente e IA em ambiente multi-tenant

- A identidade do usuário (tenant, perfil, escopo) vem do **contexto de segurança da requisição**, e é injetada nas ferramentas de consulta.
- O modelo **não escolhe** tenant nem unidade fora do escopo; se a pergunta citar unidade proibida, a resposta informa que não há dado disponível.
- As fontes e as linhas de dados expansíveis também passam pelo filtro de escopo.
- Extração de contrato: arquivo e resultado ficam no tenant; o modelo de IA não recebe dados de outros tenants e **não é treinado** com os contratos do cliente (configurar o provedor para isso).
- Conversas e feedback do assistente pertencem ao usuário e ao tenant.

## 8.10 Modelo de dados (acréscimos)

| Tabela | Campos principais | Observações |
|--------|-------------------|-------------|
| `tenant` | id, nome, slug, logo, status, mfa_obrigatorio, criado_em | Substitui/engloba o `grupo` do rascunho anterior |
| `usuario` | id, nome, email (único global), senha_hash, mfa_secret, ativo | Pode pertencer a vários tenants |
| `usuario_tenant` | usuario_id, tenant_id, ativo, ultimo_acesso | Vínculo usuário ↔ tenant |
| `permissao` | codigo (PK), descricao, area | Catálogo global |
| `perfil` | id, tenant_id (nulo = perfil do sistema), nome, sistema (bool) | |
| `perfil_permissao` | perfil_id, permissao_codigo | |
| `usuario_perfil` | usuario_id, tenant_id, perfil_id | |
| `usuario_escopo` | usuario_id, tenant_id, unidade_id | Inclui descendentes na consulta |
| `convite` | id, tenant_id, email, perfil_id, token_hash, expira_em, usado_em | |
| `sessao` | id, usuario_id, tenant_id, criada_em, expira_em, revogada | |
| `auditoria` | id, tenant_id, usuario_id, acao, entidade, entidade_id, parametros (jsonb), ip, em | Somente inserção |

Todas as tabelas de negócio dos documentos anteriores ganham `tenant_id NOT NULL` e política RLS.
`unidade_escopo` pode usar **caminho materializado** ou `ltree` para resolver descendentes rapidamente.

## 8.11 Regras (resumo)

Detalhadas em [3. Regras de negócio](03-regras-de-negocio.md): RN-13 (escopo e permissões), RN-15 (isolamento por tenant),
RN-16 (administração e perfis), RN-17 (autenticação).

## 8.12 Pendências

| # | Pergunta | Como resolver |
|---|----------|---------------|
| 1 | O tenant era o "Grupo" (ex.: Grupo Saneamento Exemplo) ou a concessionária? Um usuário podia estar em vários? | Autor / schema |
| 2 | Como era o isolamento: coluna `tenant_id`, schema por tenant ou banco por tenant? | Schema do banco antigo |
| 3 | Quais perfis existiam e quais permissões cada um tinha? | Tabelas de perfil/permissão do banco antigo |
| 4 | O acesso era limitado por unidade (escopo) além do perfil? | Autor / schema |
| 5 | Login: e-mail/senha, SSO, MFA? | Autor |
| 6 | Existia convite por e-mail, redefinição de senha, bloqueio por tentativas? | Autor |
| 7 | Havia **administrador da plataforma** para criar tenants? | Autor |
| 8 | Perfis customizáveis pelo tenant ou só fixos? | Autor / schema |
