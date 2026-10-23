package dev.aurora.contratos.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.contratos.model.Contrato;
import dev.aurora.contratos.model.DadosContrato;
import dev.aurora.contratos.model.Documento;
import dev.aurora.contratos.model.Pagina;
import dev.aurora.contratos.model.SituacaoDocumento;
import dev.aurora.contratos.model.TipoDocumento;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Contratos, documentos, arquivos e páginas. O RLS mostra o contrato a quem tem a unidade
 * dele no escopo (acima ou abaixo) e só deixa gravar abaixo do escopo.
 */
@Repository
public class ContratoRepository {

	private static final String SELECAO_CONTRATO = """
			SELECT c.id, c.numero, c.unidade_id, u.nome AS unidade_nome, c.objeto, c.poder_concedente, c.regulador,
			       c.data_assinatura, c.vigencia_fim,
			       (SELECT count(*) FROM documento_contrato d WHERE d.contrato_id = c.id) AS documentos,
			       (SELECT count(*) FROM item_contratual i WHERE i.contrato_id = c.id AND i.status = 'PROPOSTO')
			           AS itens_pendentes,
			       (SELECT count(*) FROM item_contratual i WHERE i.contrato_id = c.id AND i.status = 'APROVADO')
			           AS itens_aprovados
			  FROM contrato c JOIN unidade u ON u.id = c.unidade_id""";

	private static final String SELECAO_DOCUMENTO = """
			SELECT id, contrato_id, tipo, titulo, nome_arquivo, sha256, tamanho_bytes, paginas, status, erro, modelo_ia,
			       itens_propostos, itens_descartados, criado_em, processamento_iniciado_em, texto_por_ocr
			  FROM documento_contrato""";

	private final JdbcClient jdbc;

	ContratoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Contrato> listar() {
		return jdbc.sql(SELECAO_CONTRATO + " ORDER BY c.numero").query((r, n) -> contrato(r)).list();
	}

	public Optional<Contrato> buscar(UUID id) {
		return jdbc.sql(SELECAO_CONTRATO + " WHERE c.id = ?").param(id).query((r, n) -> contrato(r)).optional();
	}

	public void inserir(UUID id, DadosContrato dados) {
		jdbc.sql("""
				INSERT INTO contrato (id, tenant_id, unidade_id, numero, objeto, poder_concedente, regulador,
				                      data_assinatura, vigencia_fim)
				VALUES (?, app_tenant(), ?, ?, ?, ?, ?, ?, ?)""")
			.params(id, dados.unidadeId(), dados.numero(), dados.objeto(), dados.poderConcedente(), dados.regulador(),
					data(dados.dataAssinatura()), data(dados.vigenciaFim()))
			.update();
	}

	public List<Documento> listarDocumentos(UUID contrato) {
		return jdbc.sql(SELECAO_DOCUMENTO + " WHERE contrato_id = ? ORDER BY criado_em")
			.param(contrato)
			.query((r, n) -> documento(r))
			.list();
	}

	public Optional<Documento> buscarDocumento(UUID id) {
		return jdbc.sql(SELECAO_DOCUMENTO + " WHERE id = ?").param(id).query((r, n) -> documento(r)).optional();
	}

	/** Trava o documento (evita dois processamentos ao mesmo tempo). */
	public Optional<Documento> travarDocumento(UUID id) {
		return jdbc.sql(SELECAO_DOCUMENTO + " WHERE id = ? FOR UPDATE")
			.param(id)
			.query((r, n) -> documento(r))
			.optional();
	}

	public void inserirDocumento(UUID id, UUID contrato, TipoDocumento tipo, String titulo, String nomeArquivo,
			String sha256, long tamanho, int paginas, @Nullable UUID enviadoPor, byte[] conteudo) {
		jdbc.sql("""
				INSERT INTO documento_contrato (id, tenant_id, contrato_id, tipo, titulo, nome_arquivo, sha256,
				                                tamanho_bytes, paginas, status, enviado_por)
				VALUES (?, app_tenant(), ?, ?, ?, ?, ?, ?, ?, 'RECEBIDO', ?)""")
			.params(id, contrato, tipo.name(), titulo, nomeArquivo, sha256, tamanho, paginas, enviadoPor)
			.update();
		jdbc.sql("INSERT INTO documento_arquivo (documento_id, tenant_id, conteudo) VALUES (?, app_tenant(), ?)")
			.params(id, conteudo)
			.update();
	}

	public Optional<byte[]> lerArquivo(UUID documento) {
		return jdbc.sql("SELECT conteudo FROM documento_arquivo WHERE documento_id = ?")
			.param(documento)
			.query((r, n) -> r.getBytes(1))
			.optional();
	}

	public void iniciarProcessamento(UUID documento) {
		jdbc.sql("""
				UPDATE documento_contrato
				   SET status = 'RECONHECENDO_TEXTO', erro = NULL, processamento_iniciado_em = now()
				 WHERE id = ?""").param(documento).update();
	}

	public void definirSituacao(UUID documento, SituacaoDocumento situacao, @Nullable String erro,
			@Nullable String modelo) {
		jdbc.sql("UPDATE documento_contrato SET status = ?, erro = ?, modelo_ia = COALESCE(?, modelo_ia) WHERE id = ?")
			.params(situacao.name(), erro, modelo, documento)
			.update();
	}

	public void concluir(UUID documento, int propostos, int descartados, @Nullable String modelo,
			@Nullable String aviso) {
		jdbc.sql("""
				UPDATE documento_contrato
				   SET status = 'PRONTO', itens_propostos = ?, itens_descartados = ?, modelo_ia = ?, erro = ?
				 WHERE id = ?""").params(propostos, descartados, modelo, aviso, documento).update();
	}

	/**
	 * ADR 0021: o texto de alguma página veio do OCR (a tela pede conferência atenta).
	 */
	public void marcarTextoPorOcr(UUID documento) {
		jdbc.sql("UPDATE documento_contrato SET texto_por_ocr = true WHERE id = ?").param(documento).update();
	}

	public void salvarPaginas(UUID documento, Collection<Pagina> paginas) {
		jdbc.sql("DELETE FROM documento_pagina WHERE documento_id = ?").param(documento).update();
		for (var pagina : paginas) {
			jdbc.sql(
					"INSERT INTO documento_pagina (tenant_id, documento_id, pagina, texto) VALUES (app_tenant(), ?, ?, ?)")
				.params(documento, pagina.numero(), pagina.texto())
				.update();
		}
	}

	public List<Pagina> lerPaginas(UUID documento) {
		return jdbc.sql("SELECT pagina, texto FROM documento_pagina WHERE documento_id = ? ORDER BY pagina")
			.param(documento)
			.query((r, n) -> new Pagina(r.getInt("pagina"), r.getString("texto")))
			.list();
	}

	private static @Nullable Date data(@Nullable LocalDate data) {
		return data == null ? null : Date.valueOf(data);
	}

	private static Contrato contrato(ResultSet r) throws SQLException {
		return new Contrato(r.getObject("id", UUID.class), r.getString("numero"), r.getObject("unidade_id", UUID.class),
				r.getString("unidade_nome"), r.getString("objeto"), r.getString("poder_concedente"),
				r.getString("regulador"), r.getObject("data_assinatura", LocalDate.class),
				r.getObject("vigencia_fim", LocalDate.class), r.getLong("documentos"), r.getLong("itens_pendentes"),
				r.getLong("itens_aprovados"));
	}

	private static Documento documento(ResultSet r) throws SQLException {
		var iniciado = r.getTimestamp("processamento_iniciado_em");
		return new Documento(r.getObject("id", UUID.class), r.getObject("contrato_id", UUID.class),
				TipoDocumento.valueOf(r.getString("tipo")), r.getString("titulo"), r.getString("nome_arquivo"),
				r.getString("sha256"), r.getLong("tamanho_bytes"), (Integer) r.getObject("paginas"),
				SituacaoDocumento.valueOf(r.getString("status")), r.getString("erro"), r.getString("modelo_ia"),
				r.getInt("itens_propostos"), r.getInt("itens_descartados"), r.getTimestamp("criado_em").toInstant(),
				iniciado == null ? null : iniciado.toInstant(), r.getBoolean("texto_por_ocr"));
	}

}
