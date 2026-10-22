package dev.aurora.integracoes.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.integracoes.FonteAtrasada;
import dev.aurora.integracoes.Fontes;
import dev.aurora.integracoes.model.Credencial;
import dev.aurora.integracoes.model.DadosFonte;
import dev.aurora.integracoes.model.Execucao;
import dev.aurora.integracoes.model.FonteDados;
import dev.aurora.integracoes.repository.FonteRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fontes de dados, histórico de cargas e credenciais do Agente Aurora (H3, S11, RN-08).
 */
@Service
public class IntegracaoService implements Fontes {

	private static final String RECURSO = "fonte/";

	static final Duration ATRASO_MINIMO = Duration.ofHours(1);

	static final Duration ATRASO_MAXIMO = Duration.ofDays(90);

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private static final char[] ALFABETO = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();

	private final FonteRepository fontes;

	private final Auditoria auditoria;

	private final Clock relogio;

	IntegracaoService(FonteRepository fontes, Auditoria auditoria, Clock relogio) {
		this.fontes = fontes;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	/** Chave mostrada uma única vez: "prefixo.segredo". */
	public record CredencialGerada(String prefixo, String chave) {
	}

	@Transactional(readOnly = true)
	public List<FonteDados> listar() {
		return fontes.listar();
	}

	@Override
	@Transactional(readOnly = true)
	public List<FonteAtrasada> atrasadas() {
		var agora = relogio.instant();
		return fontes.listar()
			.stream()
			.filter(f -> f.atrasadaEm(agora))
			.map(f -> new FonteAtrasada(f.id(), f.nome(), f.ultimaCarga(), f.atrasoMaximo()))
			.toList();
	}

	@Transactional
	public UUID criar(DadosFonte pedido) {
		var dados = validar(pedido, null);
		var id = UUID.randomUUID();
		fontes.inserir(id, dados);
		auditoria.registrar(TipoEvento.FONTE_CRIADA, RECURSO + id, Map.of("nome", dados.nome(), "tipo",
				dados.tipo().name(), "atrasoMaximoHoras", dados.atrasoMaximo().toHours()));
		return id;
	}

	@Transactional
	public void alterar(UUID id, DadosFonte pedido) {
		var atual = fontes.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		var dados = validar(pedido, id);
		fontes.atualizar(id, dados);
		auditoria.registrar(TipoEvento.FONTE_ALTERADA, RECURSO + id,
				Map.of("atrasoMaximoHorasAntes", atual.atrasoMaximo().toHours(), "atrasoMaximoHoras",
						dados.atrasoMaximo().toHours(), "ativo", dados.ativo()));
	}

	@Transactional(readOnly = true)
	public List<Execucao> execucoes(UUID fonte, int limite) {
		fontes.buscar(fonte).orElseThrow(RecursoNaoEncontradoException::new);
		return fontes.listarExecucoes(fonte, Math.clamp(limite, 1, 200));
	}

	@Transactional(readOnly = true)
	public List<Credencial> credenciais(UUID fonte) {
		fontes.buscar(fonte).orElseThrow(RecursoNaoEncontradoException::new);
		return fontes.listarCredenciais(fonte);
	}

	/**
	 * Gera uma credencial para o Agente Aurora desta fonte. Guarda só o SHA-256 do
	 * segredo (alta entropia, não precisa de hash lento); a chave inteira só aparece
	 * agora.
	 */
	@Transactional
	public CredencialGerada gerarCredencial(UUID fonte) {
		var dados = fontes.buscar(fonte).orElseThrow(RecursoNaoEncontradoException::new);
		if (!dados.ativo()) {
			throw new ConflitoException("Reative a fonte antes de gerar credenciais.");
		}
		var prefixo = "aur_" + aleatorio(10);
		var segredo = new byte[32];
		ALEATORIO.nextBytes(segredo);
		var textoSegredo = Base64.getUrlEncoder().withoutPadding().encodeToString(segredo);
		fontes.inserirCredencial(prefixo, fonte, sha256(textoSegredo));
		auditoria.registrar(TipoEvento.CREDENCIAL_GERADA, RECURSO + fonte, Map.of("prefixo", prefixo));
		return new CredencialGerada(prefixo, prefixo + "." + textoSegredo);
	}

	@Transactional
	public void revogarCredencial(String prefixo) {
		var fonte = fontes.revogarCredencial(prefixo).orElseThrow(RecursoNaoEncontradoException::new);
		auditoria.registrar(TipoEvento.CREDENCIAL_REVOGADA, RECURSO + fonte, Map.of("prefixo", prefixo));
	}

	private DadosFonte validar(DadosFonte d, UUID exceto) {
		var nome = d.nome().strip();
		if (d.atrasoMaximo().compareTo(ATRASO_MINIMO) < 0 || d.atrasoMaximo().compareTo(ATRASO_MAXIMO) > 0) {
			throw new RegraVioladaException("O atraso máximo vai de 1 hora a 90 dias.");
		}
		if (fontes.nomeEmUso(nome, exceto)) {
			throw new ConflitoException("Já existe uma fonte chamada " + nome + ".");
		}
		return new DadosFonte(nome, d.tipo(), limpo(d.sistemaOrigem()), limpo(d.frequencia()), d.atrasoMaximo(),
				d.ativo());
	}

	private static String limpo(String texto) {
		return texto == null || texto.isBlank() ? null : texto.strip();
	}

	private static String aleatorio(int tamanho) {
		var texto = new StringBuilder();
		for (int i = 0; i < tamanho; i++) {
			texto.append(ALFABETO[ALEATORIO.nextInt(ALFABETO.length)]);
		}
		return texto.toString();
	}

	static byte[] sha256(String texto) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException erro) {
			throw new IllegalStateException(erro);
		}
	}

}
