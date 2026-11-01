# 0035 · Simulador de penalidades: regra calculável na penalidade aprovada e projeção mês a mês

**Contexto.** O painel mostra se a meta sai "no ritmo" (RN-05), mas não quanto custa ficar abaixo dela. As penalidades
já são extraídas e aprovadas (itens `PENALIDADE`), porém como texto: no contrato fictício são 30, e só uma tem cálculo
direto ("multa de 0,1% do faturamento mensal por ponto percentual abaixo da meta"). A base dessa multa (faturamento)
não está no contrato nem na Aurora.

**Decisão.**
- **Regra calculável** (`regra_penalidade`, V27) presa a um item `PENALIDADE` **aprovado**, definida por quem tem
  `contrato:gerenciar`: fórmula (`PERCENTUAL_DA_BASE_POR_PONTO`, `VALOR_POR_PONTO`, `VALOR_FIXO`), valor, base em reais
  e sua descrição (só na fórmula percentual), contagem dos pontos (`PROPORCIONAL` ou `POR_PONTO_OU_FRACAO`),
  aferição (`MENSAL` ou `ANUAL` a partir do prazo) e teto opcional por aferição. O texto aprovado não muda; a tela
  sugere a fórmula e o valor a partir do item ("%", "anual", "ou fração"). Definir e remover são auditados
  (`REGRA_DE_PENALIDADE_DEFINIDA`/`_REMOVIDA`) sem a base em reais.
- **Onde a multa é aferida:** na unidade do item ou, sem ela, na do contrato. A simulação de outra unidade não aplica
  a regra; a tela lista as unidades com penalidade calculável.
- **Simulador** (módulo `simulador`, só leitura, `GET /api/v1/simulador`): a partir da competência mais recente,
  ligados = ⌊ligados₀ + ritmo × meses⌋ (no máximo o total com rede), com a rede do tamanho atual (como RN-05) e a meta
  aplicável hoje (RN-06). Depois do prazo, essa meta continua sendo a referência da multa. Compara o ritmo atual com
  o cenário pedido (padrão: o ritmo inteiro necessário para a meta no prazo) até 0–60 meses depois do prazo.
- **Acesso:** a projeção exige `indicador:ler`; as regras são lidas só onde a pessoa também tem `contrato:ler`
  (`ContextoAcesso.restritoATodas`). Sem isso, a projeção sai sem multa e a tela explica.
- Tudo é rotulado como **estimativa**; não substitui a apuração do regulador.

**Consequências.** A gestão vê quanto custa cada ritmo e quantas ligações por mês evitam a multa, ligando metas,
contratos e o trabalho de campo. Ficam fora: regras não lineares (faixas, reincidência), reajuste da base pelo IPCA e
expansão da rede. As campanhas de ligação (próximo marco) devem alimentar o cenário.
