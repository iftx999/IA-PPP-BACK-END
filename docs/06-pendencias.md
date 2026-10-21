# 6. Pendências, riscos e plano de reconstrução

## 6.1 Pendências a confirmar

| # | Pergunta | Como resolver |
|---|----------|---------------|
| 1 | O que mais a IA extraía do contrato além de metas ("regras, metas e etc")? | Autor e/ou esquema do banco |
| 2 | Qual modelo/API de IA e qual linguagem do serviço de IA (Java ou Python/FastAPI)? | Autor |
| 3 | Como era a tela de Contratos (lista, upload, revisão ao lado do PDF)? | Autor ou schema |
| 4 | Esquema real do banco (tabelas, colunas, PostGIS, extração/aprovação) | `pg_dump --schema-only` do volume `aurora_postgres-dados` |
| 5 | Regra exata de "factível" vs. "cortado" | Autor (regra do sistema comercial) |
| 6 | Vigências sobrepostas e aditivos contratuais | Decisão de produto |
| 7 | Origem dos trechos de rede (OSM derivado? importados?) | Autor / schema |
| 8 | Perfis e permissões existentes | Schema (`perfil`, `permissao`) |
| 9 | Fonte e frequência das cargas ("Integrações") | Autor / schema |
| 10 | PDFs escaneados: OCR ou apenas PDFs com texto? | Decisão |
| 11 | Multi-tenant: o tenant era o grupo ou a concessionária? Como era o isolamento (coluna, schema, banco)? | Autor / schema |
| 12 | Perfis, permissões e escopo por unidade existentes; perfis customizáveis? | Autor / schema |
| 13 | Autenticação: e-mail/senha, MFA, SSO, convite, administrador da plataforma | Autor |

## 6.2 Riscos

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| IA extrai cláusula errada ou inventa | Meta incorreta e decisão errada | Trecho literal verificado, aprovação humana, auditoria |
| PDF complexo (tabelas, escaneado, aditivos) | Extração incompleta | OCR, segmentação por cláusula, revisão obrigatória |
| Qualidade do geocódigo CNEFE | Imóveis fora do lugar | Marcar "localização aproximada", métrica de precisão |
| Privacidade de dados de clientes | Exposição de endereço | Agente dentro da rede; Aurora só guarda matrícula + ponto |
| Vazamento de dados entre tenants | Exposição de contratos e dados de clientes | RLS no banco, tenant só do token, testes de isolamento no CI, 404 em recurso alheio |
| Perda do código novamente | Retrabalho total | Git + remoto desde o primeiro commit; backup do banco |
| Escopo grande para projeto de estudo | Nunca terminar | Entregar por marcos (6.3) |

## 6.3 Plano de reconstrução

| Marco | Entrega | Histórias |
|-------|---------|-----------|
| **0. Fundação** | Repositório no GitHub, Docker Compose (Postgres/PostGIS), CI, seed de dados fictícios, **multi-tenant com RLS e testes de isolamento** | H5 |
| **1. Acesso e núcleo** | Tenants, usuários, perfis e escopo; unidades, metas manuais, adesão mensal, painel, sem ligação | H1–H2, H6–H8, B1–B5, C1–C3, D1–D2 |
| **2. Contratos + IA** | Upload, extração, revisão e aprovação, meta com cláusula | A1–A3 |
| **3. Operação** | Alertas, auditoria, integrações/cargas | F1–F3, H3–H4 |
| **4. Assistente** | Perguntas com fontes via ferramentas | G1–G6 |
| **5. GIS** | Mapa, camadas, marcações, exportação, 3D | E1–E6 |
| **6. Refino** | Abrir PDF na página, aditivos, histórico do assistente | A4–A5, G3–G5 |

## 6.4 Como recuperar informação do banco antigo

```bash
sudo systemctl start docker
sudo cat /var/lib/docker/volumes/aurora_postgres-dados/_data/PG_VERSION
sudo cp -a /var/lib/docker/volumes/aurora_postgres-dados/_data ~/aurora-pgdata-backup
docker run -d --name aurora-recupera -v ~/aurora-pgdata-backup:/var/lib/postgresql/data postgres:<VERSAO>
docker exec aurora-recupera psql -U postgres -l
docker exec aurora-recupera pg_dump -U postgres --schema-only --no-owner <BANCO> > ~/aurora-schema.sql
```

Depois de obter o `aurora-schema.sql`, atualizar o documento [4. Modelo de dados](04-modelo-de-dados.md)
e remover os itens correspondentes desta lista.

## 6.5 Decisões já tomadas

- Projeto de estudo, sem fim comercial, com dados fictícios.
- IA propõe, humano aprova; nenhuma meta de contrato entra sem aprovação.
- Endereço permanece na concessionária.
- Reconstrução do zero, usando estes documentos e o banco antigo como referência.
