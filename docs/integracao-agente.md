# Integração do Agente Aurora

O Agente Aurora roda **dentro da rede da concessionária**. Ele lê o sistema comercial e envia à Aurora só o que
é necessário para a adesão: **matrícula, código da unidade e situação** de cada imóvel na competência. Nome e
endereço nunca saem da concessionária (RN-09). Decisões técnicas no ADR 0016.

## Credencial

1. Em **Administração › Integrações**, cadastre (ou use) uma fonte do tipo *Agente Aurora*.
2. Em *Credenciais do agente*, gere uma credencial. A chave (`aur_xxxxxxxxxx.<segredo>`) aparece **uma única
   vez**; guarde-a no cofre de segredos do servidor do agente. A Aurora guarda só o SHA-256 do segredo.
3. Para trocar, gere outra, configure o agente e revogue a antiga.

Todas as chamadas levam `Authorization: Bearer <chave>`. Limite: 60 requisições por minuto por credencial
(acima disso, `429` com `Retry-After`).

## Conferir a conexão

```bash
curl -s https://<aurora>/api/agente/v1/situacao -H "Authorization: Bearer $AURORA_CHAVE"
# {"fonte":"…","nome":"Sistema comercial","ativa":true,"ultimaCarga":"2026-10-20T14:19:36Z"}
```

## Enviar a carga da competência

```bash
curl -s https://<aurora>/api/agente/v1/cargas -H "Authorization: Bearer $AURORA_CHAVE" \
  -H "Content-Type: application/json" -d '{
    "competencia": "2026-09",
    "registros": [
      {"matricula": "100001", "unidade": "CA-CENTRO", "situacao": "LIGADO"},
      {"matricula": "100002", "unidade": "CA-CENTRO", "situacao": "FACTIVEL"}
    ]}'
```

- `unidade`: código da unidade cadastrado na Aurora (setor comercial, município ou concessão).
- `situacao`: `LIGADO`, `FACTIVEL`, `CORTADO` ou `POTENCIAL` (sem rede); aceita também "Factível" e "Sem rede".
- Até 500 mil registros por chamada; para mais, divida em várias chamadas da mesma competência (a carga é
  incremental: cada chamada grava ou atualiza só as matrículas enviadas).

Resposta: execução, `lidos`, `novos`, `atualizados`, `inalterados`, `rejeitados`, as 100 primeiras recusas
(`linha`, `matricula`, `motivo`) e quantas unidades tiveram a adesão recalculada. Linhas inválidas não derrubam a
carga; carga sem nenhuma linha válida responde `422` e fica como falha no histórico.

| Status | Quando |
|--------|--------|
| 200 | Carga gravada (pode ter recusas) |
| 401 | Credencial ausente, inválida, revogada ou cliente inativo |
| 409 | Fonte inativa |
| 422 | Competência futura, carga vazia ou nenhuma linha válida |
| 429 | Limite de requisições |

Depois de cada carga a Aurora recalcula a adesão de todas as unidades na competência e reavalia os alertas (a
fonte deixa de aparecer como atrasada).
