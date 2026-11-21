package dev.aurora.campanhas.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.aurora.campanhas.model.AvisoDeChegada;
import dev.aurora.campanhas.model.OrdemParaAviso;
import dev.aurora.campanhas.model.ResumoDeContatos;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Telefones dos imóveis (cifrados) e os avisos de chegada (ADR 0052). O telefone nunca
 * sai daqui em claro: quem decifra é o serviço, só para o envio.
 */
@Repository
public class ContatoRepository {

	private final JdbcClient jdbc;

	ContatoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Matrículas com endereço importado e visíveis no escopo (só elas recebem contato).
	 */
	public Set<String> comEndereco(Collection<String> matriculas) {
		if (matriculas.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("""
				SELECT codigo_origem FROM imovel_endereco WHERE codigo_origem = ANY (CAST(:m AS text[]))""")
			.param("m", matriculas.toArray(String[]::new))
			.query(String.class)
			.list());
	}

	public void gravar(String matricula, String telefoneCifrado, String finalTelefone, UUID usuario) {
		jdbc.sql("""
				INSERT INTO imovel_contato (tenant_id, codigo_origem, telefone_cifrado, final_telefone, atualizado_por)
				VALUES (app_tenant(), :m, :t, :f, :u)
				ON CONFLICT (tenant_id, codigo_origem) DO UPDATE
				   SET telefone_cifrado = EXCLUDED.telefone_cifrado, final_telefone = EXCLUDED.final_telefone,
				       atualizado_em = now(), atualizado_por = EXCLUDED.atualizado_por""")
			.param("m", matricula)
			.param("t", telefoneCifrado)
			.param("f", finalTelefone)
			.param("u", usuario)
			.update();
	}

	/**
	 * @return true se havia contato
	 */
	public boolean remover(String matricula) {
		return jdbc.sql("DELETE FROM imovel_contato WHERE codigo_origem = :m").param("m", matricula).update() == 1;
	}

	/** Quantos imóveis do escopo têm telefone e quando foi a última importação. */
	public ResumoDeContatos resumo() {
		return jdbc.sql("SELECT count(*) AS total, max(atualizado_em) AS ultima FROM imovel_contato")
			.query((l, n) -> new ResumoDeContatos(l.getLong("total"),
					l.getTimestamp("ultima") == null ? null : l.getTimestamp("ultima").toInstant()))
			.single();
	}

	public Optional<OrdemParaAviso> ordem(UUID os) {
		return jdbc.sql("""
				SELECT o.id, o.status, o.alvo, o.equipe_id, v.nome AS servico, c.telefone_cifrado, c.final_telefone
				  FROM ordem_servico o
				  JOIN servico_versao v ON v.id = o.servico_versao_id
				  LEFT JOIN imovel_contato c
				         ON o.alvo = 'IMOVEL' AND c.tenant_id = o.tenant_id AND c.codigo_origem = o.codigo_origem
				 WHERE o.id = :os""")
			.param("os", os)
			.query((l, n) -> new OrdemParaAviso(l.getObject("id", UUID.class), l.getString("status"),
					l.getString("alvo"), l.getObject("equipe_id", UUID.class), l.getString("servico"),
					l.getString("telefone_cifrado"), l.getString("final_telefone")))
			.optional();
	}

	/** Situação do aviso de cada OS do pacote: tem contato e, se já avisou, quando. */
	public record SituacaoDoAviso(boolean temContato, @Nullable Instant avisadoEm) {
	}

	public Map<UUID, SituacaoDoAviso> situacoes(Collection<UUID> ordens) {
		var mapa = new HashMap<UUID, SituacaoDoAviso>();
		if (ordens.isEmpty()) {
			return mapa;
		}
		jdbc.sql("""
				SELECT o.id, c.codigo_origem IS NOT NULL AS tem_contato,
				       (SELECT max(a.ocorrido_em) FROM aviso_de_chegada a
				         WHERE a.os_id = o.id AND a.situacao <> 'FALHOU') AS avisado_em
				  FROM ordem_servico o
				  LEFT JOIN imovel_contato c
				         ON o.alvo = 'IMOVEL' AND c.tenant_id = o.tenant_id AND c.codigo_origem = o.codigo_origem
				 WHERE o.id = ANY (CAST(:ids AS uuid[]))""")
			.param("ids", ordens.toArray(UUID[]::new))
			.query((l, n) -> mapa.put(l.getObject("id", UUID.class),
					new SituacaoDoAviso(l.getBoolean("tem_contato"),
							l.getTimestamp("avisado_em") == null ? null : l.getTimestamp("avisado_em").toInstant())))
			.list();
		return mapa;
	}

	public Optional<AvisoDeChegada> aviso(UUID id) {
		return jdbc.sql(CAMPOS + " WHERE id = :id").param("id", id).query(ContatoRepository::mapear).optional();
	}

	/** O aviso que já saiu (ou ficou registrado) para a OS, se houver. */
	public Optional<AvisoDeChegada> avisoValidoDaOrdem(UUID os) {
		return jdbc.sql(CAMPOS + " WHERE os_id = :os AND situacao <> 'FALHOU'")
			.param("os", os)
			.query(ContatoRepository::mapear)
			.optional();
	}

	public record NovoAviso(UUID id, UUID os, UUID equipe, UUID usuario, UUID dispositivo, int minutos,
			String finalTelefone, String situacao, String provedor, @Nullable String erro, Instant ocorridoEm) {
	}

	public void registrar(NovoAviso a) {
		jdbc.sql("""
				INSERT INTO aviso_de_chegada (id, tenant_id, os_id, equipe_id, usuario_id, dispositivo_id, minutos,
				                              final_telefone, situacao, provedor, erro, ocorrido_em)
				VALUES (:id, app_tenant(), :os, :equipe, :usuario, :dispositivo, :minutos, :final, :situacao,
				        :provedor, :erro, :ocorrido)""")
			.param("id", a.id())
			.param("os", a.os())
			.param("equipe", a.equipe())
			.param("usuario", a.usuario())
			.param("dispositivo", a.dispositivo())
			.param("minutos", a.minutos())
			.param("final", a.finalTelefone())
			.param("situacao", a.situacao())
			.param("provedor", a.provedor())
			.param("erro", a.erro())
			.param("ocorrido", Timestamp.from(a.ocorridoEm()))
			.update();
	}

	private static final String CAMPOS = "SELECT id, os_id, minutos, situacao, ocorrido_em, criado_em FROM aviso_de_chegada";

	private static AvisoDeChegada mapear(java.sql.ResultSet l, int n) throws java.sql.SQLException {
		return new AvisoDeChegada(l.getObject("id", UUID.class), l.getObject("os_id", UUID.class), l.getInt("minutos"),
				l.getString("situacao"), l.getTimestamp("ocorrido_em").toInstant(),
				l.getTimestamp("criado_em").toInstant());
	}

}
