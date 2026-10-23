# Registros de decisão de arquitetura (ADR)

Formato curto: **Contexto**, **Decisão**, **Consequências**. Um ADR aceito não é editado; uma mudança de rumo
gera um novo ADR que o substitui.

| # | Decisão | Status |
|---|---------|--------|
| [0001](0001-monolito-modular.md) | Monólito modular com Spring Modulith e camadas por módulo | Aceito |
| [0002](0002-multitenant-rls.md) | Multi-tenant em banco compartilhado com Row-Level Security | Aceito |
| [0003](0003-identidade-keycloak.md) | Identidade no Keycloak, um emissor por tenant | Aceito |
| [0004](0004-schema-herdado.md) | Schema reconstruído a partir do banco da versão anterior | Aceito |
| [0005](0005-java-21.md) | Java 21 LTS em vez de Java 25 | Aceito |
| [0006](0006-acesso-a-dados.md) | JdbcClient com SQL explícito para leituras analíticas | Aceito |
| [0007](0007-repositorios-separados.md) | Repositórios separados para back-end e front-end | Aceito |
| [0008](0008-qualidade-no-build.md) | Formatação, cobertura e documentação da API verificadas no build | Aceito |
| [0009](0009-trilha-de-auditoria.md) | Trilha de auditoria encadeada por HMAC | Aceito |
| [0010](0010-biblioteca-de-componentes.md) | Angular Material como base dos componentes de interface | Aceito |
| [0011](0011-gestao-de-usuarios-no-provedor.md) | Gestão de usuários pela Admin API do provedor de identidade | Aceito |
| [0012](0012-hierarquia-de-unidades.md) | Hierarquia de unidades e caminho ltree | Aceito |
| [0013](0013-permissao-por-unidade.md) | Permissão vale só na unidade onde foi concedida | Aceito |
| [0014](0014-extracao-de-contratos-com-ia.md) | Extração de itens contratuais com o Claude e revisão humana obrigatória | Aceito |
| [0015](0015-alertas-e-tarefas-agendadas.md) | Alertas por sincronização de condições e tarefas agendadas com trava no banco | Aceito |
| [0016](0016-ingestao-de-cargas.md) | Ingestão de cargas pelo Agente Aurora e por arquivo | Aceito |
| [0017](0017-mapa-e-tiles-vetoriais.md) | Mapa: tiles vetoriais do PostGIS, camadas leves e exportação do visível | Aceito |
| [0018](0018-assistente-com-fontes.md) | Assistente com fontes: ferramentas fixas, fontes numeradas e verificação dos números | Aceito |
| [0019](0019-provedor-de-ia-configuravel.md) | Provedor de IA configurável: Claude (padrão) ou OpenAI | Aceito |
| [0020](0020-aditivos-e-historico-de-metas.md) | Aditivo contratual substitui metas com histórico | Aceito |
| [0021](0021-ocr-de-contratos-digitalizados.md) | OCR local de contratos digitalizados com Tesseract | Aceito |
| [0022](0022-implantacao-e-observabilidade.md) | Implantação em contêineres, host único e observabilidade | Aceito |
