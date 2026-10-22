package dev.aurora.contratos.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import dev.aurora.contratos.model.DadosItem;
import dev.aurora.contratos.model.Pagina;
import dev.aurora.contratos.model.Proposta;
import dev.aurora.contratos.model.TipoItem;
import org.jspecify.annotations.Nullable;

/**
 * Salvaguardas contra erro da IA (doc 05, 5.3): só passa item com tipo conhecido, página
 * existente e trecho literal presente no texto do PDF; valores fora de faixa são limpos
 * para a pessoa completar na revisão.
 */
final class ValidadorPropostas {

	private static final BigDecimal CEM = BigDecimal.valueOf(100);

	record Resultado(List<Aprovavel> validos, int descartados) {
	}

	/** Item validado, com a proposta original para guardar junto. */
	record Aprovavel(DadosItem dados, Proposta original, @Nullable BigDecimal confianca) {
	}

	private ValidadorPropostas() {
	}

	/**
	 * @param unidades nome normalizado -> id, das unidades que o contrato cobre
	 */
	static Resultado validar(List<Proposta> propostas, List<Pagina> paginas, Map<String, UUID> unidades) {
		var validos = new ArrayList<Aprovavel>();
		int descartados = 0;
		for (var p : propostas) {
			var tipo = tipo(p.tipo());
			if (tipo == null || p.pagina() < 1 || p.pagina() > paginas.size() || vazio(p.trecho()) || vazio(p.titulo())
					|| !trechoNoTexto(p.trecho(), p.pagina(), paginas)) {
				descartados++;
				continue;
			}
			var valor = p.valorReferencia();
			var alerta = p.valorAlerta();
			if (tipo == TipoItem.META) {
				valor = percentual(valor);
				alerta = percentual(alerta);
				if (valor != null && alerta != null && alerta.compareTo(valor) > 0) {
					alerta = null;
				}
			}
			var unidade = p.unidadeAlvo() == null ? null : unidades.get(normalizar(p.unidadeAlvo()));
			var dados = new DadosItem(tipo, limitar(p.titulo(), 200),
					limitar(vazio(p.resumo()) ? p.titulo() : p.resumo(), 2000),
					vazio(p.clausula()) ? null : limitar(p.clausula().strip(), 40), p.pagina(), p.trecho().strip(),
					valor, p.unidadeMedida(), p.dataLimite(), p.periodicidade(), unidade, alerta, p.vigenciaInicio());
			validos.add(new Aprovavel(dados, p, confianca(p.confianca())));
		}
		return new Resultado(validos, descartados);
	}

	/** O trecho precisa estar na página citada ou continuar na seguinte. */
	static boolean trechoNoTexto(String trecho, int pagina, List<Pagina> paginas) {
		var procurado = normalizar(trecho);
		if (procurado.length() < 10) {
			return false;
		}
		var texto = normalizar(paginas.get(pagina - 1).texto());
		if (pagina < paginas.size()) {
			texto = texto + " " + normalizar(paginas.get(pagina).texto());
		}
		return texto.contains(procurado);
	}

	/** Sem acentos, minúsculas e espaços colapsados (quebras de linha do PDF variam). */
	static String normalizar(String texto) {
		var semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return semAcento.toLowerCase(Locale.ROOT).replaceAll("[\\s\\u00a0]+", " ").strip();
	}

	private static @Nullable TipoItem tipo(String tipo) {
		try {
			return TipoItem.valueOf(tipo.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException | NullPointerException erro) {
			return null;
		}
	}

	private static @Nullable BigDecimal percentual(@Nullable BigDecimal valor) {
		return valor == null || valor.signum() < 0 || valor.compareTo(CEM) > 0 ? null : valor;
	}

	private static @Nullable BigDecimal confianca(@Nullable BigDecimal valor) {
		if (valor == null) {
			return null;
		}
		return valor.max(BigDecimal.ZERO).min(BigDecimal.ONE);
	}

	private static boolean vazio(@Nullable String texto) {
		return texto == null || texto.isBlank();
	}

	private static String limitar(String texto, int maximo) {
		var limpo = texto.strip();
		return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo);
	}

}
