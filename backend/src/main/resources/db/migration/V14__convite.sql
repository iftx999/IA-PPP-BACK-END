-- Convite de usuário (H8): a pessoa é criada no provedor de identidade ao ser convidada, já com o sub
-- vinculado. Enquanto não houver primeiro acesso, o convite está pendente.
ALTER TABLE usuario ADD COLUMN convidado_em timestamptz;
