-- RN-02 (definição do autor, 01/11/2026): cortado = tem conexão física, com o serviço interrompido por falta de
-- pagamento ou suspenso a pedido do cliente. A carga pode informar qual dos dois (opcional; só para CORTADO).
ALTER TABLE imovel_situacao_mensal ADD COLUMN motivo_corte text
    CHECK (motivo_corte IN ('INADIMPLENCIA', 'A_PEDIDO'));
ALTER TABLE imovel_situacao_mensal ADD CONSTRAINT imovel_motivo_so_para_cortado
    CHECK (motivo_corte IS NULL OR situacao = 'CORTADO');

-- Avisos da carga (gravada mesmo assim): ex.: imóvel que já teve conexão física (ligado ou cortado em competência
-- anterior) e veio como factível, o que pela definição não acontece.
ALTER TABLE execucao_sincronizacao ADD COLUMN registros_com_aviso bigint NOT NULL DEFAULT 0;
ALTER TABLE execucao_sincronizacao ADD COLUMN avisos jsonb NOT NULL DEFAULT '[]';
