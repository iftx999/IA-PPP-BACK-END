# 0020 · Aditivo contratual substitui metas com histórico

**Contexto.** A5 pede enviar aditivos e atualizar metas sem perder o histórico. RN-06 proíbe vigências sobrepostas na
mesma unidade (garantido no banco) e deixava em aberto o que fazer com aditivos (pendência 6 do doc 06). O schema
herdado já previa `item_contratual.status = 'SUBSTITUIDO'` e `substituido_por`.

**Decisão.**
- Aprovar um item META de um documento do tipo **ADITIVO** passa por `Metas.aditarDoContrato`. As metas **de
  contrato** da mesma unidade cujo período cruza a vigência nova:
  - começaram **antes**: são **encerradas na véspera** do aditivo;
  - começaram **no mesmo dia ou depois** (aditivo retroativo): saem das metas vigentes por inteiro.
  Nos dois casos a versão anterior vai para `meta_historico` (motivo `ENCERRADA_POR_ADITIVO` ou
  `SUBSTITUIDA_POR_ADITIVO`, ligada à meta nova) e o item contratual de origem fica `SUBSTITUIDO`, apontando para o
  item do aditivo. Eventos: `META_ENCERRADA` (motivo ADITIVO) e `META_SUBSTITUIDA`.
- **Meta manual** sobreposta impede a aprovação (409, com a orientação de encerrá-la antes): um aditivo não apaga
  decisão interna sem alguém responder por isso.
- A tabela `meta` continua só com vigências válidas (a exclusion constraint e as consultas de adesão não mudam);
  `meta_historico` é só de inserção (RLS por escopo, sem UPDATE de conteúdo nem DELETE).
- Itens substituídos por aditivo aparecem na lista do contrato (histórico); propostas trocadas por reprocessamento
  continuam ocultas (nunca valeram).

**Consequências.** Um aditivo com prazo que termina antes da meta antiga não "devolve" o restante da antiga: o
analista cadastra o período seguinte, se o contrato disser. Aditivos de itens que não são metas (obrigações, prazos)
ficam registrados como itens novos, sem substituição automática.
