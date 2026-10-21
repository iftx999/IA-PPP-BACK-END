package dev.aurora.imoveis.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import dev.aurora.imoveis.model.FiltroSemLigacao;
import dev.aurora.imoveis.model.ImovelSemLigacao;
import dev.aurora.imoveis.model.SituacaoImovel;
import dev.aurora.imoveis.model.Totais;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Imóveis sem ligação a partir de {@code imovel_situacao_mensal}, limitados pelo RLS ao
 * tenant e ao escopo. O histórico (último mês ligado) é buscado por imóvel com o índice
 * (tenant_id, codigo_origem, competencia).
 */
@Repository
public class ImovelRepository {

	private static final String JUNCAO = """
			  FROM imovel_situacao_mensal s
			  JOIN unidade u ON u.id = s.unidade_id
			""";

	private static final String HISTORICO = """
			  CROSS JOIN LATERAL (
			        SELECT max(x.competencia) FILTER (WHERE x.situacao = 'LIGADO') AS ultimo_ligado,
			               min(x.competencia) AS primeira
			          FROM imovel_situacao_mensal x
			         WHERE x.tenant_id = s.tenant_id AND x.codigo_origem = s.codigo_origem
			           AND x.competencia <= s.competencia) h
			""";

	/** Filtros comuns às abas: a contagem por situação ignora o filtro de situação. */
	private static final String CONDICOES = """
			 WHERE s.competencia = :competencia
			   AND s.situacao IN ('FACTIVEL', 'CORTADO')
			   AND u.caminho <@ (SELECT caminho FROM unidade WHERE id = :unidade)
			   AND (CAST(:matricula AS text) IS NULL OR s.codigo_origem LIKE CAST(:matricula AS text) || '%')
			""";

	private static final String LISTAGEM = """
			SELECT s.codigo_origem, s.situacao, u.id AS unidade_id, u.nome AS unidade_nome, h.ultimo_ligado, h.primeira
			""" + JUNCAO + HISTORICO + CONDICOES + """
			   AND (CAST(:situacao AS text) IS NULL OR s.situacao = CAST(:situacao AS text))
			   AND (CAST(:depois AS text) IS NULL OR s.codigo_origem > CAST(:depois AS text))
			 ORDER BY s.codigo_origem
			""";

	private final JdbcClient jdbc;

	ImovelRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** RN-14: competência mais recente com carga no escopo. */
	public Optional<YearMonth> buscarUltimaCompetencia() {
		return jdbc.sql("SELECT max(competencia) FROM imovel_situacao_mensal")
			.query((linha, n) -> Optional.ofNullable(linha.getDate(1)))
			.single()
			.map(data -> YearMonth.from(data.toLocalDate()));
	}

	public Totais contar(FiltroSemLigacao filtro) {
		return jdbc.sql("""
				SELECT count(*) AS todos,
				       count(*) FILTER (WHERE s.situacao = 'FACTIVEL') AS factiveis,
				       count(*) FILTER (WHERE s.situacao = 'CORTADO') AS cortados
				""" + JUNCAO + CONDICOES)
			.param("competencia", inicio(filtro.competencia()))
			.param("unidade", filtro.unidadeId())
			.param("matricula", filtro.matricula())
			.query(Totais.class)
			.single();
	}

	/** Página ordenada por matrícula, a partir da chave {@code depois} (exclusiva). */
	public List<ImovelSemLigacao> listar(FiltroSemLigacao filtro, @Nullable String depois, int limite) {
		return listagem(filtro, depois, " LIMIT :limite").param("limite", limite)
			.query(ImovelRepository::mapear)
			.list();
	}

	/**
	 * Todos os registros do filtro, em fluxo, sem carregar tudo na memória (exportação).
	 */
	public void percorrer(FiltroSemLigacao filtro, Consumer<ImovelSemLigacao> consumidor) {
		try (var registros = listagem(filtro, null, "").query(ImovelRepository::mapear).stream()) {
			registros.forEach(consumidor);
		}
	}

	private JdbcClient.StatementSpec listagem(FiltroSemLigacao filtro, @Nullable String depois, String complemento) {
		return jdbc.sql(LISTAGEM + complemento)
			.param("competencia", inicio(filtro.competencia()))
			.param("unidade", filtro.unidadeId())
			.param("matricula", filtro.matricula())
			.param("situacao", filtro.situacao() == null ? null : filtro.situacao().name())
			.param("depois", depois);
	}

	private static ImovelSemLigacao mapear(ResultSet linha, int n) throws SQLException {
		var ultimo = linha.getDate("ultimo_ligado");
		return new ImovelSemLigacao(linha.getString("codigo_origem"), linha.getObject("unidade_id", UUID.class),
				linha.getString("unidade_nome"), SituacaoImovel.valueOf(linha.getString("situacao")),
				ultimo == null ? null : YearMonth.from(ultimo.toLocalDate()),
				YearMonth.from(linha.getDate("primeira").toLocalDate()));
	}

	private static Date inicio(YearMonth competencia) {
		return Date.valueOf(LocalDate.of(competencia.getYear(), competencia.getMonth(), 1));
	}

}
