# 7. Identidade visual e design tokens

> Medido nos quadros dos vídeos de demonstração (compressão de vídeo: tolerância de alguns pontos em cada canal).
> As fontes são **aproximações** pelo formato das letras; confirmar se houver o arquivo original do design.

## 7.1 Princípios

1. **Escuro e calmo**, com um único destaque verde-água. Cor só para significar algo (estado, ação, fonte).
2. **O número é o herói.** Indicadores grandes, em monoespaçada; texto de apoio pequeno e discreto.
3. **Tudo é conferível.** Fontes, cláusulas e dados abertos sempre a um clique, com visual próprio (chips numerados).
4. **Futurista, sem exagero.** Brilho suave (glow) apenas em destaque e foco; nada de decoração que atrapalhe a leitura.
5. **Legendas curtas e verbos diretos** ("Marque a visita.", "Pergunte em português.").

## 7.2 Cores

### Base (tema escuro, padrão)

| Token | Valor | Uso |
|-------|-------|-----|
| `--bg-0` | `#051313` | Abertura/splash, fundo mais profundo |
| `--bg-1` | `#081018` | Fundo da aplicação |
| `--bg-2` | `#0c181c` | Barra lateral e cartões |
| `--bg-3` | `#12212a` | Cartão elevado, quadro "No ritmo", campos |
| `--line` | `#1c2e36` | Bordas finas de cartão e divisores (≈ 1 px) |
| `--text-1` | `#e8f0ee` | Texto principal e números |
| `--text-2` | `#b4c1c3` | Texto secundário |
| `--text-3` | `#6f8288` | Rótulos, legendas, texto desabilitado |

### Destaque (marca)

| Token | Valor | Uso |
|-------|-------|-----|
| `--accent` | `#4ad7bc` | Cor de marca: títulos de impacto, item ativo, foco, bordas de destaque |
| `--accent-2` | `#1acbd8` | Série "Adesão" no gráfico, rede confirmada, início do gradiente do logo |
| `--accent-3` | `#2c9aeb` | Fim do gradiente do logo, links secundários |
| `--accent-glow` | `rgba(74, 215, 188, .35)` | Brilho (box-shadow) de foco e destaque |

Gradiente do logo: `conic-gradient(from 210deg, #1acbd8, #2c9aeb, #4ad7bc, #1acbd8)` dentro de um quadrado de raio 14 px.

### Semântica (estados)

| Token | Valor | Significado | Onde aparece |
|-------|-------|-------------|--------------|
| `--ok` | `#3de693` | Ligado / meta atingida | Imóvel ligado, variação positiva, selo "Meta atingida" |
| `--warn` | `#ecc32a` | Atenção / factível sem ligação | Selo "Atenção", imóvel factível, limite de alerta no gráfico |
| `--danger` | `#e76069` | Abaixo do alerta / cortado / crítico | Selo "Abaixo do alerta", imóvel cortado, variação negativa |
| `--danger-soft` | `#ee7a62` | Negação enfática (riscado) | Texto "A rede ficou pronta." riscado nas aberturas |
| `--neutral` | `#86929e` | Sem rede / sem meta | Imóvel sem rede, município sem meta |
| `--net-confirmed` | `#43c3c5` | Rede confirmada | Linha contínua no mapa |
| `--net-presumed` | `#e0c573` | Rede presumida (a conferir) | Linha tracejada no mapa |

Preenchimento dos municípios no mapa (semitransparente sobre o fundo): atingida `#267456`, atenção `#736423`,
abaixo do alerta `#6e3544`, sem meta `#2e3f46`, com borda na cor cheia do estado.

**Regra:** cor nunca é o único sinal. Todo estado leva **texto** ("Atenção"), e os imóveis levam também **ícone**
e posição na legenda.

### Tema claro (opcional, [Proposta])

| Token | Valor |
|-------|-------|
| `--bg-1` | `#f4f8f7` |
| `--bg-2` | `#ffffff` |
| `--bg-3` | `#e9f1ef` |
| `--line` | `#d3e0dd` |
| `--text-1` | `#0b1a1f` |
| `--text-2` | `#3d5359` |
| `--accent` | `#0f9d86` (escurecido para contraste) |

## 7.3 Tipografia

| Papel | Família (aproximação) | Peso | Uso |
|-------|-----------------------|------|-----|
| Display | Grotesca larga e pesada (ex.: **Archivo** expandida, ou **Syne** / **Unbounded** como alternativa) | 700–800 | Aberturas, logotipo, títulos de impacto |
| Interface | Sans-serif neutra (ex.: **Inter** ou **Manrope**) | 400–600 | Menus, tabelas, parágrafos, botões |
| Dados | Monoespaçada (ex.: **JetBrains Mono** ou **IBM Plex Mono**) | 400–600 | Percentuais, matrículas, contagens, ritmo ("90/mês"), rótulos em caixa-alta |

Escala (rem, base 16 px):

| Token | Tamanho | Uso |
|-------|---------|-----|
| `--fs-kpi` | 4.5 | Indicador principal ("76,9%") |
| `--fs-h1` | 1.75 | Título de página ("Mapa", "Alertas") |
| `--fs-h2` | 1.125 | Título de cartão |
| `--fs-body` | 0.9375 | Texto e tabelas |
| `--fs-small` | 0.8125 | Apoio, rodapé do menu |
| `--fs-label` | 0.6875 | Rótulos em caixa-alta com espaçamento (`letter-spacing: .12em`), ex.: "INDICADOR CONTRATUAL" |

Números sempre com `font-variant-numeric: tabular-nums` e formato pt-BR (vírgula decimal, ponto de milhar).

## 7.4 Espaçamento, forma e profundidade

| Token | Valor |
|-------|-------|
| `--space-1…6` | 4, 8, 12, 16, 24, 32 px |
| `--radius-s` | 8 px (selos, campos) |
| `--radius-m` | 14 px (cartões, logo) |
| `--radius-l` | 20 px (painéis grandes, legendas em pílula: 999 px) |
| `--border` | 1 px solid `--line` |
| `--shadow-card` | `0 8px 30px rgba(0, 0, 0, .35)` |
| `--shadow-focus` | `0 0 0 1px var(--accent), 0 0 24px var(--accent-glow)` |
| Grade de fundo | linhas `rgba(255,255,255,.03)` a cada 48 px, apenas nas aberturas e em áreas vazias |

Layout: barra lateral fixa (~220 px) + cabeçalho com busca, assistente, notificações, tema e usuário + conteúdo
com largura máxima fluida; cartões em grade de 12 colunas.

## 7.5 Componentes

| Componente | Descrição visual |
|------------|------------------|
| **Barra lateral** | Logo + nome, seletor de escopo ("Seu escopo"), seções (ADESÃO, CONTRATO, ADMINISTRAÇÃO) em rótulo caixa-alta, item ativo com barra de 2 px em `--accent`, fundo `--bg-3` e texto `--accent`; contador vermelho nos Alertas; rodapé "Dados de Agosto de 2026 · N fontes atrasadas" com ponto de status |
| **Cabeçalho** | Busca com atalho (Ctrl+K), botão "Assistente" com ponto colorido (Ctrl+J), sino com contador, alternador de tema, avatar e nome |
| **Cartão** | `--bg-2`, borda `--line`, raio 14, rótulo caixa-alta no topo ("INDICADOR CONTRATUAL"), título, conteúdo |
| **KPI** | Número grande monoespaçado + unidade menor; abaixo: meta, alerta, cláusula e variação (verde/vermelho com ▲▼) |
| **Selo de situação** | Pílula com ponto + texto: Meta atingida (`--ok`), Atenção (`--warn`), Abaixo do alerta (`--danger`); fundo 12% da cor, texto na cor |
| **Barra de adesão** | Trilha escura; preenchimento `--accent-2`; marcador vertical na posição da meta |
| **Barra de meta** | Trilho em três faixas (vermelho → âmbar → verde) com marcador do valor da meta |
| **Quadro "No ritmo"** | Fundo `--bg-3`, faixa de 3 px à esquerda em `--accent`, números destacados em negrito monoespaçado |
| **Gráfico** | Adesão: linha cheia `--accent-2` com pontos; Projeção: tracejada `--accent-2`; Meta: tracejada `--ok`; Limite de alerta: pontilhada `--warn`; eixos em `--text-3`; opção "Ver como tabela" |
| **Tabela** | Cabeçalho `--text-3` pequeno, linhas com divisor `--line`, hover `--bg-3`, números alinhados à direita |
| **Chip de fonte** | Pílula com número em círculo `--accent` + rótulo truncado; clique expande a linha de dados |
| **Linha de dados expansível** | Cartão recolhível "Adesão · Cidade A Norte · ago/2026 · 1 linha", conteúdo em grade de pares rótulo/valor |
| **Alerta** | Cartão com selo (Crítico/Atenção), título, detalhe, "desde dd/mm hh:mm", ações "Ver" e "Reconhecer"; destaque com borda `--accent` e glow |
| **Legenda em pílula** | Texto curto centralizado na base, borda `--accent`, fundo `rgba(5,19,19,.85)` (apenas nas demonstrações) |
| **Botão primário** | Fundo `--accent`, texto `#04201b`, raio 10, glow no foco |
| **Botão secundário** | Fundo transparente, borda `--line`, texto `--text-1` |
| **Controles do mapa** | Barra flutuante: Vista 3D / Vista de cima, Marcar local, Exportar área; estado ativo com fundo `--accent` e texto escuro; dica "Clique no mapa onde quer marcar. Esc cancela." |
| **Painel de camadas** | Cartão à direita com caixas de seleção, amostras de cor e legenda de linha contínua/tracejada |
| **Menu de exportação** | Cartão com duas opções (Planilha CSV, PDF), descrição de uso e aviso "A emissão fica registrada na auditoria." |

## 7.6 Mapa

- Base: OpenStreetMap em estilo escuro (fundo `#1c2a22`, vias discretas).
- Rede: confirmada `--net-confirmed` (linha 3 px), presumida `--net-presumed` (tracejada).
- Imóveis: **ícone de casa** por lote (não ponto), cor pelo estado; localização aproximada com contorno pontilhado.
- Municípios: polígono com preenchimento semitransparente pelo estado e contorno `--accent` em foco.
- Unidades operacionais (ETE/EEE): marcador ciano; marcações de visita: pino na cor de destaque.
- Vista 3D: inclinação até ~60°, rótulos sempre legíveis (halo escuro).
- Em zoom baixo, imóveis agrupados (cluster) com contagem; em zoom alto, ícones individuais.

## 7.7 Movimento

| Elemento | Comportamento |
|----------|---------------|
| Transição de página | Fade + deslocamento de 8 px, 180 ms, `ease-out` |
| Destaque de elemento | Borda `--accent` com glow que surge em 150 ms |
| Gráfico | Linhas se desenham da esquerda para a direita em 600 ms |
| Contadores | Contagem até o valor em 500 ms, uma vez |
| Respeitar `prefers-reduced-motion` | Desligar animações e manter só mudanças de estado |

## 7.8 Acessibilidade

- Contraste mínimo **4,5:1** para texto; verificar `--text-3` e `--warn` sobre `--bg-2` (ajustar o tom se ficar abaixo).
- Cor nunca é o único sinal (texto, ícone, forma).
- Foco visível em todos os controles (`--shadow-focus`); navegação completa por teclado, atalhos Ctrl+K e Ctrl+J documentados.
- Gráficos com alternativa em **tabela** ("Ver como tabela"); mapa com lista equivalente ("Sem ligação").
- Idioma pt-BR em tudo, incluindo leitores de tela (`lang="pt-BR"`).

## 7.9 Implementação (Angular)

```scss
// src/styles/_tokens.scss
:root {
  --bg-0:#051313; --bg-1:#081018; --bg-2:#0c181c; --bg-3:#12212a; --line:#1c2e36;
  --text-1:#e8f0ee; --text-2:#b4c1c3; --text-3:#6f8288;
  --accent:#4ad7bc; --accent-2:#1acbd8; --accent-3:#2c9aeb;
  --accent-glow:rgba(74,215,188,.35);
  --ok:#3de693; --warn:#ecc32a; --danger:#e76069; --neutral:#86929e;
  --net-confirmed:#43c3c5; --net-presumed:#e0c573;
  --radius-s:8px; --radius-m:14px; --radius-l:20px;
  --shadow-card:0 8px 30px rgba(0,0,0,.35);
  --shadow-focus:0 0 0 1px var(--accent), 0 0 24px var(--accent-glow);
  --font-display:'Archivo', system-ui, sans-serif;
  --font-ui:'Inter', system-ui, sans-serif;
  --font-data:'JetBrains Mono', ui-monospace, monospace;
}
:root[data-theme='light'] {
  --bg-1:#f4f8f7; --bg-2:#ffffff; --bg-3:#e9f1ef; --line:#d3e0dd;
  --text-1:#0b1a1f; --text-2:#3d5359; --accent:#0f9d86;
}
```

- Centralizar tokens em `_tokens.scss`; **nenhum valor de cor fixo** nos componentes.
- Biblioteca de componentes própria (selo, KPI, chip de fonte, cartão, barra de adesão) em um módulo `ui/`.
- Se usar Angular Material ou PrimeNG, apenas como base, com tema sobrescrito pelos tokens.
- Mapa: estilo MapLibre em JSON lendo as mesmas cores (gerado a partir dos tokens).
- Alternância de tema pelo atributo `data-theme` e preferência salva por usuário.

## 7.10 Pendências visuais

| Item | Como resolver |
|------|---------------|
| Fontes exatas | Autor lembrar ou conferir o código antigo (se recuperado) |
| Tema claro | Não aparece nos vídeos (há o botão); definir na reconstrução |
| Telas não vistas (Contratos, Unidades, Usuários, Perfis, Integrações) | Seguir os mesmos componentes e tokens; validar com o autor |
| Contraste de `--text-3` e `--warn` | Medir com ferramenta antes de congelar os tokens |
