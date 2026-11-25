package dev.aurora.logradouros.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.comum.TriagemDeArquivos;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.logradouros.model.FeicaoTrecho;
import dev.aurora.logradouros.model.Importacao;
import dev.aurora.logradouros.model.LinhaEndereco;
import dev.aurora.logradouros.model.Rejeicao;
import dev.aurora.logradouros.model.ResultadoImportacao;
import dev.aurora.logradouros.model.TipoImportacao;
import dev.aurora.logradouros.repository.ImportacaoRepository;
import dev.aurora.logradouros.repository.LogradouroRepository;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Importação dos trechos (GeoJSON, por município) e dos endereços dos imóveis (CSV) da
 * concessionária (J3, ADR 0043). A simulação faz tudo dentro de uma transação desfeita no
 * fim: o relatório é o mesmo da importação de verdade, sem gravar nada. O arquivo passa
 * pelo antivírus antes da leitura e não é guardado (o histórico fica com o hash).
 */
@Service
public class ImportacaoService {

	static final int MAXIMO_DE_REJEICOES = 200;

	static final int TAMANHO_MAXIMO = 50 * 1024 * 1024;

	private final LogradouroRepository logradouros;

	private final ImportacaoRepository importacoes;

	private final Unidades unidades;

	private final TriagemDeArquivos triagem;

	private final Auditoria auditoria;

	private final TransactionTemplate transacao;

	private final JsonMapper json;

	ImportacaoService(LogradouroRepository logradouros, ImportacaoRepository importacoes, Unidades unidades,
			TriagemDeArquivos triagem, Auditoria auditoria, TransactionTemplate transacao, JsonMapper json) {
		this.logradouros = logradouros;
		this.importacoes = importacoes;
		this.unidades = unidades;
		this.triagem = triagem;
		this.auditoria = auditoria;
		this.transacao = transacao;
		this.json = json;
	}

	public ResultadoImportacao importarTrechos(UUID unidade, String arquivo, byte[] conteudo, boolean simular)
			throws IOException {
		var municipio = unidades.buscarNoEscopo(unidade)
			.orElseThrow(() -> new RegraVioladaException("Escolha um município onde você gerencia logradouros."));
		if (!"MUNICIPIO".equals(municipio.tipo())) {
			throw new RegraVioladaException("Os trechos são importados por município.");
		}
		verificar(conteudo, arquivo, "GEOJSON_DE_TRECHOS");
		var leitura = LeitorGeoJsonTrechos.ler(new ByteArrayInputStream(conteudo), json);
		var usuario = usuario();
		return Objects.requireNonNull(transacao.execute(status -> {
			var rejeicoes = new ArrayList<>(leitura.rejeicoes());
			var invalidas = new HashSet<>(logradouros.geometriasInvalidas(leitura.itens()));
			var validas = new ArrayList<FeicaoTrecho>();
			for (var f : leitura.itens()) {
				if (invalidas.contains(f.posicao())) {
					rejeicoes.add(new Rejeicao(f.posicao(),
							"Geometria não forma uma linha válida (partes separadas ou que se cruzam)."));
				}
				else {
					validas.add(f);
				}
			}
			// Um logradouro por código; vale o último nome do arquivo.
			var porCodigo = validas.stream()
				.collect(Collectors.toMap(FeicaoTrecho::codigoLogradouro, Function.identity(), (a, b) -> b,
						LinkedHashMap::new));
			var ids = logradouros.gravarLogradouros(unidade, porCodigo.values(), usuario);
			var gravados = logradouros.gravarTrechos(unidade, validas, ids);
			var ausentes = logradouros.trechosAusentes(unidade, validas);
			var semTrecho = logradouros.vincularTrechos(unidade, null);
			var resultado = resultado(TipoImportacao.TRECHOS, simular, leitura.lidos(), validas.size(), gravados,
					rejeicoes, semTrecho, ausentes);
			if (simular) {
				status.setRollbackOnly();
			}
			else {
				registrar(resultado, unidade, arquivo, conteudo, usuario, TipoEvento.LOGRADOUROS_IMPORTADOS);
			}
			return resultado;
		}));
	}

	public ResultadoImportacao importarEnderecos(String arquivo, byte[] conteudo, boolean simular) throws IOException {
		verificar(conteudo, arquivo, "CSV_DE_ENDERECOS");
		var leitura = LeitorCsvEnderecos.ler(new ByteArrayInputStream(conteudo));
		var usuario = usuario();
		return Objects.requireNonNull(transacao.execute(status -> {
			var rejeicoes = new ArrayList<>(leitura.rejeicoes());
			var vinculos = logradouros.resolver(leitura.itens());
			var validas = new ArrayList<LinhaEndereco>();
			for (var linha : leitura.itens()) {
				var v = vinculos.get(linha.linha());
				if (v == null || v.unidadeId() == null) {
					rejeicoes.add(new Rejeicao(linha.linha(),
							"Matrícula " + linha.matricula() + " não está na carga mais recente do seu escopo."));
				}
				else if (v.logradouroId() == null) {
					rejeicoes.add(new Rejeicao(linha.linha(),
							"Logradouro " + linha.codigoLogradouro() + " não existe no município do imóvel."));
				}
				else {
					validas.add(linha);
				}
			}
			var gravados = logradouros.gravarEnderecos(validas, vinculos, usuario);
			var semTrecho = validas.isEmpty() ? 0
					: logradouros.vincularTrechos(null, validas.stream().map(LinhaEndereco::matricula).toList());
			var resultado = resultado(TipoImportacao.ENDERECOS, simular, leitura.lidos(), validas.size(), gravados,
					rejeicoes, semTrecho, 0);
			if (simular) {
				status.setRollbackOnly();
			}
			else {
				registrar(resultado, null, arquivo, conteudo, usuario, TipoEvento.ENDERECOS_IMPORTADOS);
			}
			return resultado;
		}));
	}

	public List<Importacao> recentes() {
		return Objects.requireNonNull(transacao.execute(status -> importacoes.recentes(30)));
	}

	private void verificar(byte[] conteudo, String arquivo, String tipo) {
		if (conteudo.length == 0) {
			throw new RegraVioladaException("O arquivo está vazio.");
		}
		if (conteudo.length > TAMANHO_MAXIMO) {
			throw new RegraVioladaException("O arquivo passa do limite de 50 MB; divida por município.");
		}
		var ameaca = triagem.ameaca(conteudo);
		if (ameaca.isPresent()) {
			transacao.executeWithoutResult(t -> auditoria.registrar(TipoEvento.ARQUIVO_RECUSADO_PELO_ANTIVIRUS,
					"importacao-logradouro", Map.of("ameaca", ameaca.get(), "sha256", sha256(conteudo), "bytes",
							conteudo.length, "tipo", tipo, "arquivo", arquivo)));
			throw new RegraVioladaException("O arquivo foi recusado pelo antivírus (" + ameaca.get() + ").");
		}
	}

	private void registrar(ResultadoImportacao r, @Nullable UUID unidade, String arquivo, byte[] conteudo, UUID usuario,
			TipoEvento evento) {
		var id = UUID.randomUUID();
		var hash = sha256(conteudo);
		importacoes.registrar(id, r, unidade, arquivo, hash, usuario);
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("arquivo", arquivo);
		detalhes.put("sha256", hash);
		detalhes.put("unidade", unidade == null ? null : unidade.toString());
		detalhes.put("lidos", r.lidos());
		detalhes.put("novos", r.novos());
		detalhes.put("atualizados", r.atualizados());
		detalhes.put("rejeitados", r.rejeitados());
		auditoria.registrar(evento, "importacao-logradouro/" + id, detalhes);
	}

	private static ResultadoImportacao resultado(TipoImportacao tipo, boolean simular, int lidos, int validas,
			int[] gravados, List<Rejeicao> rejeicoes, int semTrecho, int ausentes) {
		var ordenadas = rejeicoes.stream()
			.sorted(Comparator.comparingInt(Rejeicao::posicao))
			.limit(MAXIMO_DE_REJEICOES)
			.toList();
		return new ResultadoImportacao(tipo, simular, lidos, gravados[0], gravados[1],
				validas - gravados[0] - gravados[1], rejeicoes.size(), ordenadas, semTrecho, ausentes);
	}

	private static UUID usuario() {
		var usuario = ContextoAcesso.exigir().usuarioId();
		if (usuario == null) {
			throw new IllegalStateException("Importação fora de uma requisição");
		}
		return usuario;
	}

	static String sha256(byte[] conteudo) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
