# 0039 · Backup contínuo com restauração a um instante (pgBackRest)

**Contexto.** O backup era só um `pg_dump` diário (ADR 0022): RPO de 24 h, cópia para fora do host manual e nenhuma
forma de voltar ao minuto antes de um erro (ex.: carga errada ou exclusão indevida às 14:32). O doc 09 (9.6) pede
pgBackRest ou serviço gerenciado com PITR. Numa concessionária que presta contas de metas contratuais, perder um dia
de dados ou o histórico de cargas não é aceitável.

**Decisão.**
- **pgBackRest** na imagem `aurora-postgres` (pacote do PGDG). O PostgreSQL arquiva o WAL (`archive_command`) com
  `archive_timeout = 60 s`: perda máxima de cerca de um minuto.
- **Serviço `backup-pitr`** com a mesma imagem: completo semanal, diferencial diário, `verify` semanal. Lê os arquivos
  de dados pelo mesmo volume e fala com o PostgreSQL pelo socket local num volume compartilhado (sem senha nem porta
  de rede). Na primeira subida cria o stanza, confere o arquivamento e já faz o backup completo.
- **Dois repositórios cifrados (AES-256):** o volume local (rápido, contra erro humano e corrupção) e, por
  `pitr/pitr.env`, um bucket S3 fora do host (contra perder o host), com retenção maior. A chave fica só no `.env`
  e no cofre de segredos.
- **`pitr/restaurar.sh`** para um instante ou para o mais recente. Nunca apaga: renomeia o diretório de dados e, se a
  restauração falhar, devolve-o e religa os serviços.
- O `pg_dump` diário continua: restauração seletiva, formato independente da versão do PostgreSQL e o único backup do
  banco do Keycloak.

**Verificação.** Testado de ponta a ponta num projeto Compose isolado: dados gravados, instante anotado, "desastre"
(exclusão de 990 linhas), restauração para o instante (voltaram as 1.000 linhas, linha do tempo 2), backup completo e
diferencial na linha nova e `verify` limpo. O caminho de falha (instante anterior a qualquer backup) devolveu o
diretório original e religou o banco intacto. O repositório S3 não foi testado aqui (sem bucket disponível).

**Consequências.**
- RPO de cerca de 1 minuto; janela de restauração de 2 semanas no local e 8 no S3 (configuráveis).
- O volume do repositório local ocupa cerca de dois backups completos (compactados com zstd) mais o WAL do período.
- Perder `AURORA_BACKUP_CHAVE` torna os backups ilegíveis: ela precisa estar no cofre, fora do host.
- A restauração exige espaço para o diretório anterior até ele ser liberado.
- Falta alerta automático de backup atrasado (hoje só o log `FALHA no backup`); próximo passo: métrica no Prometheus.
