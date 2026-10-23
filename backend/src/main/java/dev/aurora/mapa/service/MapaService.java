package dev.aurora.mapa.service;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.adesao.AdesaoDaUnidade;
import dev.aurora.adesao.Medicoes;
import dev.aurora.adesao.Situacao;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.Matricula;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.mapa.model.CamadaMunicipio;
import dev.aurora.mapa.model.Camadas;
import dev.aurora.mapa.model.FiltroImoveis;
import dev.aurora.mapa.model.ImovelNoMapa;
import dev.aurora.mapa.repository.CamadasRepository;
import dev.aurora.mapa.repository.ImovelMapaRepository;
import dev.aurora.mapa.repository.TileRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura do mapa (E1, E2). Tudo vem do escopo da requisição (RLS e
 * {@code app_escopos()}); as matrículas só aparecem completas onde a pessoa tem
 * {@code dados_pessoais:ler}, e essa exibição é auditada (LGPD).
 */
@Service
public class MapaService {

	public static final String PERMISSAO_DADOS_PESSOAIS = "dados_pessoais:ler";

	static final int IMOVEIS_NO_PONTO = 10;

	private final CamadasRepository camadas;

	private final TileRepository tiles;

	private final ImovelMapaRepository imoveis;

	private final Medicoes medicoes;

	private final Auditoria auditoria;

	MapaService(CamadasRepository camadas, TileRepository tiles, ImovelMapaRepository imoveis, Medicoes medicoes,
			Auditoria auditoria) {
		this.camadas = camadas;
		this.tiles = tiles;
		this.imoveis = imoveis;
		this.medicoes = medicoes;
		this.auditoria = auditoria;
	}

	/**
	 * Municípios coloridos pela adesão da competência (a mais recente, se não informada),
	 * setores e estações, com o retângulo para enquadrar o escopo.
	 */
	@Transactional(readOnly = true)
	public Camadas camadas(Optional<YearMonth> pedida) {
		var competencias = camadas.listarCompetencias();
		var competencia = pedida.orElse(competencias.isEmpty() ? null : competencias.getFirst());
		var malhas = camadas.listarMunicipios();
		var adesao = new HashMap<UUID, AdesaoDaUnidade>();
		if (competencia != null) {
			medicoes.situacaoDas(malhas.stream().map(CamadasRepository.Malha::unidadeId).toList(), competencia)
				.forEach(a -> adesao.put(a.unidadeId(), a));
		}
		var municipios = malhas.stream().map(m -> {
			var a = adesao.get(m.unidadeId());
			return new CamadaMunicipio(m.unidadeId(), m.nome(), m.codigoIbge(), a == null ? null : a.percentual(),
					a == null ? Situacao.SEM_META : a.situacao(), m.geometria());
		}).toList();
		var enquadramento = camadas.enquadrar(malhas.stream().map(CamadasRepository.Malha::codigoIbge).toList())
			.orElse(null);
		return new Camadas(competencia, competencias, municipios, camadas.listarSetores(), camadas.listarOperacionais(),
				enquadramento);
	}

	@Transactional(readOnly = true)
	public byte[] tile(int z, int x, int y, FiltroImoveis filtro, boolean rede) {
		int lado = 1 << z;
		if (x < 0 || y < 0 || x >= lado || y >= lado) {
			throw new RegraVioladaException("Tile fora do zoom " + z + ".");
		}
		return tiles.gerar(z, x, y, filtro, rede);
	}

	/**
	 * Imóveis perto do clique (até {@value #IMOVEIS_NO_PONTO}), com a matrícula completa
	 * só onde a permissão vale.
	 */
	@Transactional
	public List<ImovelNoMapa> imoveisPerto(double longitude, double latitude, int raio, FiltroImoveis filtro) {
		var encontrados = imoveis.buscarPerto(longitude, latitude, raio, filtro, IMOVEIS_NO_PONTO);
		var contexto = ContextoAcesso.exigir();
		var completas = new int[1];
		var resultado = encontrados.stream().map(imovel -> {
			if (contexto.podeEm(PERMISSAO_DADOS_PESSOAIS, imovel.caminhoUnidade())) {
				completas[0]++;
				return imovel;
			}
			return imovel.comMatricula(Matricula.mascarar(imovel.matricula()));
		}).toList();
		if (completas[0] > 0) {
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("longitude", longitude);
			detalhes.put("latitude", latitude);
			detalhes.put("competencia", filtro.competencia().toString());
			detalhes.put("quantidade", completas[0]);
			detalhes.put("motivo", "Imóveis no ponto clicado do mapa");
			auditoria.registrar(TipoEvento.ACESSO_DADO_PESSOAL, "mapa/imoveis", detalhes);
		}
		return resultado;
	}

	/**
	 * Competência pedida ou a mais recente com dados; vazio em cliente ainda sem carga.
	 */
	@Transactional(readOnly = true)
	public Optional<YearMonth> competenciaSeHouver(Optional<YearMonth> pedida) {
		return pedida.or(() -> camadas.listarCompetencias().stream().findFirst());
	}

	/** Competência pedida ou a mais recente com dados. */
	@Transactional(readOnly = true)
	public YearMonth competencia(Optional<YearMonth> pedida) {
		return pedida.or(() -> camadas.listarCompetencias().stream().findFirst())
			.orElseThrow(() -> new RegraVioladaException("Ainda não há situação de imóveis carregada."));
	}

}
