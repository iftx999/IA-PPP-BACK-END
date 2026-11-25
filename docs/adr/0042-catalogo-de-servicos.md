# 0042 · Catálogo de serviços de campo: serviços versionados com parâmetros de encerramento

**Contexto.** O autor aprovou (17/11/2026) a especificação do doc 10: a Aurora passa a gerir os serviços de campo da
operação de esgoto da PPP (catálogo, programação por ligação ou logradouro, rota otimizada e app nativo sem sinal).
As OS de hoje (ADR 0036) têm quatro tipos fixos no código. Este é o primeiro marco (M1, histórias J1 e J2): o
catálogo que as OS, a roteirização e o app vão usar. Número 0042 porque 0037–0040 estão em outras branches e 0041 fica
reservado ao atendimento ao titular; migração V31 pelo mesmo motivo (V29 e V30 em outras branches).

**Decisão.**
- **Módulo `servicos`** (V31) com sete tabelas, todas com RLS só pelo tenant (o catálogo vale para o cliente
  inteiro, sem escopo de unidade): `grupo_servico`, `servico`, `servico_versao`, `parametro_servico`, `motivo`,
  `tipo_veiculo` e `habilidade`.
- **Versionamento (RN-20):** `servico` guarda o que não é versionado (grupo, código, ativo, versão atual);
  `servico_versao` e `parametro_servico` guardam a definição (nome, descrição, unidade de medida, alvo, tempo padrão,
  prazos por prioridade, veículo e habilidade exigidos, conferência pela carga, parâmetros) e **só recebem inserção**
  (o papel da aplicação não tem `UPDATE` nelas). Salvar compara a definição normalizada com a atual: igual não cria
  versão; diferente cria a próxima. A OS (M3) guardará o id da versão.
- **Normalização no modelo** (`DefinicaoServico.normalizada()`): textos sem espaços extras, números sem zeros à
  direita, padrões da foto (mín. 1 se obrigatória, máx. 5) e do texto (máx. 500). É idempotente: é o que torna a
  comparação de versões confiável.
- **Parâmetros:** inteiro, decimal (com unidade), texto, lista, lista múltipla, sim/não, data e hora, foto; mínimo e
  máximo por tipo; até 40 por serviço; **condição** simples ("só quando o parâmetro X anterior, sim/não ou lista, for
  igual a Y"). Validados no modelo e no banco (`CHECK`).
- **Código** estável (maiúsculas, sem acento, `_`), gerado do nome quando não informado; não muda depois do cadastro
  porque integrações e OS antigas dependem dele.
- **Motivos** por tipo (paralisação, devolução, não execução, cancelamento), globais ou de um serviço; "OUTRO" sempre
  exige observação. Os motivos de recusa e inviabilidade da V28 viram motivos de não execução com o mesmo código.
- **Catálogo inicial** (doc 10, 10.5): 4 grupos, 12 serviços (os quatro tipos da V28 entre eles), 39 parâmetros,
  5 tipos de veículo, 4 habilidades e 26 motivos, numa função SQL (`semear_catalogo_servicos`) idempotente: a V31 a
  chama para os clientes existentes (com o RLS forçado de `tenant` suspenso só na transação, como na V28) e o
  provisionamento para os novos. Uma única fonte para os dois caminhos.
- **Permissão** `servico:gerenciar`; novo perfil do sistema **Gestor de serviços** (`servico:gerenciar`,
  `unidade:ler`; recebe `os:programar` no M3); o administrador também recebe `servico:gerenciar`.
- **API** `/api/v1/catalogo` (catálogo completo; grupos, veículos, habilidades, motivos) e `/api/v1/catalogo/servicos`
  (cadastro, alteração, versões). Auditoria: `SERVICO_CADASTRADO`, `SERVICO_ALTERADO` (com `novaVersao`),
  `ITEM_DO_CATALOGO_CADASTRADO` e `ITEM_DO_CATALOGO_ALTERADO`.
- **Telas** `/servicos` (serviços por grupo com filtros na URL; motivos; grupos, veículos e habilidades) e
  `/servicos/:id` (editor com parâmetros reordenáveis e **pré-visualização do formulário do app**, que testa as
  condições, e histórico de versões).

**Consequências.** O catálogo existe antes das OS que vão usá-lo: as OS da V28 continuam com os tipos fixos até o M3,
que migra `ordem_servico` para `servico_versao_id`. Nada é apagado: inativar mantém o histórico. Um grupo com serviço
ativo não pode ser inativado. Fica para os próximos marcos: equipe com veículo, habilidades, base e jornada (M5),
prazo em dias úteis com calendário de feriados (P6, M3) e o uso dos motivos nas ações de campo (M4).
