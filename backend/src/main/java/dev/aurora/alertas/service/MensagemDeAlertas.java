package dev.aurora.alertas.service;

import java.util.List;

import dev.aurora.alertas.model.Alerta;
import dev.aurora.alertas.model.Severidade;

import org.springframework.web.util.HtmlUtils;

/**
 * Texto do e-mail com os alertas novos de uma pessoa (ADR 0024). Só o que já aparece na
 * tela de alertas (unidade, percentuais, prazos); nenhum dado pessoal.
 */
final class MensagemDeAlertas {

	record Mensagem(String assunto, String texto, String html) {
	}

	private MensagemDeAlertas() {
	}

	static Mensagem montar(String nome, List<Alerta> alertas, String enderecoFront) {
		var base = enderecoFront.endsWith("/") ? enderecoFront.substring(0, enderecoFront.length() - 1) : enderecoFront;
		var criticos = alertas.stream().filter(a -> a.severidade() == Severidade.CRITICO).count();
		var assunto = "[Aurora] " + (alertas.size() == 1 ? "1 alerta novo" : alertas.size() + " alertas novos")
				+ (criticos == 0 ? "" : criticos == 1 ? " (1 crítico)" : " (" + criticos + " críticos)");

		var texto = new StringBuilder("Olá, ").append(nome).append(".\n\n");
		texto.append(alertas.size() == 1 ? "Há um alerta novo na Aurora:" : "Há alertas novos na Aurora:")
			.append("\n\n");
		var html = new StringBuilder("""
				<div style="font-family:Arial,Helvetica,sans-serif;color:#0f1d24;max-width:640px">
				<p>Olá, %s.</p><p>%s</p>""".formatted(HtmlUtils.htmlEscape(nome),
				alertas.size() == 1 ? "Há um alerta novo na Aurora:" : "Há alertas novos na Aurora:"));
		for (var a : alertas) {
			var critico = a.severidade() == Severidade.CRITICO;
			var rotulo = critico ? "CRÍTICO" : "ATENÇÃO";
			var link = base + (a.link() == null ? "/alertas" : a.link());
			texto.append("[").append(rotulo).append("] ").append(a.titulo()).append('\n');
			texto.append("  ").append(a.descricao()).append('\n');
			texto.append("  Ver: ").append(link).append("\n\n");
			html.append("""
					<div style="border-left:4px solid %s;padding:8px 12px;margin:12px 0;background:#f4f7f8">
					<div style="font-size:12px;font-weight:bold;color:%s">%s</div>
					<div style="font-weight:bold;margin:4px 0">%s</div>
					<div>%s</div>
					<div style="margin-top:6px"><a href="%s">Ver na Aurora</a></div>
					</div>""".formatted(critico ? "#d64545" : "#e0a526", critico ? "#b42323" : "#8a6100", rotulo,
					HtmlUtils.htmlEscape(a.titulo()), HtmlUtils.htmlEscape(a.descricao()), HtmlUtils.htmlEscape(link)));
		}
		var rodape = "Todos os alertas: " + base + "/alertas\n"
				+ "Você recebe este e-mail conforme sua preferência em Alertas > Preferências de e-mail.";
		texto.append(rodape).append('\n');
		html.append("""
				<p style="font-size:12px;color:#5b6b73">Todos os alertas: <a href="%s/alertas">%s/alertas</a><br>
				Você recebe este e-mail conforme sua preferência em Alertas &gt; Preferências de e-mail.</p></div>"""
			.formatted(HtmlUtils.htmlEscape(base), HtmlUtils.htmlEscape(base)));
		return new Mensagem(assunto, texto.toString(), html.toString());
	}

}
