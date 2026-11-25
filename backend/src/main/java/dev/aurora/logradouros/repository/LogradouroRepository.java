package dev.aurora.logradouros.repository;

import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.logradouros.model.Cobertura;
import dev.aurora.logradouros.model.DadosLogradouro;
import dev.aurora.logradouros.model.DadosTrecho;
import dev.aurora.logradouros.model.FeicaoTrecho;
import dev.aurora.logradouros.model.ImovelNoLogradouro;
import dev.aurora.logradouros.model.Lado;
import dev.aurora.logradouros.model.LinhaEndereco;
import dev.aurora.logradouros.model.Logradouro;
import dev.aurora.logradouros.model.Textos;
import dev.aurora.logradouros.model.Trecho;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Logradouros, trechos e endereços dos imóveis; o RLS limita ao escopo. As gravações em
 * massa usam {@code unnest} de arrays em blocos (uma ida ao banco por bloco, não por
 * linha).
 */
@Repository
public class LogradouroRepository {

	private static final String RESUMO = """
			SELECT l.id, l.unidade_id, u.nome AS unidade_nome, l.codigo, l.tipo, l.nome, l.bairro,
			       (SELECT count(*) FROM trecho t WHERE t.logradouro_id = l.id) AS trechos,
			       (SELECT coalesce(sum(t.extensao_m), 0) FROM trecho t WHERE t.logradouro_id = l.id) AS extensao,
			       (SELECT count(*) FROM imovel_endereco e WHERE e.logradouro_id = l.id) AS imoveis
			  FROM logradouro l
			  JOIN unidade u ON u.id = l.unidade_id
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	LogradouroRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	// ----- Consulta -----

	/** Logradouros do município (ou de todos sob a unidade), por nome ou código. */
	public List<Logradouro> buscar(@Nullable String caminhoUnidade, String termo, int limite, int deslocamento) {
		return jdbc
			.sql(RESUMO + filtro(caminhoUnidade, termo) + " ORDER BY l.nome, l.codigo LIMIT :limite OFFSET :desloc")
			.param("caminho", caminhoUnidade)
			.param("termo", "%" + Textos.paraBusca(termo).replace("%", "").replace("_", "") + "%")
			.param("codigo", termo.strip())
			.param("limite", limite)
			.param("desloc", deslocamento)
			.query(LogradouroRepository::resumo)
			.list();
	}

	public long contar(@Nullable String caminhoUnidade, String termo) {
		return jdbc
			.sql("SELECT count(*) FROM logradouro l JOIN unidade u ON u.id = l.unidade_id "
					+ filtro(caminhoUnidade, termo))
			.param("caminho", caminhoUnidade)
			.param("termo", "%" + Textos.paraBusca(termo).replace("%", "").replace("_", "") + "%")
			.param("codigo", termo.strip())
			.query(Long.class)
			.single();
	}

	private static String filtro(@Nullable String caminhoUnidade, String termo) {
		var condicoes = new ArrayList<String>();
		if (caminhoUnidade != null) {
			condicoes.add("u.caminho <@ CAST(:caminho AS ltree)");
		}
		if (!termo.isBlank()) {
			condicoes.add("(l.nome_busca LIKE :termo OR l.codigo = :codigo)");
		}
		return condicoes.isEmpty() ? "" : " WHERE " + String.join(" AND ", condicoes);
	}

	public Optional<Logradouro> buscar(UUID id) {
		return jdbc.sql(RESUMO + " WHERE l.id = :id").param("id", id).query(LogradouroRepository::resumo).optional();
	}

	public List<Trecho> trechos(UUID logradouro) {
		return jdbc
			.sql("""
					SELECT t.id, t.codigo, t.extensao_m, t.numero_inicial, t.numero_final, t.lado, ST_AsGeoJSON(t.geom, 6) AS geo,
					       (SELECT count(*) FROM imovel_endereco e WHERE e.trecho_id = t.id) AS imoveis
					  FROM trecho t WHERE t.logradouro_id = :id
					 ORDER BY t.numero_inicial NULLS LAST, t.codigo""")
			.param("id", logradouro)
			.query((r, n) -> new Trecho(r.getObject("id", UUID.class), r.getString("codigo"),
					r.getBigDecimal("extensao_m"), inteiro(r, "numero_inicial"), inteiro(r, "numero_final"),
					Lado.valueOf(r.getString("lado")), r.getLong("imoveis"), coordenadas(r.getString("geo"))))
			.list();
	}

	/** Imóveis do logradouro na ordem da numeração, com a unidade (decide a máscara). */
	public List<ImovelDaUnidade> imoveis(UUID logradouro, int limite) {
		return jdbc.sql("""
				SELECT e.codigo_origem, e.unidade_id, e.numero, e.complemento, t.codigo AS trecho, p.precisao
				  FROM imovel_endereco e
				  LEFT JOIN trecho t ON t.id = e.trecho_id
				  LEFT JOIN imovel_localizacao p ON p.codigo_origem = e.codigo_origem
				 WHERE e.logradouro_id = :id
				 ORDER BY e.numero_inteiro NULLS LAST, e.numero, e.codigo_origem
				 LIMIT :limite""")
			.param("id", logradouro)
			.param("limite", limite)
			.query((r, n) -> new ImovelDaUnidade(r.getObject("unidade_id", UUID.class),
					new ImovelNoLogradouro(r.getString("codigo_origem"), r.getString("numero"),
							r.getString("complemento"), r.getString("trecho"), r.getString("precisao"))))
			.list();
	}

	public record ImovelDaUnidade(UUID unidadeId, ImovelNoLogradouro imovel) {
	}

	/**
	 * Imóveis da competência mais recente sob a unidade e quanto deles já tem endereço e
	 * trecho.
	 */
	public Cobertura cobertura(String caminhoUnidade) {
		return jdbc
			.sql("""
					WITH ultima AS (SELECT max(competencia) AS c FROM imovel_situacao_mensal),
					     imoveis AS (
					       SELECT s.codigo_origem FROM imovel_situacao_mensal s JOIN unidade u ON u.id = s.unidade_id
					        WHERE s.competencia = (SELECT c FROM ultima) AND u.caminho <@ CAST(:caminho AS ltree))
					SELECT count(*) AS imoveis,
					       count(e.codigo_origem) AS com_endereco,
					       count(e.trecho_id) AS com_trecho,
					       count(*) FILTER (WHERE p.codigo_origem IS NULL OR p.precisao IN ('LOGRADOURO', 'BAIRRO')) AS aproximados
					  FROM imoveis i
					  LEFT JOIN imovel_endereco e ON e.codigo_origem = i.codigo_origem
					  LEFT JOIN imovel_localizacao p ON p.codigo_origem = i.codigo_origem""")
			.param("caminho", caminhoUnidade)
			.query((r, n) -> new Cobertura(r.getLong("imoveis"), r.getLong("com_endereco"), r.getLong("com_trecho"),
					r.getLong("aproximados")))
			.single();
	}

	// ----- Correção manual -----

	public boolean atualizar(UUID id, DadosLogradouro dados, UUID usuario) {
		return jdbc.sql("""
				UPDATE logradouro SET tipo = :tipo, nome = :nome, bairro = :bairro, nome_busca = :busca,
				       atualizado_em = now(), atualizado_por = :usuario
				 WHERE id = :id""")
			.param("id", id)
			.param("tipo", dados.tipo())
			.param("nome", dados.nome())
			.param("bairro", dados.bairro())
			.param("busca", Textos.paraBusca(dados.nome()))
			.param("usuario", usuario)
			.update() == 1;
	}

	/** Atualiza a faixa do trecho; devolve o logradouro dele. */
	public Optional<UUID> atualizarTrecho(UUID id, DadosTrecho dados) {
		return jdbc.sql("""
				UPDATE trecho SET numero_inicial = :inicial, numero_final = :final, lado = :lado, atualizado_em = now()
				 WHERE id = :id RETURNING logradouro_id""")
			.param("id", id)
			.param("inicial", dados.numeroInicial())
			.param("final", dados.numeroFinal())
			.param("lado", dados.lado().name())
			.query(UUID.class)
			.optional();
	}

	// ----- Importação de trechos -----

	/**
	 * Posições (1..n na lista) cuja geometria não vira uma linha válida (partes soltas,
	 * autointerseção).
	 */
	public List<Integer> geometriasInvalidas(List<FeicaoTrecho> feicoes) {
		var invalidas = new ArrayList<Integer>();
		for (var bloco : blocos(feicoes)) {
			invalidas.addAll(jdbc.sql("""
					SELECT d.posicao FROM unnest(CAST(:posicoes AS int[]), CAST(:geos AS text[])) AS d(posicao, geo)
					 CROSS JOIN LATERAL (SELECT ST_LineMerge(ST_Force2D(ST_GeomFromGeoJSON(d.geo))) AS g) x
					 WHERE GeometryType(x.g) <> 'LINESTRING' OR NOT ST_IsValid(x.g) OR ST_IsEmpty(x.g)""")
				.param("posicoes", bloco.stream().map(FeicaoTrecho::posicao).toArray(Integer[]::new))
				.param("geos", bloco.stream().map(FeicaoTrecho::geometria).toArray(String[]::new))
				.query(Integer.class)
				.list());
		}
		return invalidas;
	}

	/** Grava os logradouros (um por código) e devolve o id de cada código. */
	public Map<String, UUID> gravarLogradouros(UUID unidade, Collection<FeicaoTrecho> logradouros, UUID usuario) {
		var lista = List.copyOf(logradouros);
		for (var bloco : blocos(lista)) {
			jdbc.sql(
					"""
							INSERT INTO logradouro (id, tenant_id, unidade_id, codigo, tipo, nome, bairro, nome_busca, atualizado_por)
							SELECT gen_random_uuid(), app_tenant(), :unidade, d.codigo, d.tipo, d.nome, d.bairro, d.busca, :usuario
							  FROM unnest(CAST(:codigos AS text[]), CAST(:tipos AS text[]), CAST(:nomes AS text[]),
							              CAST(:bairros AS text[]), CAST(:buscas AS text[])) AS d(codigo, tipo, nome, bairro, busca)
							ON CONFLICT (tenant_id, unidade_id, codigo) DO UPDATE
							   SET tipo = excluded.tipo, nome = excluded.nome, bairro = excluded.bairro,
							       nome_busca = excluded.nome_busca, atualizado_em = now(), atualizado_por = excluded.atualizado_por
							 WHERE (logradouro.tipo, logradouro.nome, logradouro.bairro)
							       IS DISTINCT FROM (excluded.tipo, excluded.nome, excluded.bairro)""")
				.param("unidade", unidade)
				.param("usuario", usuario)
				.param("codigos", bloco.stream().map(FeicaoTrecho::codigoLogradouro).toArray(String[]::new))
				.param("tipos", bloco.stream().map(FeicaoTrecho::tipo).toArray(String[]::new))
				.param("nomes", bloco.stream().map(FeicaoTrecho::nome).toArray(String[]::new))
				.param("bairros", bloco.stream().map(FeicaoTrecho::bairro).toArray(String[]::new))
				.param("buscas", bloco.stream().map(f -> Textos.paraBusca(f.nome())).toArray(String[]::new))
				.update();
		}
		var ids = new HashMap<String, UUID>();
		for (var bloco : blocos(lista)) {
			jdbc.sql(
					"SELECT codigo, id FROM logradouro WHERE unidade_id = :unidade AND codigo = ANY(CAST(:codigos AS text[]))")
				.param("unidade", unidade)
				.param("codigos", bloco.stream().map(FeicaoTrecho::codigoLogradouro).toArray(String[]::new))
				.query((r, n) -> ids.put(r.getString("codigo"), r.getObject("id", UUID.class)))
				.list();
		}
		return ids;
	}

	/** Grava os trechos; devolve [novos, atualizados]. */
	public int[] gravarTrechos(UUID unidade, List<FeicaoTrecho> feicoes, Map<String, UUID> logradouros) {
		int novos = 0, atualizados = 0;
		for (var bloco : blocos(feicoes)) {
			var resultado = jdbc
				.sql("""
						INSERT INTO trecho (id, tenant_id, unidade_id, logradouro_id, codigo, geom, extensao_m, numero_inicial,
						                    numero_final, lado)
						SELECT gen_random_uuid(), app_tenant(), :unidade, d.logradouro, d.codigo, x.g,
						       round(CAST(ST_Length(CAST(x.g AS geography)) AS numeric), 1), d.inicial, d.final, d.lado
						  FROM unnest(CAST(:logradouros AS uuid[]), CAST(:codigos AS text[]), CAST(:iniciais AS int[]),
						              CAST(:finais AS int[]), CAST(:lados AS text[]), CAST(:geos AS text[]))
						       AS d(logradouro, codigo, inicial, final, lado, geo)
						 CROSS JOIN LATERAL (SELECT ST_SetSRID(ST_LineMerge(ST_Force2D(ST_GeomFromGeoJSON(d.geo))), 4674) AS g) x
						ON CONFLICT (tenant_id, unidade_id, codigo) DO UPDATE
						   SET logradouro_id = excluded.logradouro_id, geom = excluded.geom, extensao_m = excluded.extensao_m,
						       numero_inicial = excluded.numero_inicial, numero_final = excluded.numero_final,
						       lado = excluded.lado, atualizado_em = now()
						 WHERE (trecho.logradouro_id, trecho.numero_inicial, trecho.numero_final, trecho.lado,
						        ST_AsBinary(trecho.geom))
						       IS DISTINCT FROM (excluded.logradouro_id, excluded.numero_inicial, excluded.numero_final,
						        excluded.lado, ST_AsBinary(excluded.geom))
						RETURNING old.id IS NULL AS novo""")
				.param("unidade", unidade)
				.param("logradouros",
						bloco.stream().map(f -> logradouros.get(f.codigoLogradouro())).toArray(UUID[]::new))
				.param("codigos", bloco.stream().map(FeicaoTrecho::codigoTrecho).toArray(String[]::new))
				.param("iniciais", bloco.stream().map(FeicaoTrecho::numeroInicial).toArray(Integer[]::new))
				.param("finais", bloco.stream().map(FeicaoTrecho::numeroFinal).toArray(Integer[]::new))
				.param("lados", bloco.stream().map(f -> f.lado().name()).toArray(String[]::new))
				.param("geos", bloco.stream().map(FeicaoTrecho::geometria).toArray(String[]::new))
				.query(Boolean.class)
				.list();
			for (var novo : resultado) {
				if (novo) {
					novos++;
				}
				else {
					atualizados++;
				}
			}
		}
		return new int[] { novos, atualizados };
	}

	/**
	 * Trechos do município que não vieram no arquivo (não são apagados: OS podem apontar
	 * para eles).
	 */
	public int trechosAusentes(UUID unidade, List<FeicaoTrecho> feicoes) {
		return jdbc.sql(
				"SELECT count(*) FROM trecho WHERE unidade_id = :unidade AND NOT (codigo = ANY(CAST(:codigos AS text[])))")
			.param("unidade", unidade)
			.param("codigos", feicoes.stream().map(FeicaoTrecho::codigoTrecho).toArray(String[]::new))
			.query(Integer.class)
			.single();
	}

	// ----- Importação de endereços -----

	/**
	 * Para cada linha, a unidade do imóvel (situação mensal mais recente, no escopo) e o
	 * logradouro com o código no município dele (ou acima). Nulos quando não acha.
	 */
	public Map<Integer, Vinculo> resolver(List<LinhaEndereco> linhas) {
		var vinculos = new HashMap<Integer, Vinculo>();
		for (var bloco : blocos(linhas)) {
			jdbc.sql(
					"""
							WITH d AS (SELECT * FROM unnest(CAST(:linhas AS int[]), CAST(:matriculas AS text[]),
							                                CAST(:codigos AS text[])) AS d(linha, matricula, codigo)),
							     ultima AS (SELECT max(competencia) AS c FROM imovel_situacao_mensal)
							SELECT d.linha, i.unidade_id, l.id AS logradouro
							  FROM d
							  LEFT JOIN imovel_situacao_mensal i ON i.competencia = (SELECT c FROM ultima) AND i.codigo_origem = d.matricula
							  LEFT JOIN unidade ui ON ui.id = i.unidade_id
							  LEFT JOIN LATERAL (
							       SELECT l.id FROM logradouro l JOIN unidade ul ON ul.id = l.unidade_id
							        WHERE l.codigo = d.codigo AND ul.caminho @> ui.caminho
							        ORDER BY nlevel(ul.caminho) DESC LIMIT 1) l ON true""")
				.param("linhas", bloco.stream().map(LinhaEndereco::linha).toArray(Integer[]::new))
				.param("matriculas", bloco.stream().map(LinhaEndereco::matricula).toArray(String[]::new))
				.param("codigos", bloco.stream().map(LinhaEndereco::codigoLogradouro).toArray(String[]::new))
				.query((r, n) -> vinculos.put(r.getInt("linha"),
						new Vinculo(r.getObject("unidade_id", UUID.class), r.getObject("logradouro", UUID.class))))
				.list();
		}
		return vinculos;
	}

	public record Vinculo(@Nullable UUID unidadeId, @Nullable UUID logradouroId) {
	}

	/** Grava os endereços resolvidos; devolve [novos, atualizados]. */
	public int[] gravarEnderecos(List<LinhaEndereco> linhas, Map<Integer, Vinculo> vinculos, UUID usuario) {
		int novos = 0, atualizados = 0;
		for (var bloco : blocos(linhas)) {
			var resultado = jdbc
				.sql("""
						INSERT INTO imovel_endereco (tenant_id, codigo_origem, unidade_id, logradouro_id, numero, numero_inteiro,
						                             complemento, atualizado_por)
						SELECT app_tenant(), d.matricula, d.unidade, d.logradouro, d.numero, d.inteiro, d.complemento, :usuario
						  FROM unnest(CAST(:matriculas AS text[]), CAST(:unidades AS uuid[]), CAST(:logradouros AS uuid[]),
						              CAST(:numeros AS text[]), CAST(:inteiros AS int[]), CAST(:complementos AS text[]))
						       AS d(matricula, unidade, logradouro, numero, inteiro, complemento)
						ON CONFLICT (tenant_id, codigo_origem) DO UPDATE
						   SET unidade_id = excluded.unidade_id, logradouro_id = excluded.logradouro_id,
						       numero = excluded.numero, numero_inteiro = excluded.numero_inteiro,
						       complemento = excluded.complemento, atualizado_em = now(),
						       atualizado_por = excluded.atualizado_por,
						       trecho_id = CASE WHEN imovel_endereco.logradouro_id = excluded.logradouro_id
						                          AND imovel_endereco.numero_inteiro IS NOT DISTINCT FROM excluded.numero_inteiro
						                        THEN imovel_endereco.trecho_id END
						 WHERE (imovel_endereco.unidade_id, imovel_endereco.logradouro_id, imovel_endereco.numero,
						        imovel_endereco.complemento)
						       IS DISTINCT FROM (excluded.unidade_id, excluded.logradouro_id, excluded.numero, excluded.complemento)
						RETURNING old.codigo_origem IS NULL AS novo""")
				.param("usuario", usuario)
				.param("matriculas", bloco.stream().map(LinhaEndereco::matricula).toArray(String[]::new))
				.param("unidades", bloco.stream().map(l -> vinculos.get(l.linha()).unidadeId()).toArray(UUID[]::new))
				.param("logradouros",
						bloco.stream().map(l -> vinculos.get(l.linha()).logradouroId()).toArray(UUID[]::new))
				.param("numeros", bloco.stream().map(LinhaEndereco::numero).toArray(String[]::new))
				.param("inteiros", bloco.stream().map(LinhaEndereco::numeroInteiro).toArray(Integer[]::new))
				.param("complementos", bloco.stream().map(LinhaEndereco::complemento).toArray(String[]::new))
				.query(Boolean.class)
				.list();
			for (var novo : resultado) {
				if (novo) {
					novos++;
				}
				else {
					atualizados++;
				}
			}
		}
		return new int[] { novos, atualizados };
	}

	/**
	 * Escolhe o trecho de cada endereço do município (ou das matrículas): pela faixa de
	 * numeração e lado; senão o trecho do logradouro mais perto do ponto do imóvel; senão
	 * o único trecho do logradouro. Devolve quantos continuam sem trecho.
	 */
	public int vincularTrechos(@Nullable UUID unidadeDoLogradouro, @Nullable List<String> matriculas) {
		var filtro = unidadeDoLogradouro != null
				? "e.logradouro_id IN (SELECT id FROM logradouro WHERE unidade_id = :unidade)"
				: "e.codigo_origem = ANY(CAST(:matriculas AS text[]))";
		var parametros = matriculas == null ? new String[0] : matriculas.toArray(String[]::new);
		jdbc.sql("""
				UPDATE imovel_endereco e SET trecho_id = coalesce(
				    (SELECT t.id FROM trecho t
				      WHERE t.logradouro_id = e.logradouro_id AND e.numero_inteiro IS NOT NULL
				        AND t.numero_inicial IS NOT NULL AND t.numero_final IS NOT NULL
				        AND e.numero_inteiro BETWEEN t.numero_inicial AND t.numero_final
				        AND (t.lado = 'AMBOS' OR (t.lado = 'PAR') = (e.numero_inteiro % 2 = 0))
				      ORDER BY t.numero_final - t.numero_inicial, t.codigo LIMIT 1),
				    (SELECT t.id FROM trecho t JOIN imovel_localizacao p ON p.codigo_origem = e.codigo_origem
				      WHERE t.logradouro_id = e.logradouro_id ORDER BY t.geom <-> p.geom, t.codigo LIMIT 1),
				    (SELECT min(CAST(t.id AS text))::uuid FROM trecho t WHERE t.logradouro_id = e.logradouro_id
				      HAVING count(*) = 1))
				 WHERE\s""" + filtro).param("unidade", unidadeDoLogradouro).param("matriculas", parametros).update();
		return jdbc.sql("SELECT count(*) FROM imovel_endereco e WHERE e.trecho_id IS NULL AND " + filtro)
			.param("unidade", unidadeDoLogradouro)
			.param("matriculas", parametros)
			.query(Integer.class)
			.single();
	}

	// ----- Apoio -----

	private static <T> List<List<T>> blocos(List<T> lista) {
		var blocos = new ArrayList<List<T>>();
		for (int i = 0; i < lista.size(); i += TAMANHO_DO_BLOCO) {
			blocos.add(lista.subList(i, Math.min(lista.size(), i + TAMANHO_DO_BLOCO)));
		}
		return blocos;
	}

	private static final int TAMANHO_DO_BLOCO = 5_000;

	private List<double[]> coordenadas(String geoJson) {
		var no = json.readTree(geoJson).get("coordinates");
		var pontos = new ArrayList<double[]>(no.size());
		for (var p : no) {
			pontos.add(new double[] { p.get(0).asDouble(), p.get(1).asDouble() });
		}
		return pontos;
	}

	private static @Nullable Integer inteiro(ResultSet r, String coluna) throws SQLException {
		var valor = r.getInt(coluna);
		return r.wasNull() ? null : valor;
	}

	private static Logradouro resumo(ResultSet r, int n) throws SQLException {
		return new Logradouro(r.getObject("id", UUID.class), r.getObject("unidade_id", UUID.class),
				r.getString("unidade_nome"), r.getString("codigo"), r.getString("tipo"), r.getString("nome"),
				r.getString("bairro"), r.getLong("trechos"),
				r.getBigDecimal("extensao").setScale(1, RoundingMode.HALF_UP), r.getLong("imoveis"));
	}

}
