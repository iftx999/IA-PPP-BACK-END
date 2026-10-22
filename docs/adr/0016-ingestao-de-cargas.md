# 0016 · Ingestão de cargas pelo Agente Aurora e por arquivo

**Contexto.** A adesão vem da situação mensal dos imóveis (RN-01). Os dados nascem no sistema comercial da
concessionária; o endereço não pode sair de lá (RN-09). Fontes têm atraso máximo (RN-08) e o histórico de cargas
precisa mostrar o que entrou e o que foi recusado (H3).

**Decisão.**
- **Duas entradas, um núcleo** (`CargaService`): o Agente Aurora envia JSON a `/api/agente/v1/cargas`; a tela
  envia CSV (`matricula;unidade;situacao`) a `/api/v1/integracoes/fontes/{id}/cargas`. Ambos produzem registros
  (matrícula, código da unidade, situação) — nunca nome nem endereço.
- **Execução registrada antes de começar** (transação própria), para que uma falha também apareça no histórico.
  Validação linha a linha: o que está errado é recusado com motivo (as 100 primeiras recusas detalhadas, todas
  contadas) sem derrubar a carga; carga sem nenhum registro válido falha.
- **Gravação em lote numa instrução** (`unnest` de arrays + `ON CONFLICT`), sem reescrever linha igual; `xmax = 0`
  separa novos de atualizados. Cargas são incrementais por competência (não apagam o que não veio).
- **Medições recalculadas na mesma transação** pelo módulo de adesão (`Medicoes.recalcular`): ligados ÷
  (ligados + factíveis + cortados) da subárvore de cada unidade, numa instrução; cria o indicador de adesão se o
  cliente ainda não o tem.
- **Evento `CargaConcluida`** depois do commit: o módulo de alertas reavalia o tenant na hora (a fonte deixa de
  estar atrasada), sem que integrações dependa de alertas.
- **Autenticação do agente:** cadeia de segurança própria para `/api/agente/**` (antes da cadeia OIDC), credencial
  `prefixo.segredo` conferida pelo SHA-256 em tempo constante, limite de 60 requisições/min por credencial
  (`limite_requisicao`), contexto do tenant da credencial; a fonte vem da credencial, nunca do corpo. Token OIDC
  de usuário não vale nessa API.
- Carga pela tela exige `integracao:gerenciar` no tenant inteiro (atualiza dados de todas as unidades).

**Consequências.** Até 500 mil registros por carga, processados na requisição (alguns segundos para a base
atual); acima disso, dividir ou levar para fila (`tarefa_em_fila`). Localização dos imóveis (ponto, E6) fica para
o Marco 5 (GIS).
