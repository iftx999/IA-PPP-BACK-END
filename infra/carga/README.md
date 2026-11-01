# Testes de carga (ADR 0034)

Metas do doc 09 (9.9). Resultados e histórico em [`docs/desempenho.md`](../../docs/desempenho.md).

## Leituras (k6)

Contra o ambiente local (ou de homologação) já no ar, com os dados de demonstração:

```bash
./preparar-cliente-de-carga.sh   # uma vez: client aurora-carga no Keycloak LOCAL (password grant)
docker run --rm -i --network host -v "$PWD:/carga" grafana/k6:1.3.0 run /carga/leitura.js
# mais carga: -e TAXA=80 -e DURACAO=5m ; outro ambiente: -e API=... -e KEYCLOAK=...
```

Mistura: painel 35%, troca de competência 15%, sem ligação 20%, tiles do mapa 30%, à taxa constante `TAXA`
(requisições/s). Reprova se o p95 de algum fluxo passar da meta ou se mais de 1% das requisições falhar.

O client `aurora-carga` só existe no Keycloak local: o script recusa outro servidor. Nunca crie client com password
grant em produção.

## Escrita (carga mensal e recálculo)

Teste JUnit com Testcontainers, fora do `verify` (tag `desempenho`):

```bash
cd backend
./mvnw test -Dtestes.excluidos= -Dgroups=desempenho -Dtest=DesempenhoDaCargaTest   # -Ddesempenho.imoveis=1000000
```

Gera 1 milhão de imóveis em 400 setores e carrega em dois lotes de 500 mil (limite do agente); reprova acima de
10 min na carga ou 2 min no recálculo. Também roda no GitHub Actions (workflow "Desempenho": manual e semanal).
