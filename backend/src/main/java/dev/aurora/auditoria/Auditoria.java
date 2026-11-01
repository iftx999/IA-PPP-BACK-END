package dev.aurora.auditoria;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

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

	/**
	 * Evento que aconteceu fora de uma requisição da API (ex.: login no provedor de
	 * identidade, ADR 0028): autor, momento e IP vêm da origem, não do contexto atual. O
	 * tenant continua sendo o do contexto.
	 * @param usuarioId autor, se identificado (falha de login com e-mail desconhecido:
	 * null)
	 */
	void registrarOcorrido(TipoEvento tipo, @Nullable UUID usuarioId, Instant ocorridoEm, @Nullable String ip,
			String recurso, Map<String, ?> detalhes);

}
