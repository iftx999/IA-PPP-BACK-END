# RIPD · Relatório de Impacto à Proteção de Dados Pessoais (rascunho)

> **Situação:** rascunho técnico preparado pela equipe de desenvolvimento a partir do que o sistema faz. Precisa ser
> revisado e aprovado pelo encarregado (DPO) e pelo jurídico do **controlador** — a concessionária cliente da Aurora,
> que decide finalidades e usos (LGPD, art. 5º, VI e art. 38). A Aurora atua como **operadora**. Este documento não
> substitui parecer jurídico.

| Item | Conteúdo |
|---|---|
| Sistema | Aurora — serviços de campo (doc 10; ADR 0042 a 0045) |
| Versão | 0.2 · 21/11/2026 |
| Operações cobertas | 1. Localização dos funcionários de campo · 2. Celular do morador para o aviso de chegada (ADR 0052) |
| Operações a incluir | Endereço dos imóveis (exceção à RN-09, ADR 0043); fotos das OS (cifradas no celular desde a ADR 0051) |

---

## 1. Localização dos funcionários de campo

### 1.1 Descrição do tratamento

| Dado | Quando é coletado | Onde fica | Guarda |
|---|---|---|---|
| Posição do celular (latitude, longitude, precisão, momento) | **Só com a jornada aberta**: a cada 2 min ou 100 m, menos com bateria baixa (RN-27) | `posicao_equipe` | **90 dias**, apagada por tarefa diária (P4) |
| Posição no início da jornada | Ao tocar "Iniciar jornada" | `jornada` | Com o registro da jornada |
| Posição de cada ação na OS (iniciar, paralisar, retomar, devolver, encerrar) e distância até o local | No momento da ação | `os_evento` | Com a OS (comprovação contratual) |
| Horários de início e fim da jornada | Ao abrir e encerrar a jornada | `jornada` | Com o registro da jornada |
| Identificação do celular (modelo, sistema, versão do app, último contato) | No cadastro e em cada conexão | `dispositivo` | Enquanto o celular estiver em uso; bloqueio registrado |

- **Titulares:** agentes de campo — empregados da concessionária e de empresas terceirizadas.
- **Celulares:** **da empresa** (P10, decidido em 18/11/2026). Não há coleta em celular pessoal.
- **Não é coletado:** posição fora da jornada; trajeto do agente fora do app; contatos, fotos ou outros dados do
  aparelho além dos listados. A navegação curva a curva é feita pelo Waze ou Google Maps instalados, que recebem só
  as coordenadas do próximo destino (sem dados do cliente).

### 1.2 Finalidades

1. **Roteirização** (RN-25): calcular a rota do dia a partir de onde a equipe está.
2. **Acompanhamento da execução** (J12): saber o andamento das OS e reagir a atrasos e emergências.
3. **Comprovação da execução do contrato de concessão:** mostrar ao poder concedente e ao regulador que o serviço foi
   feito no local e na hora informados (início longe do local fica marcado, RN-22).
4. **Localização dos imóveis** (J9): o GPS do encerramento corrige a localização aproximada do imóvel.

**Finalidade vedada:** controle de jornada para fins disciplinares fora do que está acima, e qualquer uso das posições
fora do horário de trabalho. Recomenda-se que o controlador registre isso na política interna.

### 1.3 Bases legais (a confirmar pelo controlador)

| Titular | Base legal proposta | Observação |
|---|---|---|
| Empregado da concessionária | Execução de contrato (art. 7º, V) e legítimo interesse (art. 7º, IX) | Fazer o teste de legítimo interesse (LIA) para o acompanhamento em tempo real |
| Empregado de terceirizada | Legítimo interesse do controlador (art. 7º, IX) | O contrato com a terceirizada deve prever a coleta e a informação aos empregados |
| Comprovação ao regulador | Cumprimento de obrigação legal ou regulatória (art. 7º, II) | Depende das cláusulas do contrato de concessão |

Consentimento **não** é a base recomendada: na relação de trabalho ele não é livre.

### 1.4 Necessidade e proporcionalidade

- Coleta **só com a jornada aberta**; o servidor recusa posições fora da janela da jornada (ADR 0045).
- O app mostra ao agente **quando a localização está sendo coletada** (M6, RN-27).
- Amostragem espaçada (2 min / 100 m), não contínua.
- Guarda curta das posições (90 dias); indicadores usam dados agregados, que não dependem das posições.
- Os eventos da OS guardam só a posição do momento da ação, não o trajeto.

### 1.5 Acesso e segurança

- **Quem vê:** gestores com escopo sobre a unidade da equipe (RLS por unidade, ADR 0013); o próprio agente vê as
  suas OS. A consulta das posições pela gestão (mapa do dia, M7) deve ser **auditada** — ainda não implementada.
- Isolamento por cliente (RLS forçado, ADR 0002); tráfego só por HTTPS; trilha de auditoria encadeada (ADR 0009).
- Celular: banco local criptografado com chave no Keystore, desbloqueio por PIN ou biometria (M6); celular perdido é
  **bloqueado** na tela "Celulares do app" e apaga os dados locais na próxima conexão (J13, auditado).
- Só os dados das OS da equipe no dia ficam no celular; o pacote é apagado ao encerrar a jornada.

### 1.6 Riscos e medidas

| Risco | Prob. | Impacto | Medidas | Risco residual |
|---|---|---|---|---|
| Vigilância excessiva do trabalhador (uso das posições além das finalidades) | Média | Alto | Coleta só na jornada; finalidade vedada na política interna; consulta auditada (M7); acesso só pelo escopo da equipe | Médio, até a auditoria da consulta (M7) e a política interna |
| Coleta fora do horário de trabalho | Baixa | Alto | Servidor descarta posições fora da jornada; indicador visível no app; jornada não fecha com OS em execução, mas fecha a qualquer momento sem ela | Baixo |
| Vazamento de deslocamentos e rotinas dos agentes | Baixa | Alto | RLS, HTTPS, guarda de 90 dias, celular da empresa com bloqueio remoto, banco local criptografado | Baixo |
| Celular perdido ou roubado com dados das OS | Média | Médio | Bloqueio remoto com limpeza; PIN/biometria; só o pacote do dia no aparelho | Baixo |
| Uso discriminatório (avaliação de desempenho só por posição) | Média | Médio | Indicadores por equipe e serviço, não por pessoa (J12); revisão humana de "início fora do local" | Médio — depende da política do controlador |
| Terceirizados não informados da coleta | Média | Médio | Cláusula no contrato com a terceirizada; aviso no primeiro acesso ao app | Baixo após as ações |
| Guarda além do necessário | Baixa | Médio | Expurgo automático diário (90 dias, configurável) | Baixo |

### 1.7 Direitos dos titulares e transparência

- **Aviso de privacidade** ao agente no primeiro login no app (M6): o que é coletado, quando, para quê e por quanto
  tempo.
- Pedidos de acesso e correção vão ao encarregado do controlador; a Aurora fornece o extrato das posições e eventos
  do agente quando o controlador pedir (a implementar junto com o M7).
- Informar a representação dos trabalhadores, quando houver, antes de ligar a coleta.

### 1.8 Ações pendentes

| # | Ação | Responsável | Quando |
|---|---|---|---|
| 1 | Revisão e aprovação deste RIPD pelo DPO | Controlador | Antes de usar o app em produção |
| 2 | Teste de legítimo interesse (LIA) | Controlador | Idem |
| 3 | Política interna de uso das posições (finalidade vedada) | Controlador | Idem |
| 4 | Aviso de privacidade e indicador de coleta no app | Desenvolvimento | M6 |
| 5 | Auditoria da consulta às posições e extrato por agente | Desenvolvimento | M7 |
| 6 | Cláusula de coleta nos contratos com terceirizadas | Controlador | Antes de incluir equipes terceirizadas |

---

## 2. Celular do morador para o aviso de chegada ("Estou chegando", ADR 0052)

### 2.1 Descrição do tratamento
- **Dado:** número de celular ligado à matrícula do imóvel, recebido da concessionária em arquivo
  (`matricula;telefone`). Não há nome, CPF nem outro dado do morador.
- **Uso:** quando o agente toca "Estou chegando" numa OS programada daquele imóvel, a Aurora envia um SMS: a equipe
  está a caminho (serviço e tempo estimado), sem nome do agente nem do morador. Um aviso por OS.
- **Quem envia o SMS:** um provedor contratado pela concessionária (suboperador). Enquanto não houver provedor
  configurado, nada é enviado: o aviso só fica registrado.

### 2.2 Finalidade e base legal (a confirmar pelo controlador)
- Finalidade: evitar visita perdida (imóvel fechado) e avisar o morador da chegada da equipe para um serviço que
  ele pediu ou que a concessionária precisa fazer no imóvel.
- Bases possíveis: execução de contrato de prestação do serviço (art. 7º, V) ou legítimo interesse (art. 7º, IX),
  com o morador podendo pedir para não receber (a concessionária manda a linha com o telefone em branco).

### 2.3 Minimização e segurança
- O número fica **cifrado** no banco (AES-256-GCM, chave fora do banco: `AURORA_CHAVE_CONTATOS`); em claro só os
  4 últimos dígitos, para conferência. Decifrado só no servidor, no momento do envio.
- **Nunca** vai para o app do agente, para as telas da web, para os logs ou para a trilha de auditoria.
- Importação só por quem gerencia logradouros e endereços (`logradouro:gerenciar`), e só para imóveis com endereço
  importado; cada importação e cada aviso ficam na trilha (sem o número).
- Só celular (SMS); número inválido ou fixo é recusado na importação.

### 2.4 Retenção
- O número fica enquanto a concessionária mantiver o imóvel no arquivo; a linha com o telefone em branco o apaga.
  Trocar a chave torna ilegíveis os números já importados.
- O registro do aviso (OS, hora, situação, 4 últimos dígitos) segue a guarda das OS.

### 2.5 Ações pendentes

| # | Ação | Responsável | Quando |
|---|---|---|---|
| 1 | Confirmar a base legal e o texto do SMS | Controlador | Antes de ligar o envio |
| 2 | Contratar o provedor de SMS (contrato de suboperação) e configurar o adaptador | Controlador / Desenvolvimento | Idem |
| 3 | Canal para o morador pedir para não receber (opt-out) e atualização do arquivo | Controlador | Idem |

