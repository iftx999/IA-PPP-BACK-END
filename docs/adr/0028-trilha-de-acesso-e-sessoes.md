# 0028 · Trilha de acesso e sessões: eventos do Keycloak copiados para a auditoria

**Contexto.** O doc 08 (8.7) exige na trilha login, falha de login, bloqueio e troca de tenant, e o 8.6 pede sessões
revogáveis com "sair de todos os dispositivos". O login acontece no Keycloak (ADR 0003): a API só vê tokens já
emitidos e não sabe de tentativas que falharam nem de bloqueios por força bruta.

**Decisão.**
- **Origem:** cada realm de cliente grava os eventos `LOGIN`, `LOGIN_ERROR`, `LOGOUT`,
  `USER_DISABLED_BY_TEMPORARY_LOCKOUT` e `USER_DISABLED_BY_PERMANENT_LOCKOUT` por 30 dias (`eventsExpiration`). A conta
  de serviço `aurora-api-admin` ganha `view-events`. Realms novos já nascem assim (`realm-modelo.json`); os existentes
  rodam `atualizar-realm-eventos.sh` uma vez.
- **Cópia para a trilha:** a tarefa `importar-eventos-de-acesso` (a cada 5 min; 20 s no ambiente local; trava em
  `tarefa_agendada`, como os alertas) lê no Admin REST API, para cada tenant, os eventos desde o último importado menos
  5 min de margem, do mais antigo ao mais novo (`direction=asc`, até 2.000 por execução). A leitura fica fora da
  transação; a gravação na trilha é uma transação por tenant. `evento_acesso_importado` (V24, com RLS) guarda o id do
  evento no Keycloak para não importar duas vezes (linhas com mais de 35 dias são apagadas).
- **Na trilha:** `LOGIN`, `LOGIN_FALHOU` (com o código do erro, ex.: `invalid_user_credentials`), `CONTA_BLOQUEADA`
  (`TEMPORARIO` ou `PERMANENTE`) e `LOGOUT`, com o momento e o IP **do evento original**
  (`Auditoria.registrarOcorrido`) e o usuário da Aurora com aquele `sub`, ativo ou não. O IdP do SSO corporativo, se
  houver, entra nos detalhes. **O nome digitado no login nunca sai do Keycloak:** falha com conta inexistente fica sem
  usuário, em `acesso/conta-desconhecida`, só com o motivo. IP que não é IP válido fica de fora (a coluna é `inet`).
- **Troca de tenant:** "Trocar de empresa" (ADR 0027) encerra a sessão num realm e entra em outro; ficam `LOGOUT` na
  trilha do primeiro e `LOGIN` na do segundo. Não há evento próprio porque a pessoa é outra conta em cada realm.
- **Sessões:** `DELETE /api/v1/eu/sessoes` ("Sair de todos os dispositivos", no menu da conta) e
  `DELETE /api/v1/usuarios/{id}/sessoes` (administrador, em Usuários: aparelho perdido, suspeita de acesso indevido)
  chamam `POST /users/{id}/logout` do Keycloak e registram `SESSOES_ENCERRADAS` (quem pediu: a própria pessoa ou o
  administrador). Os tokens de atualização deixam de valer na hora; o de acesso expira em até 5 min
  (`accessTokenLifespan`). O Keycloak não gera `LOGOUT` nesse caso: o registro é o `SESSOES_ENCERRADAS`.

**Alternativas descartadas.** *Event listener* SPI no Keycloak enviando para a API: exigiria um JAR próprio no
Keycloak, versão a versão, e uma rota de entrada na API a proteger; a leitura periódica usa só a API pública. Registrar
o login quando o front chama `/eu`: não vê falhas nem bloqueios e duplica a cada recarga.

**Consequências.** Um login aparece na Auditoria em até 5 min. Se a API ficar mais de 30 dias sem importar, os
eventos mais antigos se perdem (o Keycloak já os apagou). A ordem na trilha é a da importação: um login pode ficar
depois de uma ação que aconteceu minutos depois dele, mas o horário mostrado é o original.
