# 1. Visão e escopo

## 1.1 Objetivo

Dar à concessionária e ao poder concedente uma visão **mensal, rastreável e antecipada** da adesão à rede
de esgoto, ligando dados comerciais, cláusulas contratuais e IA, de modo que cada número possa ser
conferido na fonte.

## 1.2 Personas

| Persona | Necessidade | Fonte |
|---------|-------------|-------|
| **Gestor da concessão** | Saber se a meta contratual será cumprida e quando agir | [Visto] |
| **Analista comercial** | Encontrar cada imóvel com rede e sem ligação, pela matrícula, no sistema comercial | [Visto] |
| **Analista de contratos** | Subir o contrato, conferir o que a IA extraiu e aprovar | [Relatado] |
| **Equipe de campo** | Receber a área do mapa em PDF/planilha e marcar visitas | [Visto] |
| **Administrador do tenant** | Cadastrar unidades, usuários, perfis, escopos e integrações; consultar auditoria | [Visto] |
| **Administrador da plataforma** | Criar tenants e primeiro administrador; não acessa dados dos clientes | [Proposta] |

## 1.3 Multi-tenant e hierarquia de unidades [Visto/Relatado]

A Aurora é **multi-tenant**: cada cliente (tenant) tem seus dados, usuários, perfis, contratos e metas isolados.

```
Tenant (cliente / grupo)  >  Concessão / SPE  >  Município  >  Setor comercial
```

O usuário enxerga apenas o **escopo** a que tem acesso (ex.: "Grupo Saneamento Exemplo"), de acordo com seu
**perfil** e suas **unidades**. Detalhes em [8. Multi-tenant, perfis e acesso](08-multitenancy-e-acesso.md).

## 1.4 Módulos

| Módulo | Menu | Resumo | Fonte |
|--------|------|--------|-------|
| Painel | Painel | Adesão por unidade, projeção "no ritmo", gráfico, exportação | [Visto] |
| Assistente | Assistente (Ctrl+J) | Perguntas em português, respostas com fontes | [Visto] |
| Metas | Metas | Metas por unidade e vigência, herança, origem contrato/manual | [Visto] |
| Sem ligação | Sem ligação | Imóveis com rede disponível e sem ligação | [Visto] |
| Mapa (GIS) | Mapa | Municípios, setores, rede, imóveis, marcações, 3D, exportação | [Visto] |
| Alertas | Alertas | Alertas de adesão e de fonte de dados atrasada | [Visto] |
| Contratos | Contratos | Upload do PDF, extração pela IA, revisão e aprovação | [Relatado] |
| Administração | Unidades, Usuários, Perfis, Integrações | Cadastros, permissões por perfil, cargas de dados | [Visto] |

Recursos transversais [Visto]: busca global (Ctrl+K), seletor de competência (mês), tema claro/escuro,
indicador "Dados de <mês> · N fontes atrasadas", notificações, aviso "dados fictícios de demonstração".

## 1.5 Fora de escopo (versão de estudo)

- Faturamento, cobrança e arrecadação (a Aurora lê dados do sistema comercial, não o substitui).
- Envio de dados reais de clientes para fora da rede da concessionária.
- Aplicativo mobile.
- Execução de ordens de serviço (o PDF exportado é anexado a uma OS feita em outro sistema).

## 1.6 Princípios

1. **Rastreabilidade:** todo número aponta para sua origem (cláusula, base, competência).
2. **Humano no controle:** a IA propõe, a pessoa aprova.
3. **Privacidade:** endereço fica na concessionária; a Aurora guarda matrícula e ponto.
4. **Transparência de dados:** o sistema avisa quando uma fonte está atrasada.
5. **Dados abertos:** CNEFE/IBGE e OpenStreetMap.
