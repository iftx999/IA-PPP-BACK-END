package dev.aurora.auditoria;

/**
 * Tipos de evento da trilha de auditoria (doc 08, 8.7). Os nomes seguem os já gravados
 * pela versão anterior.
 */
public enum TipoEvento {

	LOGIN, ACESSO_CONCEDIDO, USUARIO_CONVIDADO, CONVITE_ACEITO,

	/**
	 * Acesso (doc 08, 8.6 e 8.7; ADR 0028): vindos do provedor de identidade, e o
	 * encerramento de todas as sessões de alguém.
	 */
	LOGIN_FALHOU, CONTA_BLOQUEADA, LOGOUT, SESSOES_ENCERRADAS,

	/**
	 * Gestão de usuários (H8, RN-16): concessões perfil × unidade, ciclo de vida e senha.
	 */
	ACESSO_ALTERADO, CONVITE_REENVIADO, USUARIO_DESATIVADO, USUARIO_REATIVADO, REDEFINICAO_SENHA_ENVIADA,

	/**
	 * Verificação em duas etapas (H9): política do tenant e remoção do TOTP de alguém.
	 */
	POLITICA_SEGUNDO_FATOR_ALTERADA, SEGUNDO_FATOR_REMOVIDO,

	/**
	 * Administração da plataforma (S1, ADR 0027): cliente criado ou alterado (na trilha
	 * do cliente).
	 */
	CLIENTE_CRIADO, CLIENTE_ALTERADO,

	/** SSO corporativo (S10): IdP configurado, alterado ou removido. */
	SSO_CONFIGURADO, SSO_REMOVIDO,

	/**
	 * Retenção da trilha (ADR 0029): prazo alterado e expurgo do que passou do prazo.
	 */
	RETENCAO_AUDITORIA_ALTERADA, TRILHA_EXPURGADA,

	/** ADR 0033: arquivo enviado e recusado pelo antivírus (nome da ameaça e SHA-256). */
	ARQUIVO_RECUSADO_PELO_ANTIVIRUS,

	/** Exibição de dado pessoal sem máscara (ex.: matrícula completa). */
	ACESSO_DADO_PESSOAL,

	/**
	 * Emissão de relatório ou exportação (CSV/PDF), com filtros e quantidade de linhas
	 * (RN-12).
	 */
	RELATORIO_EXPORTADO,

	META_DEFINIDA, META_ENCERRADA, META_REMOVIDA,

	/**
	 * A5: meta substituída por inteiro por um aditivo (a versão anterior fica no
	 * histórico).
	 */
	META_SUBSTITUIDA,

	/** Integrações (H3). */
	FONTE_CRIADA, FONTE_ALTERADA, CREDENCIAL_GERADA, CREDENCIAL_REVOGADA, CARGA_CONCLUIDA,

	/** Alertas (F3). */
	ALERTA_RECONHECIDO,

	/** Cadastro de unidades (H1). */
	UNIDADE_CRIADA, UNIDADE_ALTERADA, UNIDADE_MOVIDA, UNIDADE_INATIVADA, UNIDADE_REATIVADA,

	PERFIL_CRIADO, PERFIL_ALTERADO, PERFIL_REMOVIDO,

	/** Contratos (épico A). */
	CONTRATO_CRIADO, DOCUMENTO_CONTRATO_ENVIADO, ITEM_CONTRATUAL_APROVADO, ITEM_CONTRATUAL_REJEITADO,

	/**
	 * ADR 0024, ADR 0026: agenda de vencimentos definida; vencimento cumprido ou
	 * reaberto.
	 */
	AGENDA_DE_PRAZO_DEFINIDA, PRAZO_CONTRATUAL_CUMPRIDO, PRAZO_CONTRATUAL_REABERTO,

	/** ADR 0035: regra calculável de uma penalidade definida ou removida. */
	REGRA_DE_PENALIDADE_DEFINIDA, REGRA_DE_PENALIDADE_REMOVIDA,

	/** ADR 0036: equipes de campo, campanhas de ligação e ordens de serviço. */
	EQUIPE_CADASTRADA, EQUIPE_ALTERADA, CAMPANHA_CRIADA, CAMPANHA_ENCERRADA, CAMPANHA_CANCELADA, ORDENS_DISTRIBUIDAS,
	RESULTADO_DE_ORDEM_REGISTRADO,

	/**
	 * ADR 0042: catálogo de serviços (serviço cadastrado ou alterado, com ou sem nova
	 * versão; grupo, veículo, habilidade ou motivo cadastrado ou alterado).
	 */
	SERVICO_CADASTRADO, SERVICO_ALTERADO, ITEM_DO_CATALOGO_CADASTRADO, ITEM_DO_CATALOGO_ALTERADO,

	/**
	 * ADR 0043: importação de trechos e de endereços; correção manual de logradouro e de
	 * trecho.
	 */
	LOGRADOUROS_IMPORTADOS, ENDERECOS_IMPORTADOS, LOGRADOURO_CORRIGIDO, TRECHO_CORRIGIDO,

	/**
	 * ADR 0044: OS avulsas, de trecho e lotes criados; OS programadas ou reprogramadas,
	 * devolvidas e canceladas; feriados cadastrados e removidos.
	 */
	ORDENS_CRIADAS, ORDENS_PROGRAMADAS, ORDEM_DEVOLVIDA, ORDEM_CANCELADA, FERIADO_CADASTRADO, FERIADO_REMOVIDO,

	/**
	 * ADR 0045: celular cadastrado e revogado; conflito de campo aberto e decidido; foto
	 * recusada (antivírus ou conteúdo); fotos de OS vistas na web; posições expurgadas.
	 */
	DISPOSITIVO_CADASTRADO, DISPOSITIVO_REVOGADO, CONFLITO_DE_CAMPO_ABERTO, CONFLITO_DE_CAMPO_DECIDIDO, ANEXO_RECUSADO,
	ANEXO_DE_ORDEM_LIDO,

	/**
	 * ADR 0046: roteirização pedida (prévia, recálculo ou reotimização), publicada como
	 * programação ou descartada.
	 */
	ROTEIRIZACAO_PEDIDA, ROTEIRIZACAO_PUBLICADA, ROTEIRIZACAO_DESCARTADA,

	/**
	 * ADR 0049: sugestão automática de reotimização aceita ou ignorada pelo programador.
	 */
	REOTIMIZACAO_SUGERIDA_ACEITA, REOTIMIZACAO_SUGERIDA_IGNORADA,

	/** ADR 0050: o agente confirmou no app a nova ordem das OS. */
	ROTA_CONFIRMADA_NO_CAMPO,

	/** ADR 0021: páginas digitalizadas lidas por OCR (nome herdado). */
	TEXTO_RECONHECIDO_POR_OCR,

	/** Mapa (épico E): marcações de visita e localizações enviadas pelo agente. */
	MARCACAO_CRIADA, MARCACAO_REMOVIDA, IMOVEIS_GEOCODIFICADOS,

	/**
	 * Assistente (épico G): só identificadores, tokens e custo; nunca o texto da
	 * pergunta.
	 */
	PERGUNTA_IA, RESPOSTA_IA_AVALIADA

}
