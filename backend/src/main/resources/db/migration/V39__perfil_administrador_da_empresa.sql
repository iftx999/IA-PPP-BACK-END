-- "Tenant" é termo técnico: o perfil do sistema passa a se chamar "Administrador da empresa". Só muda o nome; as
-- permissões e as concessões ficam. Cliente que já tenha um perfil próprio com o nome novo fica como está.
ALTER TABLE perfil NO FORCE ROW LEVEL SECURITY;

UPDATE perfil p
   SET nome = 'Administrador da empresa'
 WHERE p.sistema AND p.nome = 'Administrador do tenant'
   AND NOT EXISTS (SELECT 1 FROM perfil x WHERE x.tenant_id = p.tenant_id AND x.nome = 'Administrador da empresa');

ALTER TABLE perfil FORCE ROW LEVEL SECURITY;
