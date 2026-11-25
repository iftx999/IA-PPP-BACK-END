-- ADR 0041: atendimento ao titular (LGPD, art. 18, I e II). Permissão própria para gerar o relatório do que a
-- Aurora guarda de uma matrícula; dada ao perfil de administrador do sistema (os clientes repassam a quem atende o
-- encarregado). Índice para achar as OS de uma matrícula sem varrer a tabela.
INSERT INTO permissao (codigo, descricao) VALUES
    ('titular:atender', 'Atender pedidos de titulares (LGPD): relatório do que a Aurora guarda de uma matrícula');

ALTER TABLE perfil_permissao NO FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil NO FORCE ROW LEVEL SECURITY;
INSERT INTO perfil_permissao (tenant_id, perfil_id, permissao)
SELECT p.tenant_id, p.id, 'titular:atender'
  FROM perfil p
 WHERE p.sistema AND p.nome = 'Administrador do tenant'
ON CONFLICT DO NOTHING;
ALTER TABLE perfil FORCE ROW LEVEL SECURITY;
ALTER TABLE perfil_permissao FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_os_matricula ON ordem_servico (tenant_id, codigo_origem);
