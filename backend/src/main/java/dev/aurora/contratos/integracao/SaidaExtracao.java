package dev.aurora.contratos.integracao;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Esquema da resposta da IA (saída estruturada). Campos opcionais vêm como texto vazio: o
 * esquema estrito exige todos os campos.
 */
@JsonClassDescription("Itens contratuais encontrados no trecho do contrato")
record SaidaExtracao(@JsonPropertyDescription("Itens encontrados; lista vazia se não houver") List<ItemExtraido> itens,
		@JsonPropertyDescription("Observações curtas sobre problemas de leitura, se houver") List<String> observacoes) {

	record ItemExtraido(
			@JsonPropertyDescription("META, INDICADOR, OBRIGACAO, PRAZO, PENALIDADE, REAJUSTE, RISCO ou OUTRO") String tipo,
			@JsonPropertyDescription("Título curto, até 120 caracteres") String titulo,
			@JsonPropertyDescription("Resumo em uma ou duas frases") String resumo,
			@JsonPropertyDescription("Número da cláusula como aparece no texto (ex.: 7.2); vazio se não houver") String clausula,
			@JsonPropertyDescription("Página indicada no marcador [p. N] do trecho de onde o item foi tirado") int pagina,
			@JsonPropertyDescription("Cópia literal e contínua do texto do contrato que sustenta o item, até 400 caracteres") String trecho,
			@JsonPropertyDescription("Valor numérico principal (para META, o percentual da meta); vazio se não houver") String valorReferencia,
			@JsonPropertyDescription("Unidade do valor (%, meses, anos, R$); vazio se não houver") String unidadeMedida,
			@JsonPropertyDescription("Data limite ou fim da vigência, AAAA-MM-DD; vazio se não houver") String dataLimite,
			@JsonPropertyDescription("Periodicidade (mensal, anual...); vazio se não houver") String periodicidade,
			@JsonPropertyDescription("Para META: nome exato de uma das unidades informadas; vazio se não identificar") String unidadeAlvo,
			@JsonPropertyDescription("Para META: percentual do limite de alerta, se o contrato o definir; vazio se não") String valorAlerta,
			@JsonPropertyDescription("Para META: início da vigência AAAA-MM-DD; vazio se não houver") String vigenciaInicio,
			@JsonPropertyDescription("Confiança de 0 a 1 de que o item está correto") double confianca) {
	}

}
