# 0040 · Homologação e implantação automática

**Contexto.** As imagens já eram publicadas só com o CI verde (ADR 0022), mas a implantação era manual: editar o
`.env` no host e subir o compose. Não havia ambiente de homologação, a volta de versão também era manual e o runbook
usava uma única tag (`AURORA_VERSAO`) para API e front, embora a imagem do front seja publicada com o SHA do
repositório **dele**: seguir o runbook levaria a uma tag inexistente para `aurora-web`. O doc 09 pede homologação e
implantação automatizada.

**Decisão.**
- **Tags separadas:** `AURORA_VERSAO` (aurora-api e aurora-postgres) e `AURORA_VERSAO_WEB` (aurora-web). As imagens
  passam a ter também a tag com o SHA completo, que é o que o CI conhece.
- **`infra/producao/implantar.sh`** no host, único caminho de implantação: backup na hora (contínuo e lógico), `infra/`
  no commit pedido, troca das tags, `pull`, `up --wait` (healthchecks) e conferência pela borda (front e API pelo
  domínio). Falhou: volta sozinho ao commit e às tags anteriores. Trava contra implantações simultâneas, registro em
  `implantacoes.log`, argumentos validados por expressão regular e recusa de commit que não tenha o próprio script.
- **Chave SSH restrita:** no host, `command="…/implantar.sh",restrict` no `authorized_keys`; a chave do GitHub não abre
  shell, só passa os três argumentos (via `SSH_ORIGINAL_COMMAND`).
- **Homologação automática:** no push no `main` com CI verde, cada repositório implanta o seu componente no ambiente
  `homologacao` (ligado pela variável `IMPLANTAR_HOMOLOGACAO`, para não falhar enquanto o ambiente não existe).
- **Produção manual com aprovação:** workflow "Implantar" (back-end), ambiente `producao` do GitHub com revisor
  obrigatório e só a partir do `main`; recebe as duas tags já validadas em homologação.
- Homologação é um host igual ao de produção, com segredos próprios e só dados fictícios ou anonimizados.

**Verificação.** O `implantar.sh` foi testado com um host simulado (clone do repositório, `docker` e `curl`
substituídos): argumentos inválidos, curingas e tentativas de injeção recusados; implantação de API, só do front e de
commit; falha na conferência voltando ao estado anterior; commit sem o script recusado antes do backup. Workflows
conferidos com actionlint e o script com shellcheck. A ida real a um host depende dos segredos de ambiente.

**Consequências.**
- Toda versão passa por homologação antes de produção, e a implantação fica rastreável (Actions e `implantacoes.log`).
- A volta automática não desfaz migração incompatível do banco: nesse caso, restauração para o instante anterior à
  implantação (ADR 0039).
- Host único por ambiente: a implantação tem alguns segundos de indisponibilidade na troca dos contêineres.
