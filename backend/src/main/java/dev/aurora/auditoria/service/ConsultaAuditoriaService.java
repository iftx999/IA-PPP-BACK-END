package dev.aurora.auditoria.service;

import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.auditoria.model.Autor;
import dev.aurora.auditoria.model.FiltroAuditoria;
import dev.aurora.auditoria.model.PaginaAuditoria;
import dev.aurora.auditoria.model.ResultadoVerificacao;
import dev.aurora.auditoria.repository.TrilhaRepository;
import dev.aurora.comum.CursorCifrado;
import dev.aurora.comum.EscritorCsv;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import org.jspecify.annotations.Nullable;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta da trilha de auditoria (S8): filtros, exportação (também auditada) e
 * verificação da cadeia. A trilha é do tenant inteiro, por isso exige a permissão na
 * unidade raiz.
 */
@Service
public class ConsultaAuditoriaService {

	public static final String EXPORTAR = "relatorio:exportar";

	private static final int LIMITE_MAXIMO = 200;

	private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final TrilhaRepository trilha;

	private final AuditoriaService verificacao;

	private final Auditoria auditoria;

	private final CursorCifrado cursores;

	private final Clock relogio;

	ConsultaAuditoriaService(TrilhaRepository trilha, AuditoriaService verificacao, Auditoria auditoria,
			CursorCifrado cursores, Clock relogio) {
		this.trilha = trilha;
		this.verificacao = verificacao;
		this.auditoria = auditoria;
		this.cursores = cursores;
		this.relogio = relogio;
	}

	/** Filtros como chegam da tela: período em dias inteiros, no fuso do negócio. */
	public record Pedido(@Nullable UUID usuarioId, List<String> tipos, @Nullable LocalDate de, @Nullable LocalDate ate,
			@Nullable UUID unidadeId) {
	}

	@Transactional(readOnly = true)
	public PaginaAuditoria consultar(Pedido pedido, Optional<String> cursor, int limite) {
		exigirTenantInteiro();
		var filtro = filtro(pedido);
		int tamanho = Math.clamp(limite, 1, LIMITE_MAXIMO);
		var antes = cursor.flatMap(cursores::decifrar).map(Long::valueOf).orElse(null);
		var eventos = trilha.consultar(filtro, antes, tamanho + 1);
		String proximo = null;
		if (eventos.size() > tamanho) {
			eventos = eventos.subList(0, tamanho);
			proximo = cursores.cifrar(String.valueOf(eventos.getLast().sequencia()));
		}
		return new PaginaAuditoria(List.copyOf(eventos), proximo);
	}

	@Transactional(readOnly = true)
	public List<Autor> autores() {
		exigirTenantInteiro();
		return trilha.listarAutores();
	}

	@Transactional(readOnly = true)
	public ResultadoVerificacao verificar() {
		exigirTenantInteiro();
		return verificacao.verificar();
	}

	/**
	 * CSV com todos os eventos do filtro. A emissão vai para a própria trilha, com os
	 * filtros, a quantidade de linhas e o SHA-256 do arquivo.
	 * @return quantidade de eventos exportados
	 */
	@Transactional
	public long exportarCsv(Pedido pedido, OutputStream destino) {
		var contexto = exigirTenantInteiro();
		if (contexto.escoposDe(EXPORTAR).stream().noneMatch(caminho -> caminho.indexOf('.') < 0)) {
			throw new AccessDeniedException("Exportar a trilha exige permissão de exportação no tenant inteiro.");
		}
		var filtro = filtro(pedido);
		var zona = relogio.getZone();
		var resumo = sha256();
		var linhas = new long[1];
		try (var csv = new EscritorCsv(new DigestOutputStream(destino, resumo))) {
			csv.linha("sequencia", "ocorrido_em", "tipo", "usuario", "email", "recurso", "recurso_nome", "unidade",
					"ip", "detalhes");
			trilha.percorrer(filtro, e -> {
				csv.linha(e.sequencia(), DATA_HORA.format(e.ocorridoEm().atZone(zona)), e.tipo(), e.usuarioNome(),
						e.usuarioEmail(), e.recurso(), e.recursoNome(), e.unidadeNome(), e.ip(), e.detalhes());
				linhas[0]++;
			});
		}
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("formato", "CSV");
		detalhes.put("usuario", pedido.usuarioId() == null ? null : pedido.usuarioId().toString());
		detalhes.put("tipos", pedido.tipos());
		detalhes.put("de", pedido.de() == null ? null : pedido.de().toString());
		detalhes.put("ate", pedido.ate() == null ? null : pedido.ate().toString());
		detalhes.put("unidade", pedido.unidadeId() == null ? null : pedido.unidadeId().toString());
		detalhes.put("linhas", linhas[0]);
		detalhes.put("sha256", HexFormat.of().formatHex(resumo.digest()));
		auditoria.registrar(TipoEvento.RELATORIO_EXPORTADO, "auditoria/eventos", detalhes);
		return linhas[0];
	}

	private FiltroAuditoria filtro(Pedido pedido) {
		if (pedido.de() != null && pedido.ate() != null && pedido.ate().isBefore(pedido.de())) {
			throw new RegraVioladaException("O fim do período não pode ser antes do início.");
		}
		var desconhecidos = pedido.tipos().stream().filter(t -> !tipoValido(t)).toList();
		if (!desconhecidos.isEmpty()) {
			throw new RegraVioladaException("Tipos de evento desconhecidos: " + String.join(", ", desconhecidos));
		}
		ZoneId zona = relogio.getZone();
		return new FiltroAuditoria(pedido.usuarioId(), pedido.tipos(),
				pedido.de() == null ? null : pedido.de().atStartOfDay(zona).toInstant(),
				pedido.ate() == null ? null : pedido.ate().plusDays(1).atStartOfDay(zona).toInstant(),
				pedido.unidadeId());
	}

	private static boolean tipoValido(String tipo) {
		try {
			TipoEvento.valueOf(tipo);
			return true;
		}
		catch (IllegalArgumentException erro) {
			return false;
		}
	}

	private static ContextoAcesso exigirTenantInteiro() {
		var contexto = ContextoAcesso.exigir();
		if (!contexto.abrangeTenantInteiro()) {
			throw new AccessDeniedException("A trilha de auditoria exige a permissão no tenant inteiro.");
		}
		return contexto;
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
