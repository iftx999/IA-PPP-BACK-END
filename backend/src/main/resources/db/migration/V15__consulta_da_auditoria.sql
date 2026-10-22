-- Consulta da trilha (S8): filtros por pessoa e por tipo de evento, sempre do mais recente para o mais antigo.
CREATE INDEX idx_evento_tenant_usuario_seq ON evento_auditoria (tenant_id, usuario_id, sequencia DESC);
CREATE INDEX idx_evento_tenant_tipo_seq ON evento_auditoria (tenant_id, tipo, sequencia DESC);
