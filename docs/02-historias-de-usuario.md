# 2. Histórias de usuário

Formato: **Como** <persona>, **quero** <ação>, **para** <benefício>. Critérios de aceite em Dado/Quando/Então.
Prioridade: **P0** núcleo, **P1** importante, **P2** desejável.

---

## Épico A — Contratos e extração por IA (núcleo)

### A1 · Enviar contrato (P0) [Relatado]
Como analista de contratos, quero enviar o PDF do contrato de concessão/PPP, para que a IA leia as cláusulas.
- Dado um PDF válido, quando eu envio, então ele fica salvo e a extração entra em status **Processando**.
- Dado um arquivo que não é PDF ou está corrompido, então o sistema recusa com mensagem clara.
- Dado um PDF escaneado sem texto, então o sistema avisa que precisa de OCR ou tenta OCR (a confirmar).

### A2 · IA extrai cláusulas, regras e metas (P0) [Relatado]
Como analista de contratos, quero que a IA identifique metas, regras e demais pontos relevantes, para não digitar tudo à mão.
- Cada item extraído traz: tipo, unidade alvo, valores (meta, limite de alerta), vigência, **número da cláusula, página e trecho original**.
- Itens sem cláusula ou página identificáveis são marcados **"sem referência"** e não podem ser aprovados sem edição.

### A3 · Revisar e aprovar (P0) [Relatado]
Como analista de contratos, quero ver o que a IA extraiu ao lado do trecho do PDF e aprovar, editar ou rejeitar cada item, para que só dados conferidos entrem no sistema.
- Dado itens pendentes, quando eu aprovo, então viram metas com origem **Contrato** e referência (contrato, cláusula, página).
- Dado um item editado por mim, então o sistema guarda o valor original da IA e o valor final.
- Nada é gravado como meta oficial antes da aprovação.
- A aprovação registra usuário e data na auditoria.

### A4 · Abrir o contrato na página citada (P1) [Proposta]
Como gestor, quero clicar em "cláusula 7.2, p. 2" e abrir o PDF naquela página, para conferir a fonte.

### A5 · Substituir ou aditar contrato (P2) [Proposta]
Como analista, quero enviar uma nova versão do contrato (aditivo), para atualizar as metas sem perder o histórico.

---

## Épico B — Painel de adesão

### B1 · Ver adesão da concessão (P0) [Visto]
Como gestor, quero ver o percentual de adesão da concessão no mês, com meta, limite de alerta, cláusula de origem e variação em pontos percentuais.
- Exibe "N ligados de M imóveis com rede disponível".
- Exibe selo de situação: Meta atingida, Atenção ou Abaixo do alerta.

### B2 · Ver projeção "no ritmo" (P0) [Visto]
Como gestor, quero saber se, no ritmo atual, a meta sai no prazo, para agir antes.
- Mostra adesão projetada na data da meta, ligações que faltam, ritmo necessário por mês e ritmo atual.
- Explica o método (média dos últimos 6 meses, sem meses em que a rede mudou).

### B3 · Gráfico da série histórica (P0) [Visto]
Linhas de adesão, projeção, meta e limite de alerta; opção "Ver como tabela".

### B4 · Detalhar por unidade (P0) [Visto]
Tabela por município e setor: adesão (barra), situação, meta, variação e ligados/factíveis; navegação com "Voltar para o nível acima".

### B5 · Escolher competência (P0) [Visto]
Seleção do mês de referência.

### B6 · Exportar painel (P1) [Visto]
Botões CSV e PDF.

### B7 · Ir direto aos imóveis sem ligação (P0) [Visto]
Link "Ver os 2.639 imóveis sem ligação" leva à lista já filtrada.

---

## Épico C — Metas

### C1 · Listar metas em hierarquia (P0) [Visto]
Concessão > município > setor, com meta, alerta, vigência, origem e situação.
Filtros: busca por unidade, situação, vigência, origem. Resumo: "N metas cadastradas · N do contrato · N unidades seguem a unidade acima".

### C2 · Herança de meta (P0) [Visto]
Unidade sem meta própria "segue" a unidade acima e mostra qual (ex.: "Segue Cidade A · 85,0%").

### C3 · Meta própria manual (P0) [Visto]
Como gestor, quero criar uma meta própria para uma unidade ("Nova meta" / "Meta própria"), com percentual, limite de alerta e vigência (fim opcional → "sem fim").

### C4 · Encerrar meta (P1) [Visto]
Botão de remover/encerrar a meta, sem apagar o histórico (a confirmar).

---

## Épico D — Imóveis sem ligação

### D1 · Listar imóveis com rede e sem ligação (P0) [Visto]
Colunas: matrícula, unidade, situação (Factível/Cortado), "sem ligação há", origem ("Ligado até jul/2026").
Filtro por unidade; abas Todos / Factíveis / Cortados com contagem; busca por matrícula; ordenação; paginação ("Mostrando 50 de 2.431 · Carregar mais").

### D2 · Exportar lista (P0) [Visto]
Exportar CSV, com a emissão registrada na auditoria.

### D3 · Localizar no sistema comercial (P0) [Visto]
A tela orienta: "localize cada um no sistema comercial pela matrícula". A Aurora não mostra nome do cliente nem endereço.

---

## Épico E — Mapa (GIS)

### E1 · Mapa em níveis (P0) [Visto]
Do polígono do município até cada lote, no mesmo mapa, com fundo OpenStreetMap.

### E2 · Camadas (P0) [Visto]
Municípios (por situação da adesão), setores comerciais, rede de esgoto (confirmada/presumida), imóveis (ligado, factível sem ligação, cortado, sem rede, localização aproximada), marcações e unidades operacionais (ETE, EEE). Cada camada liga e desliga.

### E3 · Vista 3D (P1) [Visto]
Alternar entre vista de cima e 3D, com giro e inclinação.

### E4 · Marcar visita (P0) [Visto]
Botão **Marcar local**, clique no mapa, Esc cancela. A marcação guarda ponto, texto, autor e data.

### E5 · Exportar área (P0) [Visto]
Exporta o que está visível (imóveis e marcações) em CSV ("para abrir no Excel ou levar a outro sistema") ou PDF ("para imprimir ou anexar a uma ordem de serviço"). A emissão fica na auditoria.

### E6 · Localizar imóveis sem expor endereço (P0) [Visto]
Agente cruza matrícula e endereço do sistema comercial com o CNEFE/IBGE **dentro da rede da concessionária** e entrega à Aurora só matrícula e ponto; imóveis sem correspondência exata ficam como "localização aproximada".

---

## Épico F — Alertas

### F1 · Alertas de adesão (P0) [Visto]
Gera alerta quando a unidade fica abaixo do limite (**Crítico**) ou abaixo da meta (**Atenção**).

### F2 · Alertas de fonte atrasada (P0) [Visto]
Gera alerta quando a última carga excede o atraso máximo ("Fonte de dados atrasada: Sistema comercial").

### F3 · Ciclo de vida (P0) [Visto]
Verificação a cada 15 minutos; o alerta **se resolve sozinho** quando a condição passa; o usuário pode **Reconhecer**; **Ver** leva à tela relacionada; contador no menu e no sino.

---

## Épico G — Assistente com fontes

### G1 · Perguntar em português (P0) [Visto]
Ex.: "Quais setores estão abaixo do limite de alerta?". Resposta em texto com os valores citados.

### G2 · Mostrar fontes (P0) [Visto]
Chips numerados com a origem de cada número e linhas de dados expansíveis (meta, tipo, adesão, ligados, com rede, situação, variação).

### G3 · Histórico de conversas (P1) [Visto]
Nova conversa, lista de anteriores, busca nas conversas.

### G4 · Feedback (P1) [Visto]
Copiar; "Ajudou? Sim / Tem erro".

### G5 · Perguntar a partir do painel (P1) [Visto]
Link "Perguntar ao assistente sobre Concessão A" abre a conversa com o contexto.

### G6 · Nunca inventar número (P0) [Relatado/Proposta]
A resposta só usa números vindos das bases e cita a fonte; sem fonte, o assistente diz que não sabe.

---

## Épico H — Administração e segurança

### H1 · Cadastro de unidades (P0) [Visto]
Concessão, município e setor, com hierarquia e geometria.

### H2 · Usuários e perfis (P0) [Visto]
Permissões por perfil e escopo por grupo/unidade.

### H3 · Integrações e cargas (P0) [Visto]
Cadastro das fontes (sistema comercial, rede, CNEFE), data da última carga, atraso máximo.

### H4 · Auditoria (P0) [Visto]
Registro de emissões (exportações), aprovações de contrato e alterações de metas.

### H5 · Isolamento entre tenants (P0) [Relatado]
Como cliente, quero que meus dados, contratos e usuários fiquem isolados dos demais clientes.
- Dado dois tenants, quando um usuário do tenant A consulta qualquer tela, exportação, mapa ou assistente, então nunca recebe dado do tenant B.
- Acesso por ID ou URL de outro tenant retorna 404.

### H6 · Perfis e permissões (P0) [Relatado]
Como administrador, quero criar e editar perfis com uma matriz de permissões e atribuí-los aos usuários.

### H7 · Escopo por unidade (P0) [Proposta]
Como administrador, quero limitar um usuário a unidades específicas (e suas descendentes).

### H8 · Convite e ciclo de vida do usuário (P0) [Proposta]
Convidar por e-mail, ativar/desativar, redefinir senha, último acesso; não remover o último administrador.

### H9 · Autenticação segura (P1) [Proposta]
Bloqueio por tentativas, MFA opcional/obrigatório e, depois, SSO.

Histórias completas e critérios em [8. Multi-tenant, perfis e acesso](08-multitenancy-e-acesso.md#88-histórias-de-usuário).

---

## Marcos sugeridos

1. **MVP:** A1–A3, C1–C3, B1–B5, D1–D2, H1–H2.
2. **Operação:** F1–F3, G1–G2, H3–H4.
3. **GIS:** E1–E6.
4. **Refino:** A4–A5, G3–G5, B6, E3.
