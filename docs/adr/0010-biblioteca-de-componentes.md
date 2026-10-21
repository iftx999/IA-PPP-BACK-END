# 0010 · Angular Material como base dos componentes de interface

**Contexto.** Os controles nativos do navegador (select, date, tabelas simples) deixavam a interface com aspecto
antigo. O doc 07 prevê um design system próprio, aceitando uma biblioteca como base desde que tematizada pelos
tokens. O PrimeNG 22 foi testado: visual adequado, mas a versão 22 (e as anteriores republicadas) exige licença
comercial paga por desenvolvedor para empresas acima de US$ 1 mi de faturamento ou 10 funcionários, e exibe aviso
de licença sem chave. Nenhuma versão gratuita é compatível com o Angular 22.

**Decisão.** Angular Material 22 (MIT, mantido pelo time do Angular) com:
- variáveis de sistema `--mat-sys-*` apontando para os tokens da Aurora (`src/styles.scss`), de modo que o tema
  claro/escuro continua controlado só por `data-theme`;
- sobrescritas por componente (campos, botões, tabelas, diálogos, calendário) para a identidade do doc 07;
- datas com `@angular/material-date-fns-adapter` e locale pt-BR (`dd/MM/yyyy`);
- componentes próprios onde o Material não tem equivalente (árvore de metas sobre `mat-table`, selo, barra de
  adesão);
- fontes e ícones (Material Symbols, Apache-2.0) servidos pela aplicação.

**Consequências.** Sem custo de licença nem chave; atualização no mesmo ritmo do Angular. Telas novas usam os
componentes do Material com o tema da Aurora; controles nativos de formulário não devem ser usados nas telas.
