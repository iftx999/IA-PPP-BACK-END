package dev.aurora.mapa.service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.aurora.comum.RelatorioPdf;
import dev.aurora.mapa.model.ImovelNoMapa;
import dev.aurora.mapa.model.Marcacao;
import dev.aurora.mapa.model.PedidoExportacao;
import dev.aurora.mapa.model.SituacaoNoMapa;

/**
 * PDF da área do mapa "para imprimir ou anexar a uma ordem de serviço" (E5): imagem do
 * mapa como a pessoa o via, resumo por situação e as listas de imóveis e marcações.
 */
class PdfDaArea {

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR);

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR);

	private final Clock relogio;

	PdfDaArea(Clock relogio) {
		this.relogio = relogio;
	}

	void escrever(PedidoExportacao pedido, Map<SituacaoNoMapa, Long> porSituacao, List<ImovelNoMapa> imoveis,
			List<Marcacao> marcacoes, OutputStream saida) throws IOException {
		try (var pdf = new RelatorioPdf("Aurora - Área do mapa")) {
			pdf.titulo("Área do mapa");
			var subtitulo = new StringBuilder();
			if (pedido.imoveis() != null) {
				subtitulo.append("Dados de ").append(MES.format(pedido.imoveis().competencia())).append(" · ");
			}
			subtitulo.append("Emitido em ")
				.append(DATA_HORA.format(ZonedDateTime.now(relogio)))
				.append(" · Área ")
				.append(coordenadas(pedido));
			pdf.linha(subtitulo.toString(), false, 9).espaco(6);
			if (pedido.imagemPng() != null) {
				pdf.imagem(pedido.imagemPng(), 330);
			}

			var resumo = new StringBuilder();
			if (pedido.imoveis() != null) {
				resumo.append(imoveis.size()).append(imoveis.size() == 1 ? " imóvel" : " imóveis");
				porSituacao.forEach((situacao,
						quantidade) -> resumo.append(" · ").append(situacao.rotulo()).append(": ").append(quantidade));
			}
			if (pedido.marcacoes()) {
				resumo.append(resumo.isEmpty() ? "" : " · ")
					.append(marcacoes.size())
					.append(marcacoes.size() == 1 ? " marcação" : " marcações");
			}
			pdf.linha(resumo.toString(), true, 10);

			if (pedido.imoveis() != null) {
				pdf.subtitulo("Imóveis")
					.tabela(new float[] { 110, 140, 90, 230, 80, 80 },
							List.of("Matrícula", "Situação", "Localização", "Unidade", "Latitude", "Longitude"),
							imoveis.stream()
								.map(i -> List.of(i.matricula(), i.situacao().rotulo(),
										i.precisao().aproximada() ? "Aproximada" : "Exata", i.unidadeNome(),
										coordenada(i.latitude()), coordenada(i.longitude())))
								.toList());
			}
			if (pedido.marcacoes()) {
				pdf.subtitulo("Marcações")
					.tabela(new float[] { 150, 250, 120, 70, 90, 90 },
							List.of("Título", "Observação", "Autor", "Data", "Latitude", "Longitude"),
							marcacoes.stream()
								.map(m -> List.of(m.titulo(), m.observacao() == null ? "" : m.observacao(),
										m.autorNome(), DATA.format(m.criadaEm().atZone(relogio.getZone())),
										coordenada(m.latitude()), coordenada(m.longitude())))
								.toList());
			}
			pdf.salvar(saida);
		}
	}

	private static String coordenadas(PedidoExportacao pedido) {
		var a = pedido.area();
		return String.format(PT_BR, "%.5f; %.5f a %.5f; %.5f", a.sul(), a.oeste(), a.norte(), a.leste());
	}

	private static String coordenada(double valor) {
		return String.format(Locale.ROOT, "%.6f", valor);
	}

}
