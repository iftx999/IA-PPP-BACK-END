package dev.aurora.adesao.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.adesao.model.Adesao;
import dev.aurora.adesao.model.Composicao;
import dev.aurora.adesao.model.MedicaoUnidade;
import dev.aurora.adesao.model.MetaVigente;
import dev.aurora.adesao.model.PontoSerie;
import dev.aurora.adesao.model.UnidadeResumo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Leituras do painel. A adesão vem de {@code medicao} (indicador ADESAO_REDE, numerador =
 * ligados, denominador = com rede), pré-agregada por unidade e competência. O RLS limita
 * tudo ao tenant e ao escopo do usuário; as metas também são visíveis nas unidades acima
 * do escopo, para resolver a herança.
 */
@Repository
public class AdesaoRepository {

	public static final String INDICADOR_ADESAO = "ADESAO_REDE";

	private final JdbcClient jdbc;

	AdesaoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<UUID> buscarIndicadorAdesao() {
		return jdbc.sql("SELECT id FROM indicador WHERE codigo = ?")
			.param(INDICADOR_ADESAO)
			.query(UUID.class)
			.optional();
	}

	public Optional<UnidadeResumo> buscarUnidadeNoEscopo(UUID unidadeId) {
		return jdbc.sql("""
				SELECT id, nome, tipo FROM unidade
				 WHERE id = ? AND ativo AND caminho <@ ANY (app_escopos())""")
			.param(unidadeId)
			.query(UnidadeResumo.class)
			.optional();
	}

	/** Unidade-pai, só se também estiver no escopo (não revela unidades de fora). */
	public Optional<UnidadeResumo> buscarPaiNoEscopo(UUID unidadeId) {
		return jdbc.sql("""
				SELECT p.id, p.nome, p.tipo
				  FROM unidade u JOIN unidade p ON p.id = u.unidade_pai_id
				 WHERE u.id = ? AND p.ativo AND p.caminho <@ ANY (app_escopos())""")
			.param(unidadeId)
			.query(UnidadeResumo.class)
			.optional();
	}

	/**
	 * Unidade mais alta do escopo que tem medição, preferindo a concessão ao grupo
	 * econômico.
	 */
	public Optional<UnidadeResumo> buscarUnidadePadrao(UUID indicadorId) {
		return jdbc.sql("""
				SELECT u.id, u.nome, u.tipo FROM unidade u
				 WHERE u.ativo AND u.caminho <@ ANY (app_escopos())
				   AND EXISTS (SELECT 1 FROM medicao m WHERE m.unidade_id = u.id AND m.indicador_id = ?)
				 ORDER BY u.tipo = 'GRUPO', nlevel(u.caminho), u.nome
				 LIMIT 1""").param(indicadorId).query(UnidadeResumo.class).optional();
	}

	/**
	 * RN-14: só competências com medição calculada, da mais recente para a mais antiga.
	 */
	public List<YearMonth> listarCompetencias(UUID unidadeId, UUID indicadorId) {
		return jdbc.sql("""
				SELECT periodo_inicio FROM medicao
				 WHERE unidade_id = ? AND indicador_id = ?
				 ORDER BY periodo_inicio DESC""")
			.param(unidadeId)
			.param(indicadorId)
			.query((linha, n) -> YearMonth.from(linha.getDate(1).toLocalDate()))
			.list();
	}

	public List<PontoSerie> listarSerie(UUID unidadeId, UUID indicadorId, YearMonth de, YearMonth ate) {
		return jdbc.sql("""
				SELECT periodo_inicio, numerador, denominador FROM medicao
				 WHERE unidade_id = ? AND indicador_id = ? AND periodo_inicio BETWEEN ? AND ?
				 ORDER BY periodo_inicio""")
			.param(unidadeId)
			.param(indicadorId)
			.param(inicio(de))
			.param(inicio(ate))
			.query((linha, n) -> new PontoSerie(YearMonth.from(linha.getDate("periodo_inicio").toLocalDate()),
					new Adesao(linha.getLong("numerador"), linha.getLong("denominador"))))
			.list();
	}

	/**
	 * Unidades filhas com medição na competência, com a medição do mês anterior quando
	 * houver.
	 */
	public List<MedicaoUnidade> listarFilhas(UUID unidadeId, UUID indicadorId, YearMonth competencia) {
		return jdbc
			.sql("""
					SELECT u.id, u.nome, u.tipo,
					       m.numerador AS ligados, m.denominador AS com_rede,
					       a.numerador AS ligados_ant, a.denominador AS com_rede_ant
					  FROM unidade u
					  JOIN medicao m ON m.unidade_id = u.id AND m.indicador_id = :indicador AND m.periodo_inicio = :competencia
					  LEFT JOIN medicao a ON a.unidade_id = u.id AND a.indicador_id = :indicador
					                     AND a.periodo_inicio = :anterior
					 WHERE u.unidade_pai_id = :unidade AND u.ativo
					 ORDER BY u.nome""")
			.param("indicador", indicadorId)
			.param("competencia", inicio(competencia))
			.param("anterior", inicio(competencia.minusMonths(1)))
			.param("unidade", unidadeId)
			.query((linha, n) -> {
				var unidade = new UnidadeResumo(linha.getObject("id", UUID.class), linha.getString("nome"),
						linha.getString("tipo"));
				var atual = new Adesao(linha.getLong("ligados"), linha.getLong("com_rede"));
				var ligadosAnteriores = linha.getObject("ligados_ant");
				var anterior = ligadosAnteriores == null ? null
						: new Adesao(linha.getLong("ligados_ant"), linha.getLong("com_rede_ant"));
				return new MedicaoUnidade(unidade, atual, anterior);
			})
			.list();
	}

	/** RN-06: meta própria vigente ou, na falta dela, a da unidade acima mais próxima. */
	public Optional<MetaVigente> buscarMetaVigente(UUID unidadeId, UUID indicadorId, YearMonth competencia) {
		return jdbc.sql("""
				SELECT m.id AS meta_id, m.unidade_id, u.nome AS unidade_nome, m.valor_meta, m.valor_alerta,
				       m.vigencia_inicio, m.vigencia_fim, m.origem, m.unidade_id <> :unidade AS herdada
				  FROM meta m
				  JOIN unidade u ON u.id = m.unidade_id
				 WHERE m.indicador_id = :indicador
				   AND u.caminho @> (SELECT caminho FROM unidade WHERE id = :unidade)
				   AND m.vigencia_inicio <= :competencia
				   AND (m.vigencia_fim IS NULL OR m.vigencia_fim >= :competencia)
				 ORDER BY nlevel(u.caminho) DESC, m.vigencia_inicio DESC
				 LIMIT 1""")
			.param("unidade", unidadeId)
			.param("indicador", indicadorId)
			.param("competencia", inicio(competencia))
			.query(MetaVigente.class)
			.optional();
	}

	/** Factíveis e cortados na unidade e descendentes (RN-02). */
	public Composicao contarSemLigacao(UUID unidadeId, YearMonth competencia) {
		return jdbc.sql("""
				SELECT count(*) FILTER (WHERE s.situacao = 'FACTIVEL') AS factiveis,
				       count(*) FILTER (WHERE s.situacao = 'CORTADO')  AS cortados
				  FROM imovel_situacao_mensal s
				  JOIN unidade u ON u.id = s.unidade_id
				 WHERE s.competencia = ?
				   AND u.caminho <@ (SELECT caminho FROM unidade WHERE id = ?)""")
			.param(inicio(competencia))
			.param(unidadeId)
			.query(Composicao.class)
			.single();
	}

	/**
	 * RN-14: competências com medição de adesão no escopo, da mais recente à mais antiga.
	 */
	public List<YearMonth> listarCompetenciasDoEscopo(UUID indicadorId) {
		return jdbc.sql("SELECT DISTINCT periodo_inicio FROM medicao WHERE indicador_id = ? ORDER BY 1 DESC LIMIT 60")
			.param(indicadorId)
			.query((linha, n) -> YearMonth.from(linha.getDate(1).toLocalDate()))
			.list();
	}

	/** Medição da competência de cada unidade pedida que a tem (unidades do escopo). */
	public Map<UUID, Adesao> buscarMedicoes(Collection<UUID> unidades, UUID indicadorId, YearMonth competencia) {
		var medicoes = new HashMap<UUID, Adesao>();
		if (unidades.isEmpty()) {
			return medicoes;
		}
		jdbc.sql("""
				SELECT unidade_id, numerador, denominador FROM medicao
				 WHERE indicador_id = :indicador AND periodo_inicio = :competencia AND unidade_id IN (:unidades)""")
			.param("indicador", indicadorId)
			.param("competencia", inicio(competencia))
			.param("unidades", List.copyOf(unidades))
			.query(linha -> {
				medicoes.put(linha.getObject("unidade_id", UUID.class),
						new Adesao(linha.getLong("numerador"), linha.getLong("denominador")));
			});
		return medicoes;
	}

	private static Date inicio(YearMonth competencia) {
		return Date.valueOf(LocalDate.of(competencia.getYear(), competencia.getMonth(), 1));
	}

}
