# 5. Arquitetura, stack e IA

## 5.1 Stack

| Camada | Escolha | Origem |
|--------|---------|--------|
| Backend | Java 21 + Spring Boot 3 (Spring Data, Spring Security) | [Proposta], alinhado ao perfil do autor |
| Frontend | Angular (versão atual estável) | [Proposta] |
| Banco | PostgreSQL + **PostGIS** | [Inferido], volume `aurora_postgres-dados` |
| Mapa | **MapLibre GL JS** com tiles OpenStreetMap, vista 3D com inclinação | [Inferido] |
| IA | API da Claude para extração de contrato e assistente | [Proposta] |
| Serviço de IA | Spring Boot (cliente HTTP) ou Python/FastAPI, **a decidir** | [Pendente] |
| Mensageria | Apache Kafka para cargas e eventos (opcional no MVP) | [Proposta] |
| Contêineres | Docker Compose | [Visto] (volumes Docker) |
| PDF/CSV | Geração no backend (ex.: OpenPDF/Apache PDFBox, Commons CSV) | [Proposta] |
| Extração de texto do PDF | Apache PDFBox; OCR (ex.: Tesseract) para PDFs escaneados | [Proposta] |

## 5.2 Visão de componentes

```
Angular SPA ──HTTPS──> API Spring Boot ──> PostgreSQL/PostGIS
                         │   │
                         │   ├──> Serviço de IA ──> API da Claude
                         │   └──> Armazenamento de PDFs (disco/objeto)
                         │
                Jobs agendados (alertas 15 min, projeções, cargas)

Rede da concessionária:  Sistema comercial ──> Agente Aurora (CNEFE) ──> API da Aurora (matrícula + ponto)
```

Estilo: monólito modular (hexagonal) no início; módulos `contratos`, `adesao`, `metas`, `imoveis`,
`mapa`, `alertas`, `assistente`, `admin`. Separar em serviços só se houver necessidade comprovada.

## 5.3 Fluxo de extração do contrato

1. **Upload** do PDF (valida tipo, tamanho, calcula hash, guarda o arquivo).
2. **Extração de texto por página** (PDFBox; OCR se a página não tiver texto). Guardar página de cada trecho.
3. **Segmentação por cláusula** (numeração "7.2", "Cláusula Sétima" etc.), mantendo número e página.
4. **Chamada à IA** com o texto segmentado e um **esquema JSON** de saída obrigatório.
5. **Validação do resultado** no backend: esquema, faixas (0–100%), datas, unidade existente, trecho presente no texto original.
6. **Gravação** em `extracao_item` com status Pendente e a extração em `PENDENTE`.
7. **Revisão** na tela de Contratos: item ao lado do trecho do PDF; aprovar, editar ou rejeitar.
8. **Aprovação** cria `meta` (origem Contrato) + `clausula` e registra auditoria.

### Esquema de saída esperado (rascunho)

```json
{
  "itens": [
    {
      "tipo": "META_ADESAO",
      "unidade_alvo": "Concessão A",
      "meta_pct": 80.0,
      "alerta_pct": 75.0,
      "vigencia_inicio": "2025-01-01",
      "vigencia_fim": "2026-12-31",
      "clausula": "7.2",
      "pagina": 2,
      "trecho": "…texto literal da cláusula…",
      "confianca": 0.93
    }
  ],
  "observacoes": []
}
```

Tipos de item [Relatado: "regras, metas e etc"; lista exata a confirmar]: `META_ADESAO`, `PRAZO`,
`REGRA`, `PENALIDADE`, `OBRIGACAO`, `INDICADOR`.

### Salvaguardas contra erro da IA

- **Trecho literal obrigatório** e verificado no texto do PDF (evita invenção de cláusula).
- Itens com baixa confiança ou sem referência ficam destacados.
- Nada vira meta sem aprovação humana.
- Guardar `modelo_ia`, `versao_prompt` e o valor original para auditoria e melhoria.
- Não enviar dados pessoais à IA: o contrato não os contém e as bases comerciais não saem da rede.

## 5.4 Assistente com fontes

Abordagem [Inferida pelo vídeo]: o assistente **não calcula de memória**; ele consulta ferramentas
internas e cita o resultado.

1. A pergunta chega com o escopo e as permissões do usuário.
2. O modelo escolhe **ferramentas** (ex.: `listar_unidades`, `adesao_por_unidade(competencia)`, `metas_vigentes`, `imoveis_sem_ligacao`).
3. As ferramentas executam consultas **parametrizadas** no banco (nunca SQL livre gerado pelo modelo).
4. A resposta sai com chips numerados (`1 Cadastro de unidades · 4 unidade(s)`, `2 Adesão à rede de esgoto · Cidade A Centro…`) e **linhas de dados** expansíveis.
5. Se nenhuma ferramenta retornar dado, o assistente informa que não encontrou.
6. Guardar pergunta, resposta, fontes e feedback.

## 5.5 Mapa e GIS

- **Tiles vetoriais** de município, setor, rede e imóveis servidos pelo backend (PostGIS `ST_AsMVT`) ou GeoJSON por viewport.
- Níveis de zoom: município (polígono colorido por situação) → setor → rede e lotes (ícones por situação).
- **Clusterização** de imóveis em zoom baixo; **18.167 imóveis** localizados no demo.
- Exportação da área: filtra por viewport + camadas ativas; PDF com mapa e lista; registra auditoria.
- **Agente Aurora:** cruza (matrícula, endereço) com CNEFE/IBGE na rede da concessionária e envia (matrícula, ponto, precisão).

## 5.6 Segurança

- Autenticação com Spring Security; senhas com hash forte; tokens de curta duração.
- **Multi-tenant** com `tenant_id` em todas as tabelas e **Row-Level Security** no PostgreSQL (ver [8](08-multitenancy-e-acesso.md)).
- **RBAC por perfil + escopo por unidade** aplicado no backend em toda consulta (inclusive no assistente).
- Tenant e escopo vêm do **token/contexto de segurança**, nunca do corpo da requisição ou do texto da pergunta à IA.
- Auditoria de exportações, aprovações e alterações de metas.
- PDFs de contrato com acesso restrito; links assinados temporários.
- Segredos fora do repositório (variáveis de ambiente).
- Rate limit e validação de upload (tipo, tamanho, antivírus se aplicável).

## 5.7 Cargas e jobs

- **Carga mensal** do sistema comercial (matrícula, situação, setor) → `imovel_situacao_mensal` → recálculo de `adesao_mensal`.
- **Job de alertas** a cada 15 minutos (RN-07) e **job de atraso de fonte** (RN-08).
- Cada carga registra início, fim, linhas e status; falha gera alerta.

## 5.8 Qualidade e entrega

- Git com remoto desde o primeiro commit; CI com testes e análise estática.
- Testes de unidade das regras (RN-01 a RN-05 usando os exemplos numéricos do demo como casos de teste).
- Testes de integração com Testcontainers (PostgreSQL/PostGIS).
- Dados fictícios gerados por script reproduzível (seed), com aviso na interface.
- Backup periódico do banco e do armazenamento de PDFs.

## 5.9 Design da interface [Visto]

Tema escuro por padrão e opção clara; paleta verde-água/azul sobre fundo quase preto; números grandes em
fonte monoespaçada para indicadores; cartões com borda sutil; legendas curtas nas ações. Atalhos: **Ctrl+K**
busca, **Ctrl+J** assistente. Prioridade: leitura rápida do indicador, depois o detalhe.
