package dev.aurora.contratos.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.contratos.PrazoContratual;
import dev.aurora.contratos.Recorrencia;
import dev.aurora.contratos.model.AgendaDePrazo;
import dev.aurora.contratos.model.AgendaDePrazo.Cumprimento;
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
			       i.motivo_revisao, i.substituido_por, i.primeiro_vencimento, i.recorrencia,
			       c.vigencia_fim AS fim_do_contrato
			  FROM item_contratual i
			  JOIN contrato c ON c.id = i.contrato_id
			  LEFT JOIN unidade un ON un.id = i.unidade_id
			  LEFT JOIN usuario rev ON rev.id = i.revisado_por""";

	private static final String CUMPRIMENTOS = """
			SELECT ic.item_id, ic.vencimento, ic.cumprido_em, u.nome AS cumprido_por
			  FROM item_contratual_cumprimento ic
			  LEFT JOIN usuario u ON u.id = ic.cumprido_por""";

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
			.query((r, n) -> item(r,
					cumprimentos(CUMPRIMENTOS + " JOIN item_contratual i ON i.id = ic.item_id WHERE i.contrato_id = ?",
							contrato)))
			.list();
	}

	public Optional<ItemContratual> buscar(UUID id) {
		var cumprimentos = cumprimentos(CUMPRIMENTOS + " WHERE ic.item_id = ?", id);
		return jdbc.sql(SELECAO + " WHERE i.id = ?").param(id).query((r, n) -> item(r, cumprimentos)).optional();
	}

	private Map<UUID, List<Cumprimento>> cumprimentos(String sql, UUID parametro) {
		var porItem = new HashMap<UUID, List<Cumprimento>>();
		jdbc.sql(sql).param(parametro).query((ResultSet r) -> {
			porItem.computeIfAbsent(r.getObject("item_id", UUID.class), k -> new ArrayList<>())
				.add(new Cumprimento(r.getObject("vencimento", LocalDate.class),
						r.getTimestamp("cumprido_em").toInstant(), r.getString("cumprido_por")));
		});
		return porItem;
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

	/**
	 * A5: item aprovado cujo efeito um aditivo substituiu; o histórico aponta para o item
	 * novo.
	 */
	public int marcarSubstituido(UUID item, UUID novo) {
		return jdbc.sql("""
				UPDATE item_contratual SET status = 'SUBSTITUIDO', substituido_por = :novo
				 WHERE id = :item AND status = 'APROVADO'""").param("novo", novo).param("item", item).update();
	}

	/** Um novo processamento troca as propostas ainda não revisadas do documento. */
	public int substituirPropostos(UUID documento) {
		return jdbc
			.sql("UPDATE item_contratual SET status = 'SUBSTITUIDO' WHERE documento_id = ? AND status = 'PROPOSTO'")
			.param(documento)
			.update();
	}

	/**
	 * Agenda do item aprovado (ADR 0026): primeiro vencimento (nulo = a data limite) e
	 * repetição.
	 * @return se o item era um prazo ou obrigação aprovado
	 */
	public boolean definirAgenda(UUID item, @Nullable LocalDate primeiroVencimento, @Nullable Recorrencia recorrencia) {
		return jdbc.sql("""
				UPDATE item_contratual SET primeiro_vencimento = ?, recorrencia = ?
				 WHERE id = ? AND tipo IN ('PRAZO', 'OBRIGACAO') AND status = 'APROVADO'""")
			.params(data(primeiroVencimento), recorrencia == null ? null : recorrencia.name(), item)
			.update() == 1;
	}

	public void registrarCumprimento(UUID item, LocalDate vencimento, UUID usuario) {
		jdbc.sql("""
				INSERT INTO item_contratual_cumprimento (tenant_id, item_id, vencimento, cumprido_por)
				VALUES (app_tenant(), ?, ?, ?)""").params(item, Date.valueOf(vencimento), usuario).update();
	}

	public void removerCumprimento(UUID item, LocalDate vencimento) {
		jdbc.sql("DELETE FROM item_contratual_cumprimento WHERE item_id = ? AND vencimento = ?")
			.params(item, Date.valueOf(vencimento))
			.update();
	}

	/**
	 * Próximo vencimento de cada prazo ou obrigação aprovado com data, quando cai até a
	 * data informada (vencidos inclusive).
	 */
	public List<PrazoContratual> listarPrazosPendentes(LocalDate ate) {
		var pendentes = new ArrayList<PrazoContratual>();
		jdbc.sql("""
				SELECT c.id AS contrato_id, c.numero, c.vigencia_fim, i.id AS item_id, i.titulo, i.clausula,
				       COALESCE(i.unidade_id, c.unidade_id) AS unidade_id,
				       COALESCE(i.primeiro_vencimento, i.data_limite) AS primeiro, i.recorrencia,
				       (SELECT max(vencimento) FROM item_contratual_cumprimento ic WHERE ic.item_id = i.id) AS ultimo
				  FROM item_contratual i
				  JOIN contrato c ON c.id = i.contrato_id
				 WHERE i.tipo IN ('PRAZO', 'OBRIGACAO') AND i.status = 'APROVADO'
				   AND COALESCE(i.primeiro_vencimento, i.data_limite) IS NOT NULL""").query((ResultSet r) -> {
			var ultimo = r.getObject("ultimo", LocalDate.class);
			var recorrencia = recorrencia(r.getString("recorrencia"));
			var agenda = new AgendaDePrazo(r.getObject("primeiro", LocalDate.class), recorrencia,
					ultimo == null ? List.of() : List.of(new Cumprimento(ultimo, java.time.Instant.EPOCH, null)),
					r.getObject("vigencia_fim", LocalDate.class));
			var proximo = agenda.proximoVencimento();
			if (proximo.isPresent() && !proximo.get().isAfter(ate)) {
				pendentes.add(new PrazoContratual(r.getObject("contrato_id", UUID.class), r.getString("numero"),
						r.getObject("item_id", UUID.class), r.getString("titulo"), r.getString("clausula"),
						r.getObject("unidade_id", UUID.class), proximo.get(), recorrencia));
			}
		});
		pendentes.sort(java.util.Comparator.comparing(PrazoContratual::data));
		return pendentes;
	}

	private static @Nullable Date data(@Nullable LocalDate data) {
		return data == null ? null : Date.valueOf(data);
	}

	private static java.time.@Nullable Instant instante(@Nullable Timestamp t) {
		return t == null ? null : t.toInstant();
	}

	private static @Nullable Recorrencia recorrencia(@Nullable String valor) {
		return valor == null ? null : Recorrencia.valueOf(valor);
	}

	private static ItemContratual item(ResultSet r, Map<UUID, List<Cumprimento>> cumprimentos) throws SQLException {
		var dados = new DadosItem(TipoItem.valueOf(r.getString("tipo")), r.getString("titulo"), r.getString("resumo"),
				r.getString("clausula"), r.getInt("pagina"), r.getString("trecho"), r.getBigDecimal("valor_referencia"),
				r.getString("unidade_medida"), r.getObject("data_limite", LocalDate.class),
				r.getString("periodicidade"), r.getObject("unidade_id", UUID.class), r.getBigDecimal("valor_alerta"),
				r.getObject("vigencia_inicio", LocalDate.class));
		var id = r.getObject("id", UUID.class);
		var status = StatusItem.valueOf(r.getString("status"));
		var primeiro = r.getObject("primeiro_vencimento", LocalDate.class);
		if (primeiro == null) {
			primeiro = dados.dataLimite();
		}
		var agenda = status == StatusItem.APROVADO && ItemContratual.temVencimento(dados.tipo()) && primeiro != null
				? new AgendaDePrazo(primeiro, recorrencia(r.getString("recorrencia")),
						cumprimentos.getOrDefault(id, List.of()), r.getObject("fim_do_contrato", LocalDate.class))
				: null;
		return new ItemContratual(id, r.getObject("contrato_id", UUID.class), r.getObject("documento_id", UUID.class),
				dados, r.getString("unidade_nome"), status, r.getString("origem"), r.getString("modelo_ia"),
				r.getBigDecimal("confianca"), r.getString("proposta_ia"), r.getTimestamp("proposto_em").toInstant(),
				r.getString("revisado_por"), instante(r.getTimestamp("revisado_em")), r.getString("motivo_revisao"),
				r.getObject("substituido_por", UUID.class), agenda);
	}

}
