package dev.aurora.auditoria;

/**
 * Tipos de evento da trilha de auditoria (doc 08, 8.7). Os nomes seguem os já gravados
 * pela versão anterior.
 */
public enum TipoEvento {

	LOGIN, ACESSO_CONCEDIDO, USUARIO_CONVIDADO, CONVITE_ACEITO,

	/** Exibição de dado pessoal sem máscara (ex.: matrícula completa). */
	ACESSO_DADO_PESSOAL,

	/**
	 * Emissão de relatório ou exportação (CSV/PDF), com filtros e quantidade de linhas
	 * (RN-12).
	 */
	RELATORIO_EXPORTADO,

	META_DEFINIDA, META_ENCERRADA, META_REMOVIDA, UNIDADE_CRIADA, FONTE_CRIADA, CARGA_CONCLUIDA,

	PERFIL_CRIADO, PERFIL_ALTERADO, PERFIL_REMOVIDO,

	DOCUMENTO_CONTRATO_ENVIADO, ITEM_CONTRATUAL_APROVADO, ITEM_CONTRATUAL_REJEITADO, MARCACAO_CRIADA

}
