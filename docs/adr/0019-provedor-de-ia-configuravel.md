# 0019 · Provedor de IA configurável: Claude (padrão) ou OpenAI

**Contexto.** Os docs 05/09 e os ADRs 0014 e 0018 adotam a API do Claude. Em 23/10/2026 o dono do produto pediu
para poder usar também a OpenAI (a empresa tem contrato com ela). As regras de negócio (trecho literal conferido,
revisão humana, fontes numeradas, verificação de números, escopo) não podem depender do provedor.

**Decisão.**
- `aurora.ia.provedor` escolhe `claude` (padrão) ou `openai`; configuração única em `PropriedadesIa` (módulo
  `comum`). Chaves só por variável de ambiente/cofre: `AURORA_IA_CHAVE` (Claude), `AURORA_IA_OPENAI_CHAVE` e
  `AURORA_IA_OPENAI_MODELO` (OpenAI). Configuração incompleta desliga a IA, com aviso no log.
- Os adaptadores ficam atrás das portas que já existiam (`ExtratorContrato`, `ModeloDoAssistente`). A OpenAI usa o
  SDK oficial `openai-java` 4.79.0 (Apache-2.0), API de Chat Completions: ferramentas com `strict`, saída
  estruturada estrita para a extração (mesmo esquema `SaidaExtracao`), mesmas instruções e lotes.
- **Modelo da OpenAI sem padrão:** a escolha (e o contrato) é da empresa; `AURORA_IA_OPENAI_ESFORCO` só para modelos
  de raciocínio que aceitam `reasoning_effort`.
- **Custo pelo adaptador:** o do Claude pela tabela interna; o da OpenAI por preços configurados
  (`AURORA_IA_OPENAI_PRECO_*`, US$ por milhão de tokens). Sem preço, o uso é contado como "sem custo", sem inventar.
- Recusa da OpenAI (`refusal` ou filtro de conteúdo) tem o mesmo tratamento da recusa do Claude.

**Consequências.** Trocar de provedor é configuração, não código. O fallback do servidor e o cache explícito de
prompt são recursos do Claude; na OpenAI o cache é automático (contado em `cache_lido`). A qualidade da extração e
das respostas deve ser conferida ao trocar de provedor ou de modelo (revisão humana continua obrigatória).
