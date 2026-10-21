package dev.aurora.auditoria;

import java.util.Map;

/**
 * Registro na trilha de auditoria do tenant atual (somente inserção, encadeada por HMAC).
 * Participa da transação de quem chama: a ação e o seu registro são gravados juntos ou
 * não são gravados.
 */
public interface Auditoria {

	/**
	 * @param recurso o que foi afetado, no formato "tipo/identificador" (ex.:
	 * "imoveis/sem-ligacao")
	 * @param detalhes parâmetros do evento (filtros, quantidade de linhas); nunca dados
	 * pessoais
	 */
	void registrar(TipoEvento tipo, String recurso, Map<String, ?> detalhes);

}
