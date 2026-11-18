package dev.aurora.servicos.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.servicos.model.AlvoServico;
import dev.aurora.servicos.model.Condicao;
import dev.aurora.servicos.model.DefinicaoServico;
import dev.aurora.servicos.model.Parametro;
import dev.aurora.servicos.model.Prazos;
import dev.aurora.servicos.model.Servico;
import dev.aurora.servicos.model.TipoParametro;
import dev.aurora.servicos.model.UnidadeMedida;
import dev.aurora.servicos.model.VersaoServico;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Serviços, versões e parâmetros (RN-20): versão e parâmetros só são inseridos, nunca
 * alterados.
 */
@Repository
public class ServicoRepository {

	private static final String VERSAO = """
			v.id AS versao_id, v.versao, v.nome, v.descricao, v.unidade_medida, v.alvo, v.tempo_padrao_min,
			v.tempo_por_unidade, v.prazo_emergencial_horas, v.prazo_alta_dias, v.prazo_media_dias, v.prazo_baixa_dias,
			v.tipo_veiculo_id, v.habilidade_id, v.conferido_pela_carga""";

	private static final String SELECAO = "SELECT s.id, s.grupo_id, s.codigo, s.ativo, s.atualizado_em, " + VERSAO + """

			  FROM servico s
			  JOIN servico_versao v ON v.servico_id = s.id AND v.versao = s.versao_atual
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	ServicoRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public List<Servico> listar() {
		var linhas = jdbc.sql(SELECAO + " ORDER BY s.ativo DESC, v.nome").query(ServicoRepository::servico).list();
		return comParametros(linhas);
	}

	public Optional<Servico> buscar(UUID id) {
		var linhas = jdbc.sql(SELECAO + " WHERE s.id = :id").param("id", id).query(ServicoRepository::servico).list();
		return comParametros(linhas).stream().findFirst();
	}

	/** Trava o serviço para criar versão sem corrida; devolve a versão atual. */
	public Optional<Integer> travar(UUID id) {
		return jdbc.sql("SELECT versao_atual FROM servico WHERE id = :id FOR UPDATE")
			.param("id", id)
			.query(Integer.class)
			.optional();
	}

	public void inserir(UUID id, UUID grupo, String codigo, boolean ativo) {
		jdbc.sql("""
				INSERT INTO servico (id, tenant_id, grupo_id, codigo, ativo, versao_atual)
				VALUES (:id, app_tenant(), :grupo, :codigo, :ativo, 1)""")
			.param("id", id)
			.param("grupo", grupo)
			.param("codigo", codigo)
			.param("ativo", ativo)
			.update();
	}

	public void atualizar(UUID id, UUID grupo, boolean ativo, int versaoAtual) {
		jdbc.sql("""
				UPDATE servico SET grupo_id = :grupo, ativo = :ativo, versao_atual = :versao, atualizado_em = now()
				 WHERE id = :id""")
			.param("id", id)
			.param("grupo", grupo)
			.param("ativo", ativo)
			.param("versao", versaoAtual)
			.update();
	}

	public void inserirVersao(UUID servico, int versao, DefinicaoServico d, UUID autor) {
		var id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO servico_versao (id, tenant_id, servico_id, versao, nome, descricao, unidade_medida, alvo,
				       tempo_padrao_min, tempo_por_unidade, prazo_emergencial_horas, prazo_alta_dias, prazo_media_dias,
				       prazo_baixa_dias, tipo_veiculo_id, habilidade_id, conferido_pela_carga, criada_por)
				VALUES (:id, app_tenant(), :servico, :versao, :nome, :descricao, :unidade, :alvo, :tempo, :porUnidade,
				       :emergencial, :alta, :media, :baixa, :veiculo, :habilidade, :carga, :autor)""")
			.param("id", id)
			.param("servico", servico)
			.param("versao", versao)
			.param("nome", d.nome())
			.param("descricao", d.descricao())
			.param("unidade", d.unidadeMedida().name())
			.param("alvo", d.alvo().name())
			.param("tempo", d.tempoPadraoMin())
			.param("porUnidade", d.tempoPorUnidade())
			.param("emergencial", d.prazos().emergencialHoras())
			.param("alta", d.prazos().altaDias())
			.param("media", d.prazos().mediaDias())
			.param("baixa", d.prazos().baixaDias())
			.param("veiculo", d.tipoVeiculoId())
			.param("habilidade", d.habilidadeId())
			.param("carga", d.conferidoPelaCarga())
			.param("autor", autor)
			.update();
		var ordem = 0;
		for (var p : d.parametros()) {
			jdbc.sql(
					"""
							INSERT INTO parametro_servico (id, tenant_id, servico_versao_id, chave, rotulo, ajuda, tipo, obrigatorio,
							       minimo, maximo, unidade, opcoes, condicao, ordem)
							VALUES (:id, app_tenant(), :versao, :chave, :rotulo, :ajuda, :tipo, :obrigatorio, :minimo, :maximo,
							       :unidade, CAST(:opcoes AS jsonb), CAST(:condicao AS jsonb), :ordem)""")
				.param("id", UUID.randomUUID())
				.param("versao", id)
				.param("chave", p.chave())
				.param("rotulo", p.rotulo())
				.param("ajuda", p.ajuda())
				.param("tipo", p.tipo().name())
				.param("obrigatorio", p.obrigatorio())
				.param("minimo", p.minimo())
				.param("maximo", p.maximo())
				.param("unidade", p.unidade())
				.param("opcoes", p.tipo().temOpcoes() ? json.writeValueAsString(p.opcoes()) : null)
				.param("condicao", p.condicao() == null ? null : json.writeValueAsString(p.condicao()))
				.param("ordem", ++ordem)
				.update();
		}
	}

	/** Todas as versões do serviço, da mais nova para a mais antiga. */
	public List<VersaoServico> versoes(UUID servico) {
		var linhas = jdbc.sql("SELECT v.criada_em, u.nome AS autor, " + VERSAO + """

				 FROM servico_versao v
				 LEFT JOIN usuario u ON u.id = v.criada_por
				WHERE v.servico_id = :servico
				ORDER BY v.versao DESC""")
			.param("servico", servico)
			.query((r, n) -> new LinhaVersao(r.getTimestamp("criada_em").toInstant(), r.getString("autor"), versao(r)))
			.list();
		var parametros = parametros(linhas.stream().map(l -> l.versao().id()).toList());
		return linhas.stream()
			.map(l -> new VersaoServico(l.versao().numero(), l.criadaEm(), l.autor(),
					l.versao().definicao(parametros.getOrDefault(l.versao().id(), List.of()))))
			.toList();
	}

	public boolean grupoAtivo(UUID grupo) {
		return jdbc.sql("SELECT count(*) FROM grupo_servico WHERE id = :id AND ativo")
			.param("id", grupo)
			.query(Long.class)
			.single() > 0;
	}

	private List<Servico> comParametros(List<LinhaServico> linhas) {
		var parametros = parametros(linhas.stream().map(l -> l.versao().id()).toList());
		return linhas.stream()
			.map(l -> new Servico(l.id(), l.grupoId(), l.codigo(), l.ativo(), l.versao().numero(), l.atualizadoEm(),
					l.versao().definicao(parametros.getOrDefault(l.versao().id(), List.of()))))
			.toList();
	}

	/** Parâmetros de cada versão, na ordem. */
	public Map<UUID, List<Parametro>> parametros(Collection<UUID> versoes) {
		var porVersao = new HashMap<UUID, List<Parametro>>();
		if (versoes.isEmpty()) {
			return porVersao;
		}
		jdbc.sql(
				"""
						SELECT servico_versao_id, chave, rotulo, ajuda, tipo, obrigatorio, minimo, maximo, unidade, opcoes, condicao
						  FROM parametro_servico WHERE servico_versao_id IN (:versoes) ORDER BY servico_versao_id, ordem""")
			.param("versoes", versoes)
			.query((r, n) -> {
				var tipo = TipoParametro.valueOf(r.getString("tipo"));
				var opcoes = r.getString("opcoes");
				var condicao = r.getString("condicao");
				var parametro = new Parametro(r.getString("chave"), r.getString("rotulo"), r.getString("ajuda"), tipo,
						r.getBoolean("obrigatorio"), Parametro.limpo(r.getBigDecimal("minimo")),
						Parametro.limpo(r.getBigDecimal("maximo")), r.getString("unidade"),
						opcoes == null ? List.of() : json.readValue(opcoes, new TypeReference<List<String>>() {
						}), condicao == null ? null : json.readValue(condicao, Condicao.class));
				porVersao.computeIfAbsent(r.getObject("servico_versao_id", UUID.class), v -> new ArrayList<>())
					.add(parametro);
				return parametro;
			})
			.list();
		return porVersao;
	}

	private static LinhaServico servico(ResultSet r, int n) throws SQLException {
		return new LinhaServico(r.getObject("id", UUID.class), r.getObject("grupo_id", UUID.class),
				r.getString("codigo"), r.getBoolean("ativo"), r.getTimestamp("atualizado_em").toInstant(), versao(r));
	}

	private static VersaoLida versao(ResultSet r) throws SQLException {
		return new VersaoLida(r.getObject("versao_id", UUID.class), r.getInt("versao"), r.getString("nome"),
				r.getString("descricao"), UnidadeMedida.valueOf(r.getString("unidade_medida")),
				AlvoServico.valueOf(r.getString("alvo")),
				Objects.requireNonNull(Parametro.limpo(r.getBigDecimal("tempo_padrao_min"))),
				r.getBoolean("tempo_por_unidade"),
				new Prazos(r.getInt("prazo_emergencial_horas"), r.getInt("prazo_alta_dias"),
						r.getInt("prazo_media_dias"), r.getInt("prazo_baixa_dias")),
				r.getObject("tipo_veiculo_id", UUID.class), r.getObject("habilidade_id", UUID.class),
				r.getBoolean("conferido_pela_carga"));
	}

	private record LinhaServico(UUID id, UUID grupoId, String codigo, boolean ativo, Instant atualizadoEm,
			VersaoLida versao) {
	}

	private record LinhaVersao(Instant criadaEm, @Nullable String autor, VersaoLida versao) {
	}

	private record VersaoLida(UUID id, int numero, String nome, @Nullable String descricao, UnidadeMedida unidade,
			AlvoServico alvo, BigDecimal tempo, boolean porUnidade, Prazos prazos, @Nullable UUID veiculo,
			@Nullable UUID habilidade, boolean carga) {

		DefinicaoServico definicao(List<Parametro> parametros) {
			return new DefinicaoServico(nome, descricao, unidade, alvo, tempo, porUnidade, prazos, veiculo, habilidade,
					carga, parametros);
		}
	}

}
