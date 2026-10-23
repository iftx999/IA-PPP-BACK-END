# 0018 · Assistente com fontes: ferramentas fixas, fontes numeradas pelo servidor e verificação dos números

**Contexto.** O épico G pede um assistente que responda em português sobre adesão, metas e alertas, mostrando a
fonte de cada número (G2) e nunca inventando número (G6, RN-11), respeitando escopo e permissões (RN-13). Um
modelo de linguagem, sozinho, pode errar contas ou "lembrar" valores.

**Decisão.**
- **Laço manual de ferramentas** com o SDK oficial `anthropic-java` (`claude-opus-5-5`, esforço `medium`, fallback
  do servidor ligado). Ferramentas fixas e parametrizadas, nunca SQL gerado pelo modelo: `consultar_adesao`
  (adesão, ligados, com rede, sem ligação, variação, meta e alerta aplicáveis com a origem contratual, falta para a
  meta, situação; regras do módulo de adesão via `Medicoes.detalhar`), `listar_unidades`, `listar_competencias`,
  `listar_alertas_abertos`. Esquemas estritos (`strict`, `additionalProperties: false`).
- **Escopo:** cada ferramenta roda numa transação curta só de leitura, no escopo onde a pessoa tem
  `assistente:usar` **e** `indicador:ler` (`ContextoAcesso.restritoATodas`, interseção de subárvores) e com o mesmo
  RLS das telas. Unidade fora do escopo volta como "não encontrada", sem revelar que existe. Tenant e escopo vêm do
  token, nunca do texto da pergunta.
- **Fontes numeradas pelo servidor:** cada resultado recebe um número de fonte na ordem da leitura e uma linha de
  dados com os valores formatados; o modelo só cita `[n]`. Marcas que não correspondem a fonte lida são removidas.
- **Verificação dos números (G6):** todo número da resposta precisa aparecer nas saídas das ferramentas, aceitando
  a escrita brasileira e o arredondamento; o que não bate é gravado em `numeros_sem_fonte` e a tela avisa. Inteiros
  de um dígito (contagens) ficam de fora.
- **Conversas:** a chamada ao modelo acontece fora de transação; pergunta e resposta são gravadas juntas no fim
  (falha do serviço não deixa pergunta órfã). O histórico volta ao modelo só como texto (até 12 turnos), sem as
  marcas de fontes antigas. RLS por pessoa: ninguém lê conversa alheia, nem administradores.
- **Custo e limite:** tokens e custo (tabela de preços por modelo em `CustoDoUso`) por resposta e por mês; limite
  mensal por cliente (`assistente_limite`) responde 429. Na trilha (`PERGUNTA_IA`), só identificadores, tokens e
  custo, nunca o texto da pergunta. Custo visível só para quem atua no cliente inteiro.
- Sem chave (`AURORA_IA_CHAVE`), o assistente fica desligado: a tela avisa e o histórico continua consultável.

**Consequências.** Perguntas que exigem conta nova (projeções, médias) recebem "a Aurora não tem esse dado pronto":
para responder, cria-se uma ferramenta que calcule no servidor. A verificação é uma rede de segurança, não prova:
um número igual a outro valor de fonte, mas usado no lugar errado, passa.
