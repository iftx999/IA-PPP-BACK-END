-- Infraestrutura sem dado de negócio (sem RLS): trava de tarefas agendadas, fila em tabela e limite de requisições.
-- Cada tarefa na fila carrega o tenant e executa dentro do contexto dele.

CREATE TABLE tarefa_agendada (
    nome             text PRIMARY KEY,
    executando_ate   timestamptz,
    ultima_execucao  timestamptz,
    ultimo_resultado text
);

INSERT INTO tarefa_agendada (nome) VALUES ('avaliar-alertas'), ('verificar-trilha');

-- Consumida com SELECT ... FOR UPDATE SKIP LOCKED.
CREATE TABLE tarefa_em_fila (
    id             uuid PRIMARY KEY,
    tipo           text NOT NULL,
    tenant_id      uuid NOT NULL,
    contexto       jsonb NOT NULL,
    referencia     uuid,
    situacao       text NOT NULL DEFAULT 'PENDENTE' CHECK (situacao IN ('PENDENTE', 'FALHOU')),
    criada_em      timestamptz NOT NULL DEFAULT now(),
    disponivel_em  timestamptz NOT NULL DEFAULT now(),
    executando_ate timestamptz,
    tentativas     integer NOT NULL DEFAULT 0,
    ultimo_erro    text
);

CREATE INDEX tarefa_em_fila_pendentes ON tarefa_em_fila (disponivel_em) WHERE situacao = 'PENDENTE';

CREATE UNLOGGED TABLE limite_requisicao (
    chave    text NOT NULL,
    minuto   bigint NOT NULL,
    contagem integer NOT NULL,
    PRIMARY KEY (chave, minuto)
);

GRANT SELECT, UPDATE ON tarefa_agendada TO aurora_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON tarefa_em_fila, limite_requisicao TO aurora_app;
