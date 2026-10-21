# 3. Regras de negócio

Os exemplos numéricos vêm do vídeo de demonstração (dados fictícios) e foram conferidos.

## RN-01 · Adesão

```
adesão (%) = imóveis ligados / imóveis com rede disponível × 100
```

- "Com rede disponível" = ligados + factíveis sem ligação + cortados.
- Imóveis **sem rede** ficam fora do denominador.
- Calculada por **competência (mês)** e para cada nível: setor, município e concessão.
- A adesão de um nível superior é a soma dos ligados e dos com rede dos níveis abaixo (não a média dos percentuais).

**Exemplo [Visto]:** Concessão A em ago/2026: 8.761 ligados de 11.400 com rede → 76,85% (exibido 76,9%).

## RN-02 · Imóveis sem ligação

```
sem ligação = com rede disponível − ligados = factíveis + cortados
```

**Exemplo [Visto]:** 11.400 − 8.761 = **2.639** = 2.431 factíveis + 208 cortados.

| Situação do imóvel | Significado |
|--------------------|-------------|
| Ligado | Tem ligação ativa à rede |
| Factível | Rede disponível, nunca se ligou (ou sem ligação ativa) |
| Cortado | Teve ligação, foi cortado |
| Sem rede | Rede ainda não chega ao imóvel; fora da adesão |

- "Sem ligação há" = meses desde a última situação ligada. "Origem" mostra o último mês ligado (ex.: "Ligado até jul/2026").
- *A confirmar:* a regra exata que separa "factível" de "cortado" no sistema comercial de origem.

## RN-03 · Situação da unidade (semáforo)

Cada meta tem **percentual de meta** e **limite de alerta** (alerta ≤ meta).

| Condição | Situação | Severidade do alerta |
|----------|----------|----------------------|
| adesão ≥ meta | **Meta atingida** | — |
| limite de alerta ≤ adesão < meta | **Atenção** | Atenção |
| adesão < limite de alerta | **Abaixo do alerta** | Crítico |
| unidade sem meta aplicável | **Sem meta** | — |

**Exemplos [Visto]:**
- Concessão A: 76,9% com meta 80% e alerta 75% → **Atenção**.
- Cidade B: 68,0% com meta 75% e alerta 70% → **Abaixo do alerta**.
- Cidade A: 85,4% com meta 85% → **Meta atingida**.

## RN-04 · Variação

`variação (p.p.) = adesão do mês − adesão do mês anterior`. Positiva em verde, negativa em vermelho.
**Exemplo:** +0,7 p.p. na concessão A.

## RN-05 · Projeção "no ritmo"

1. **Ritmo atual** = média mensal de novas ligações nos **últimos 6 meses**, **excluindo meses em que a rede mudou** (expansão altera o denominador).
   Implementação: a janela recua até 6 meses **enquanto o tamanho da rede for o atual**; o mês da mudança e os anteriores
   saem. É a única leitura que reproduz o demo: rede cresceu em abr/2026 → base mai–ago = (8.761 − 8.357) / 4 = **101/mês**
   (excluir só abril daria 75/mês).
2. **Denominador futuro** = rede do tamanho atual (sem prever expansão).
3. **Projeção na data da meta** = (ligados atuais + ritmo × meses restantes) / com rede.
4. **Ligações que faltam** = meta% × com rede − ligados.
5. **Ritmo necessário** = faltam / meses restantes até o fim da vigência.
6. **Veredito:** se a projeção ≥ meta, "No ritmo, a meta sai em <mês>"; senão, indicar o déficit.

**Exemplo [Visto]** (ago/2026, meta 80% até dez/2026):
- Faltam: 0,80 × 11.400 − 8.761 = **359**.
- Meses restantes: set, out, nov, dez = **4** → ritmo necessário ≈ **90/mês**.
- Ritmo atual: **101/mês** → projeção = (8.761 + 101×4) / 11.400 = **80,4%**.

## RN-06 · Metas

- Uma meta pertence a **uma unidade** e a um **período de vigência** (início; fim opcional = "sem fim").
- **Origem:** `CONTRATO` (vem de item aprovado, com contrato, cláusula e página) ou `MANUAL`.
- **Herança:** unidade sem meta própria vigente usa a meta da unidade acima; a tela mostra "Segue <unidade> · <meta>".
- **Meta própria** substitui a herdada na unidade (e é herdada pelas filhas).
- Metas de contrato só nascem de **extração aprovada** (RN-10).
- Vigências da mesma unidade não podem se sobrepor (a confirmar: o que fazer em aditivo).

## RN-07 · Alertas

| Tipo | Gatilho | Severidade |
|------|---------|------------|
| Adesão abaixo do limite de alerta | RN-03 = Abaixo do alerta | Crítico |
| Adesão abaixo da meta | RN-03 = Atenção | Atenção |
| Fonte de dados atrasada | `agora − última carga > atraso máximo` | Atenção |

- Verificação **a cada 15 minutos**.
- O alerta tem estados **Aberto → Reconhecido** e **Resolvido automaticamente** quando a condição deixa de valer.
- Não duplicar alerta aberto para a mesma unidade e condição.
- Mensagem inclui adesão, meta, limite, competência e desde quando.

## RN-08 · Dados atrasados

- Cada fonte tem **atraso máximo** configurável (ex.: 2 dias).
- O rodapé do menu mostra "Dados de <mês> · N fontes atrasadas".
- Indicadores exibidos com fonte atrasada continuam visíveis, mas com aviso de que podem estar desatualizados.

## RN-09 · Localização dos imóveis e privacidade

- O endereço **não sai da concessionária**. O agente local cruza matrícula/endereço com o **CNEFE do IBGE** e envia à Aurora somente **matrícula + ponto**.
- Sem correspondência exata → ponto marcado como **localização aproximada**.
- A Aurora **não armazena nome do cliente nem endereço** (a confirmar para a nova versão; é a decisão de projeto).
- Rede de esgoto: trecho **confirmado** (linha contínua) ou **presumido, a conferir** (tracejada).

## RN-10 · Extração de contrato pela IA

1. Upload do PDF cria uma **extração** (status Processando → Pendente de revisão).
2. A IA gera **itens** com: tipo, unidade alvo, valores, vigência, **cláusula, página e trecho**.
3. O usuário **aprova, edita ou rejeita** cada item. Edições preservam o valor original da IA.
4. Só itens **aprovados** geram metas (origem Contrato) e referências de cláusula.
5. Item sem cláusula/página identificável não pode ser aprovado sem o usuário preencher a referência.
6. Aprovação e rejeição são auditadas (quem, quando, o quê).
7. A IA **nunca** grava direto em metas.

## RN-11 · Assistente

- Respostas só com números vindos das bases da plataforma.
- Cada número exibe **fonte** (chip numerado) e **linhas de dados** expansíveis.
- Respeita o **escopo** e as permissões do usuário (não revela unidades fora do escopo).
- Sem dado disponível → informa que não encontrou, em vez de estimar.
- Registra feedback "Ajudou? Sim / Tem erro" para melhoria.

## RN-12 · Exportações e auditoria

- Exportação (CSV/PDF) do painel, da lista sem ligação e da área do mapa respeita o escopo do usuário.
- Cada emissão gera **registro de auditoria**: usuário, data/hora, tipo, filtros/área, quantidade de linhas.
- A exportação do mapa inclui **apenas o que está visível** (camadas ligadas e área atual).

## RN-13 · Escopo e permissões

- Todo dado pertence a um **tenant** e a uma **unidade** (concessão → município → setor).
- Perfis definem **o que** o usuário faz (ver, aprovar contrato, editar metas, exportar, administrar).
- O escopo define **onde** o usuário atua (unidades atribuídas e descendentes).
- Acesso permitido = mesmo tenant **E** perfil tem a permissão **E** unidade no escopo do usuário.
- Totais e indicadores agregados mostram apenas dados das unidades do escopo.
- Sugestão de perfis iniciais [Proposta]: Administrador, Gestor, Analista de contratos, Analista comercial, Campo, Leitura.

## RN-15 · Isolamento por tenant

- Toda tabela de negócio tem `tenant_id`; o banco aplica **Row-Level Security** com `app.tenant_id` definido pela API a partir do token.
- Tenant nunca vem do corpo ou da URL da requisição como fonte de verdade.
- Arquivos (PDFs), cache, filas, jobs e logs são segregados por tenant.
- Acesso a recurso de outro tenant retorna **404**.
- Exportações, mapa e assistente aplicam o mesmo filtro.
- Testes automatizados de isolamento são obrigatórios no CI.

## RN-16 · Administração e perfis

- Perfis do sistema não podem ser editados nem apagados; o tenant pode criar perfis próprios a partir do catálogo de permissões.
- Um usuário pode ter mais de um perfil; a permissão efetiva é a **união** das permissões, limitada pelo escopo.
- Não é permitido remover ou desativar o **último administrador** do tenant.
- Desativar usuário preserva histórico e auditoria; sessões ativas são revogadas.
- Mudanças de perfil, permissão e escopo são **auditadas** e valem na próxima requisição.

## RN-17 · Autenticação

- Senha com hash forte, bloqueio progressivo após falhas, recuperação por e-mail com link de uso único.
- MFA opcional por tenant e obrigatório para administradores.
- Convites expiram e são de uso único.
- Troca de tenant gera novo token e é auditada.

## RN-14 · Competência

- Todos os indicadores são calculados por **mês de competência**.
- O seletor lista apenas competências com carga concluída.
- A série histórica usa as competências disponíveis; meses em que a rede mudou são sinalizados (RN-05).
