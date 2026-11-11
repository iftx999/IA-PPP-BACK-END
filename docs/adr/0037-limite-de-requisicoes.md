# 0037 · Limite de requisições por pessoa na API e por IP na borda

**Contexto.** A API só tinha limite para o Agente Aurora (credencial do conector, contado no banco) e para a cota de
IA. Uma pessoa autenticada (ou um token vazado) podia disparar requisições sem limite, e o endpoint público
(`/api/v1/publico/**`, antes do login) ficava aberto a varredura. Contar no banco a cada requisição, como no agente,
custaria uma escrita por requisição do sistema inteiro.

**Decisão.**
- **Por pessoa, na API:** filtro `FiltroLimiteDeRequisicoes`, logo depois do contexto de acesso, com janela fixa de um
  minuto **em memória** por `tenant + usuário`. Padrão 1.200 por minuto (`AURORA_LIMITE_POR_MINUTO`), folgado para o
  mapa (dezenas de tiles por movimento) e apertado para automação abusiva. Acima do limite: 429 em Problem Details
  com `Retry-After`. A chave nunca é o IP: escritórios inteiros da concessionária saem pelo mesmo IP.
- **Por IP, na borda:** `limit_req` no nginx do front (repositório do front), com o IP real vindo do Caddy
  (`X-Forwarded-For`, só de redes privadas). Folgado em `/api/` (contra inundação) e apertado em `/api/v1/publico/`.
- Ambiente local e testes com limite alto (o k6 de `infra/carga` usa um só usuário a 40+ req/s); o comportamento é
  coberto por `LimitadorDeRequisicoesTest` e `LimiteDeRequisicoesApiTest`.

**Consequências.**
- Sem custo de banco por requisição. A memória é limitada: janelas de minutos passados são descartadas na virada.
- O limite vale por instância: com N réplicas da API o efetivo é N × o configurado. Se a implantação deixar de ser
  um host único (ADR 0022), trocar por contador compartilhado (Redis ou a tabela `limite_requisicao`).
- Reinício da API zera as contagens (aceitável para um limite de minuto).
