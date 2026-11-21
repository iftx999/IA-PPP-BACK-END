-- ADR 0052: botão "Estou chegando" do app. O telefone do imóvel vem da concessionária (CSV matricula;telefone) e fica
-- cifrado (AES-GCM, chave AURORA_CHAVE_CONTATOS, fora do banco); só os 4 últimos dígitos ficam em claro para conferência.
-- O número nunca vai para o app nem para a web: o servidor decifra só para mandar o SMS. Um aviso por OS.

CREATE TABLE imovel_contato (
    tenant_id        uuid NOT NULL,
    codigo_origem    text NOT NULL,
    telefone_cifrado text NOT NULL CHECK (char_length(telefone_cifrado) <= 200),
    final_telefone   text NOT NULL CHECK (final_telefone ~ '^[0-9]{4}$'),
    atualizado_em    timestamptz NOT NULL DEFAULT now(),
    atualizado_por   uuid,
    PRIMARY KEY (tenant_id, codigo_origem),
    -- Só imóvel com endereço importado (a mesma área de acesso restrito do vínculo matrícula → número).
    FOREIGN KEY (tenant_id, codigo_origem)  REFERENCES imovel_endereco (tenant_id, codigo_origem) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, atualizado_por) REFERENCES usuario (tenant_id, id)
);

CREATE TABLE aviso_de_chegada (
    -- Gerado no celular: o mesmo toque reenviado é reconhecido.
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenant (id),
    os_id          uuid NOT NULL,
    equipe_id      uuid NOT NULL,
    usuario_id     uuid NOT NULL,
    dispositivo_id uuid NOT NULL,
    minutos        integer NOT NULL CHECK (minutos BETWEEN 1 AND 120),
    final_telefone text NOT NULL CHECK (final_telefone ~ '^[0-9]{4}$'),
    -- ENVIADO pelo provedor de SMS; REGISTRADO quando não há provedor configurado (só fica a trilha); FALHOU.
    situacao       text NOT NULL CHECK (situacao IN ('ENVIADO', 'REGISTRADO', 'FALHOU')),
    provedor       text NOT NULL CHECK (char_length(provedor) <= 40),
    erro           text CHECK (char_length(erro) <= 300),
    ocorrido_em    timestamptz NOT NULL,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, os_id)          REFERENCES ordem_servico (tenant_id, id),
    FOREIGN KEY (tenant_id, equipe_id)      REFERENCES equipe (tenant_id, id),
    FOREIGN KEY (tenant_id, usuario_id)     REFERENCES usuario (tenant_id, id),
    FOREIGN KEY (tenant_id, dispositivo_id) REFERENCES dispositivo (tenant_id, id)
);

-- Um aviso que saiu (ou ficou registrado) por OS; o que falhou pode ser tentado de novo.
CREATE UNIQUE INDEX uq_aviso_de_chegada_por_os ON aviso_de_chegada (tenant_id, os_id) WHERE situacao <> 'FALHOU';

ALTER TABLE imovel_contato ENABLE ROW LEVEL SECURITY;
ALTER TABLE imovel_contato FORCE ROW LEVEL SECURITY;
CREATE POLICY imovel_contato_pelo_endereco ON imovel_contato USING (
    tenant_id = app_tenant() AND codigo_origem IN (SELECT codigo_origem FROM imovel_endereco));

ALTER TABLE aviso_de_chegada ENABLE ROW LEVEL SECURITY;
ALTER TABLE aviso_de_chegada FORCE ROW LEVEL SECURITY;
CREATE POLICY aviso_de_chegada_pela_os ON aviso_de_chegada USING (
    tenant_id = app_tenant() AND os_id IN (SELECT id FROM ordem_servico));

GRANT SELECT, INSERT, UPDATE, DELETE ON imovel_contato TO aurora_app;
GRANT SELECT, INSERT ON aviso_de_chegada TO aurora_app;
