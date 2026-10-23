package dev.aurora.mapa.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.EscritorCsv;
import dev.aurora.comum.Matricula;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.mapa.model.ArquivoExportado;
import dev.aurora.mapa.model.ImovelNoMapa;
import dev.aurora.mapa.model.Marcacao;
import dev.aurora.mapa.model.PedidoExportacao;
import dev.aurora.mapa.model.SituacaoNoMapa;
import dev.aurora.mapa.repository.ImovelMapaRepository;
import dev.aurora.mapa.repository.MarcacaoRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E5 e RN-12: exporta só o que está visível (área atual e camadas ligadas). Matrícula
 * completa só nas unidades onde vale {@code dados_pessoais:ler}. A emissão vai para a
 * auditoria com área, camadas, linhas e SHA-256 do arquivo.
 */
@Service
public class ExportacaoMapaService {

	/** Acima disso a pessoa aproxima o mapa (o PDF viraria um catálogo). */
	static final long MAXIMO_DE_IMOVEIS = 50_000;

	private final ImovelMapaRepository imoveis;

	private final MarcacaoRepository marcacoes;

	private final Auditoria auditoria;

	private final Clock relogio;

	ExportacaoMapaService(ImovelMapaRepository imoveis, MarcacaoRepository marcacoes, Auditoria auditoria,
			Clock relogio) {
		this.imoveis = imoveis;
		this.marcacoes = marcacoes;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	@Transactional
	public ArquivoExportado exportar(PedidoExportacao pedido, OutputStream destino) {
		if (pedido.imoveis() == null && !pedido.marcacoes()) {
			throw new RegraVioladaException("Ligue a camada de imóveis ou a de marcações para exportar.");
		}
		var filtro = pedido.imoveis();
		if (filtro != null) {
			var total = imoveis.contar(pedido.area(), filtro);
			if (total > MAXIMO_DE_IMOVEIS) {
				throw new RegraVioladaException("A área tem %d imóveis; aproxime o mapa para exportar até %d."
					.formatted(total, MAXIMO_DE_IMOVEIS));
			}
		}

		var contexto = ContextoAcesso.exigir();
		var completas = new long[1];
		Consumer<Consumer<ImovelNoMapa>> percorrerImoveis = consumidor -> {
			if (filtro != null) {
				imoveis.percorrer(pedido.area(), filtro, imovel -> {
					if (contexto.podeEm(MapaService.PERMISSAO_DADOS_PESSOAIS, imovel.caminhoUnidade())) {
						completas[0]++;
						consumidor.accept(imovel);
					}
					else {
						consumidor.accept(imovel.comMatricula(Matricula.mascarar(imovel.matricula())));
					}
				});
			}
		};

		var resumo = sha256();
		var saida = new DigestOutputStream(destino, resumo);
		var contagem = switch (pedido.formato()) {
			case CSV -> csv(pedido, percorrerImoveis, saida);
			case PDF -> pdf(pedido, percorrerImoveis, saida);
		};
		var resultado = new ArquivoExportado(contagem[0], contagem[1], HexFormat.of().formatHex(resumo.digest()));
		auditar(pedido, resultado, completas[0]);
		return resultado;
	}

	private long[] csv(PedidoExportacao pedido, Consumer<Consumer<ImovelNoMapa>> percorrerImoveis, OutputStream saida) {
		var contagem = new long[2];
		try (var csv = new EscritorCsv(saida)) {
			csv.linha("tipo", "matricula", "situacao", "localizacao", "unidade", "latitude", "longitude", "titulo",
					"observacao", "autor", "data");
			percorrerImoveis.accept(i -> {
				csv.linha("Imóvel", i.matricula(), i.situacao().rotulo(),
						i.precisao().aproximada() ? "Aproximada" : "Exata", i.unidadeNome(), coordenada(i.latitude()),
						coordenada(i.longitude()), null, null, null, null);
				contagem[0]++;
			});
			if (pedido.marcacoes()) {
				marcacoes.percorrer(pedido.area(), m -> {
					csv.linha("Marcação", null, null, "Exata", m.unidadeNome(), coordenada(m.latitude()),
							coordenada(m.longitude()), m.titulo(), m.observacao(), m.autorNome(), m.criadaEm());
					contagem[1]++;
				});
			}
		}
		return contagem;
	}

	private long[] pdf(PedidoExportacao pedido, Consumer<Consumer<ImovelNoMapa>> percorrerImoveis, OutputStream saida) {
		var contagem = new long[2];
		var porSituacao = new LinkedHashMap<SituacaoNoMapa, Long>();
		var lista = new ArrayList<ImovelNoMapa>();
		percorrerImoveis.accept(i -> {
			lista.add(i);
			porSituacao.merge(i.situacao(), 1L, Long::sum);
		});
		List<Marcacao> visitas = new ArrayList<>();
		if (pedido.marcacoes()) {
			marcacoes.percorrer(pedido.area(), visitas::add);
		}
		contagem[0] = lista.size();
		contagem[1] = visitas.size();
		try {
			new PdfDaArea(relogio).escrever(pedido, porSituacao, lista, visitas, saida);
		}
		catch (IOException erro) {
			throw new UncheckedIOException(erro);
		}
		return contagem;
	}

	private void auditar(PedidoExportacao pedido, ArquivoExportado resultado, long completas) {
		var area = pedido.area();
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("formato", pedido.formato().name());
		detalhes.put("area", List.of(area.oeste(), area.sul(), area.leste(), area.norte()));
		var filtro = pedido.imoveis();
		if (filtro != null) {
			detalhes.put("competencia", filtro.competencia().toString());
			detalhes.put("situacoes", List.of(filtro.situacoesNoBanco()));
			detalhes.put("aproximadas", filtro.aproximadas());
		}
		detalhes.put("camadas", camadas(pedido));
		detalhes.put("linhas", resultado.imoveis() + resultado.marcacoes());
		detalhes.put("imoveis", resultado.imoveis());
		detalhes.put("marcacoes", resultado.marcacoes());
		detalhes.put("matriculasCompletas", completas);
		detalhes.put("sha256", resultado.sha256());
		auditoria.registrar(TipoEvento.RELATORIO_EXPORTADO, "mapa/area", detalhes);
	}

	private static List<String> camadas(PedidoExportacao pedido) {
		var camadas = new ArrayList<String>();
		if (pedido.imoveis() != null) {
			camadas.add("imoveis");
		}
		if (pedido.marcacoes()) {
			camadas.add("marcacoes");
		}
		return camadas;
	}

	/**
	 * Número (não texto): a proteção contra fórmulas do CSV não prefixa o sinal negativo.
	 */
	private static BigDecimal coordenada(double valor) {
		return BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP);
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException erro) {
			throw new IllegalStateException(erro);
		}
	}

}
