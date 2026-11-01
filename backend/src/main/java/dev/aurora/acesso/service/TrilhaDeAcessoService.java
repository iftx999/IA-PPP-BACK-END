package dev.aurora.acesso.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.aurora.acesso.model.EventoDeAcesso;
import dev.aurora.acesso.repository.EventoAcessoImportadoRepository;
import dev.aurora.acesso.repository.TenantRepository;
import dev.aurora.acesso.repository.UsuarioRepository;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.contexto.ContextoAcesso;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Copia os eventos de acesso do provedor de identidade (login, falha, bloqueio por
 * tentativas, logout) para a trilha de auditoria do tenant do contexto (doc 08, 8.7; ADR
 * 0028). Roda numa tarefa agendada; cada evento entra uma vez só.
 */
@Service
public class TrilhaDeAcessoService {

	/** Eventos lidos por execução; o restante fica para a próxima. */
	static final int LIMITE_POR_EXECUCAO = 2000;

	/**
	 * Releitura antes do último evento importado: o provedor pode gravar eventos com
	 * horário um pouco anterior ao do último já lido.
	 */
	private static final Duration MARGEM = Duration.ofMinutes(5);

	/** O provedor guarda 30 dias; o controle de duplicados guarda um pouco mais. */
	private static final Duration GUARDA_DO_CONTROLE = Duration.ofDays(35);

	private static final Pattern IPV4 = Pattern
		.compile("((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)");

	private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f]{0,4}(:[0-9A-Fa-f]{0,4}){2,7}");

	private static final String RECURSO_USUARIO = "usuario/";

	private static final String RECURSO_SEM_CONTA = "acesso/conta-desconhecida";

	private final ProvedorIdentidade identidade;

	private final TenantRepository tenants;

	private final UsuarioRepository usuarios;

	private final EventoAcessoImportadoRepository importados;

	private final Auditoria auditoria;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	TrilhaDeAcessoService(ProvedorIdentidade identidade, TenantRepository tenants, UsuarioRepository usuarios,
			EventoAcessoImportadoRepository importados, Auditoria auditoria, TransactionTemplate transacao,
			Clock relogio) {
		this.identidade = identidade;
		this.tenants = tenants;
		this.usuarios = usuarios;
		this.importados = importados;
		this.auditoria = auditoria;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	/**
	 * Importa no tenant do contexto. A leitura no provedor fica fora da transação; a
	 * gravação na trilha é uma transação só.
	 * @return quantos eventos novos entraram na trilha
	 */
	public int importar() {
		var tenant = ContextoAcesso.exigir().tenantId();
		var emissor = tenants.buscarEmissor(tenant).orElse(null);
		if (emissor == null) {
			return 0;
		}
		var desde = transacao.execute(t -> importados.maisRecente().map(i -> i.minus(MARGEM)).orElse(null));
		var eventos = identidade.eventosDeAcesso(emissor, desde, LIMITE_POR_EXECUCAO);
		var novos = transacao.execute(t -> {
			int gravados = 0;
			for (var evento : eventos) {
				if (importados.marcar(evento.id(), evento.ocorridoEm())) {
					registrar(evento);
					gravados++;
				}
			}
			importados.apagarAntesDe(relogio.instant().minus(GUARDA_DO_CONTROLE));
			return gravados;
		});
		return novos == null ? 0 : novos;
	}

	private void registrar(EventoDeAcesso evento) {
		var usuario = evento.sub() == null ? null : usuarios.buscarIdPorSub(evento.sub()).orElse(null);
		var detalhes = new LinkedHashMap<String, Object>();
		detalhes.put("origem", "provedor-de-identidade");
		colocar(detalhes, "cliente", evento.cliente());
		colocar(detalhes, "provedorCorporativo", evento.provedorCorporativo());
		var tipo = switch (evento.tipo()) {
			case LOGIN -> TipoEvento.LOGIN;
			case LOGOUT -> TipoEvento.LOGOUT;
			case FALHA_DE_LOGIN -> {
				colocar(detalhes, "motivo", evento.motivo());
				yield TipoEvento.LOGIN_FALHOU;
			}
			case BLOQUEIO_TEMPORARIO -> {
				detalhes.put("bloqueio", "TEMPORARIO");
				yield TipoEvento.CONTA_BLOQUEADA;
			}
			case BLOQUEIO_PERMANENTE -> {
				detalhes.put("bloqueio", "PERMANENTE");
				yield TipoEvento.CONTA_BLOQUEADA;
			}
		};
		if (usuario == null && evento.sub() != null) {
			// Conta do provedor que não é usuário da Aurora neste tenant: guarda só o
			// identificador técnico, para investigação.
			detalhes.put("contaExterna", evento.sub());
		}
		auditoria.registrarOcorrido(tipo, usuario, evento.ocorridoEm(), ipValido(evento.ip()), recurso(usuario),
				detalhes);
	}

	private static String recurso(@Nullable UUID usuario) {
		return usuario == null ? RECURSO_SEM_CONTA : RECURSO_USUARIO + usuario;
	}

	private static void colocar(Map<String, Object> detalhes, String chave, @Nullable String valor) {
		if (valor != null && !valor.isBlank()) {
			detalhes.put(chave, valor.length() > 100 ? valor.substring(0, 100) : valor);
		}
	}

	/** O banco guarda o IP como inet: texto que não é IP fica de fora. */
	static @Nullable String ipValido(@Nullable String ip) {
		if (ip == null) {
			return null;
		}
		if (IPV4.matcher(ip).matches()) {
			return ip;
		}
		if (!IPV6.matcher(ip).matches()) {
			return null;
		}
		try {
			// Com ":" o Java só interpreta o texto como endereço IPv6, sem consultar DNS.
			java.net.InetAddress.getByName(ip);
			return ip;
		}
		catch (java.net.UnknownHostException invalido) {
			return null;
		}
	}

}
