package dev.aurora.contratos.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.contratos.model.DadosItem;
import dev.aurora.contratos.model.ItemContratual;
import dev.aurora.contratos.model.StatusItem;
import dev.aurora.contratos.model.TipoItem;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Itens contratuais (o RLS segue o contrato visível). */
@Repository
public class ItemRepository {

	private static final String SELECAO = """
			SELECT i.id, i.contrato_id, i.documento_id, i.tipo, i.titulo, i.resumo, i.clausula, i.pagina, i.trecho,
			       i.valor_referencia, i.unidade_medida, i.data_limite, i.periodicidade, i.unidade_id, i.valor_alerta,
			       i.vigencia_inicio, un.nome AS unidade_nome, i.status, i.origem, i.modelo_ia, i.confianca,
			       i.proposta_ia::text AS proposta_ia, i.proposto_em, rev.nome AS revisado_por, i.revisado_em,
			       i.motivo_revisao, i.substituido_por
			  FROM item_contratual i
			  LEFT JOIN unidade un ON un.id = i.unidade_id
			  LEFT JOIN usuario rev ON rev.id = i.revisado_por""";

	private final JdbcClient jdbc;

	ItemRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Itens do contrato na ordem de leitura do documento. Propostas trocadas por
	 * reprocessamento ficam de fora (nunca valeram); itens substituídos por aditivo ficam
	 * (são histórico do contrato).
	 */
	public List<ItemContratual> listar(UUID contrato, @Nullable StatusItem status) {
		return jdbc.sql(SELECAO + "\n" + """
				WHERE i.contrato_id = :contrato AND (i.status <> 'SUBSTITUIDO' OR i.substituido_por IS NOT NULL)
				  AND (CAST(:status AS text) IS NULL OR i.status = CAST(:status AS text))
				ORDER BY i.documento_id, i.pagina, i.clausula NULLS LAST, i.proposto_em""")
			.param("contrato", contrato)
			.param("status", status == null ? null : status.name())
			.query((r, n) -> item(r))
			.list();
	}

	public Optional<ItemContratual> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE i.id = ?").param(id).query((r, n) -> item(r)).optional();
	}

	public Optional<ItemContratual> travar(UUID id) {
		jdbc.sql("SELECT id FROM item_contratual WHERE id = ? FOR UPDATE").param(id).query(UUID.class).optional();
		return buscar(id);
	}

	public void inserir(UUID id, UUID contrato, UUID documento, DadosItem d, String origem, @Nullable String modelo,
			@Nullable String versaoPrompt, java.math.@Nullable BigDecimal confianca, @Nullable String propostaJson) {
		jdbc.sql(
				"""
						INSERT INTO item_contratual (id, tenant_id, contrato_id, documento_id, tipo, titulo, resumo, clausula,
						                             pagina, trecho, valor_referencia, unidade_medida, data_limite,
						                             periodicidade, unidade_id, valor_alerta, vigencia_inicio, status, origem,
						                             modelo_ia, versao_prompt, confianca, proposta_ia)
						VALUES (?, app_tenant(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PROPOSTO', ?, ?, ?, ?, ?::jsonb)""")
			.params(id, contrato, documento, d.tipo().name(), d.titulo(), d.resumo(), d.clausula(), d.pagina(),
					d.trecho(), d.valorReferencia(), d.unidadeMedida(), data(d.dataLimite()), d.periodicidade(),
					d.unidadeId(), d.valorAlerta(), data(d.vigenciaInicio()), origem, modelo, versaoPrompt, confianca,
					propostaJson)
			.update();
	}

	public void atualizar(UUID id, DadosItem d) {
		jdbc.sql("""
				UPDATE item_contratual
				   SET tipo = ?, titulo = ?, resumo = ?, clausula = ?, pagina = ?, trecho = ?, valor_referencia = ?,
				       unidade_medida = ?, data_limite = ?, periodicidade = ?, unidade_id = ?, valor_alerta = ?,
				       vigencia_inicio = ?
				 WHERE id = ? AND status = 'PROPOSTO'""")
			.params(d.tipo().name(), d.titulo(), d.resumo(), d.clausula(), d.pagina(), d.trecho(), d.valorReferencia(),
					d.unidadeMedida(), data(d.dataLimite()), d.periodicidade(), d.unidadeId(), d.valorAlerta(),
					data(d.vigenciaInicio()), id)
			.update();
	}

	public void revisar(UUID id, StatusItem status, @Nullable UUID revisor, @Nullable String motivo) {
		jdbc.sql("""
				UPDATE item_contratual SET status = ?, revisado_por = ?, revisado_em = now(), motivo_revisao = ?
				 WHERE id = ? AND status = 'PROPOSTO'""").params(status.name(), revisor, motivo, id).update();
	}

	/** Um novo processamento troca as propostas ainda não revisadas do documento. */
	/**
	 * A5: item aprovado cujo efeito um aditivo substituiu; o histórico aponta para o item
	 * novo.
	 */
	public int marcarSubstituido(UUID item, UUID novo) {
		return jdbc.sql("""
				UPDATE item_contratual SET status = 'SUBSTITUIDO', substituido_por = :novo
				 WHERE id = :item AND status = 'APROVADO'""").param("novo", novo).param("item", item).update();
	}

	public int substituirPropostos(UUID documento) {
		return jdbc
			.sql("UPDATE item_contratual SET status = 'SUBSTITUIDO' WHERE documento_id = ? AND status = 'PROPOSTO'")
			.param(documento)
			.update();
	}

	private static @Nullable Date data(@Nullable LocalDate data) {
		return data == null ? null : Date.valueOf(data);
	}

	private static java.time.@Nullable Instant instante(@Nullable Timestamp t) {
		return t == null ? null : t.toInstant();
	}

	private static ItemContratual item(ResultSet r) throws SQLException {
		var dados = new DadosItem(TipoItem.valueOf(r.getString("tipo")), r.getString("titulo"), r.getString("resumo"),
				r.getString("clausula"), r.getInt("pagina"), r.getString("trecho"), r.getBigDecimal("valor_referencia"),
				r.getString("unidade_medida"), r.getObject("data_limite", LocalDate.class),
				r.getString("periodicidade"), r.getObject("unidade_id", UUID.class), r.getBigDecimal("valor_alerta"),
				r.getObject("vigencia_inicio", LocalDate.class));
		return new ItemContratual(r.getObject("id", UUID.class), r.getObject("contrato_id", UUID.class),
				r.getObject("documento_id", UUID.class), dados, r.getString("unidade_nome"),
				StatusItem.valueOf(r.getString("status")), r.getString("origem"), r.getString("modelo_ia"),
				r.getBigDecimal("confianca"), r.getString("proposta_ia"), r.getTimestamp("proposto_em").toInstant(),
				r.getString("revisado_por"), instante(r.getTimestamp("revisado_em")), r.getString("motivo_revisao"),
				r.getObject("substituido_por", UUID.class));
	}

}
