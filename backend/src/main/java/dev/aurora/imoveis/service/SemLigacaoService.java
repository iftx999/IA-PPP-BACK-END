package dev.aurora.imoveis.service;

import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.CursorCifrado;
import dev.aurora.comum.EscritorCsv;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.imoveis.model.FiltroSemLigacao;
import dev.aurora.comum.Matricula;
import dev.aurora.imoveis.model.PaginaSemLigacao;
import dev.aurora.imoveis.model.ResultadoExportacao;
import dev.aurora.imoveis.model.SituacaoImovel;
import dev.aurora.imoveis.repository.ImovelRepository;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imóveis com rede e sem ligação (épico D). Matrículas completas só com
 * {@code dados_pessoais:ler}, e cada exibição ou exportação completa fica na trilha de
 * auditoria (RN-12, LGPD).
 */
@Service
public class SemLigacaoService {

	public static final String PERMISSAO_DADOS_PESSOAIS = "dados_pessoais:ler";

	static final int LIMITE_PADRAO = 50;

	static final int LIMITE_MAXIMO = 200;

	private static final String RECURSO = "imoveis/sem-ligacao";

	private final ImovelRepository imoveis;

	private final Unidades unidades;

	private final CursorCifrado cursores;

	private final Auditoria auditoria;

	SemLigacaoService(ImovelRepository imoveis, Unidades unidades, CursorCifrado cursores, Auditoria auditoria) {
		this.imoveis = imoveis;
		this.unidades = unidades;
		this.cursores = cursores;
		this.auditoria = auditoria;
	}

	@Transactional
	public PaginaSemLigacao listar(Optional<UUID> unidadeId, Optional<YearMonth> competencia,
			Optional<SituacaoImovel> situacao, Optional<String> matricula, Optional<String> cursor, int limite) {
		var unidade = unidadeId.map(unidades::buscarNoEscopo)
			.orElseGet(unidades::principalDoEscopo)
			.orElseThrow(RecursoNaoEncontradoException::new);
		var filtro = new FiltroSemLigacao(unidade.id(), competencia(competencia), situacao.orElse(null),
				matricula.orElse(null));
		int tamanho = Math.clamp(limite, 1, LIMITE_MAXIMO);
		var depois = cursor.flatMap(cursores::decifrar).orElse(null);

		var itens = imoveis.listar(filtro, depois, tamanho + 1);
		String proximo = null;
		if (itens.size() > tamanho) {
			itens = itens.subList(0, tamanho);
			proximo = cursores.cifrar(itens.getLast().matricula());
		}

		boolean visiveis = podeVerDadosPessoais(unidade.id());
		if (visiveis && !itens.isEmpty()) {
			var detalhes = detalhes(filtro);
			detalhes.put("quantidade", itens.size());
			detalhes.put("motivo", "Lista de imóveis sem ligação");
			auditoria.registrar(TipoEvento.ACESSO_DADO_PESSOAL, RECURSO, detalhes);
		}
		else if (!visiveis) {
			itens = itens.stream().map(i -> i.comMatricula(Matricula.mascarar(i.matricula()))).toList();
		}
		return new PaginaSemLigacao(unidade.id(), unidade.nome(), filtro.competencia(), imoveis.contar(filtro), itens,
				proximo, visiveis);
	}

	/**
	 * D2: CSV com todos os imóveis do filtro; a emissão é auditada com linhas e SHA-256
	 * do arquivo.
	 */
	@Transactional
	public ResultadoExportacao exportarCsv(UUID unidadeId, Optional<YearMonth> competencia,
			Optional<SituacaoImovel> situacao, OutputStream destino) {
		var unidade = unidades.buscarNoEscopo(unidadeId).orElseThrow(RecursoNaoEncontradoException::new);
		var filtro = new FiltroSemLigacao(unidade.id(), competencia(competencia), situacao.orElse(null), null);
		boolean visiveis = podeVerDadosPessoais(unidade.id());

		var resumo = sha256();
		var linhas = new long[1];
		try (var csv = new EscritorCsv(new DigestOutputStream(destino, resumo))) {
			csv.linha("matricula", "unidade", "situacao", "sem_ligacao_ha_meses", "ultimo_mes_ligado", "competencia");
			imoveis.percorrer(filtro, imovel -> {
				csv.linha(visiveis ? imovel.matricula() : Matricula.mascarar(imovel.matricula()), imovel.unidadeNome(),
						rotulo(imovel.situacao()), imovel.mesesSemLigacao(filtro.competencia()),
						imovel.ultimoMesLigado(), filtro.competencia());
				linhas[0]++;
			});
		}
		var resultado = new ResultadoExportacao(linhas[0], HexFormat.of().formatHex(resumo.digest()));

		var detalhes = detalhes(filtro);
		detalhes.put("formato", "CSV");
		detalhes.put("linhas", resultado.linhas());
		detalhes.put("sha256", resultado.sha256());
		detalhes.put("matriculasCompletas", visiveis);
		auditoria.registrar(TipoEvento.RELATORIO_EXPORTADO, RECURSO, detalhes);
		return resultado;
	}

	private YearMonth competencia(Optional<YearMonth> pedida) {
		return pedida.or(imoveis::buscarUltimaCompetencia).orElseThrow(RecursoNaoEncontradoException::new);
	}

	/**
	 * Matrícula completa só se a permissão vale na unidade consultada: todas as linhas
	 * estão dentro dela (RN-13, ADR 0013).
	 */
	private boolean podeVerDadosPessoais(UUID unidade) {
		var contexto = ContextoAcesso.exigir();
		return unidades.caminhoNoEscopo(unidade)
			.map(caminho -> contexto.podeEm(PERMISSAO_DADOS_PESSOAIS, caminho))
			.orElse(false);
	}

	private static Map<String, @Nullable Object> detalhes(FiltroSemLigacao filtro) {
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("unidade", filtro.unidadeId().toString());
		detalhes.put("competencia", filtro.competencia().toString());
		detalhes.put("situacao", filtro.situacao() == null ? "TODAS" : filtro.situacao().name());
		return detalhes;
	}

	private static String rotulo(SituacaoImovel situacao) {
		return switch (situacao) {
			case FACTIVEL -> "Factível";
			case CORTADO -> "Cortado";
		};
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
