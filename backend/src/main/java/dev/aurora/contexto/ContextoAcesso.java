package dev.aurora.contexto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.jspecify.annotations.Nullable;

/**
 * Quem está agindo e onde: tenant, usuário, onde cada permissão vale (caminhos ltree das
 * unidades onde foi concedida) e o escopo efetivo da operação. Resolvido no servidor a
 * cada requisição; nunca vem do corpo da requisição nem do texto enviado à IA.
 *
 * <p>
 * O escopo efetivo começa como a união de todas as concessões e é restringido às unidades
 * da permissão que autoriza a operação ({@link #restritoA}, aplicado por
 * {@link ExigePermissao}). É ele que vai para o banco ({@code app_escopos()}).
 */
public record ContextoAcesso(UUID tenantId, @Nullable UUID usuarioId, Map<String, List<String>> concessoes,
		List<String> escopos) {

	private static final ThreadLocal<ContextoAcesso> ATUAL = new ThreadLocal<>();

	public ContextoAcesso {
		Objects.requireNonNull(tenantId, "tenantId");
		var copia = new HashMap<String, List<String>>();
		concessoes.forEach((permissao, caminhos) -> copia.put(permissao, List.copyOf(caminhos)));
		concessoes = Map.copyOf(copia);
		escopos = List.copyOf(escopos);
	}

	/**
	 * Todas as permissões valendo em todos os escopos (tarefas internas e testes).
	 */
	public ContextoAcesso(UUID tenantId, @Nullable UUID usuarioId, Set<String> permissoes, List<String> escopos) {
		this(tenantId, usuarioId, todasEm(permissoes, escopos), escopos);
	}

	/**
	 * Contexto só com o tenant, usado enquanto o usuário ainda está sendo identificado.
	 */
	public static ContextoAcesso somenteTenant(UUID tenantId) {
		return new ContextoAcesso(tenantId, null, Map.of(), List.of());
	}

	/** Permissões que a pessoa tem em alguma unidade. */
	public Set<String> permissoes() {
		return concessoes.keySet();
	}

	public boolean pode(String permissao) {
		return concessoes.containsKey(permissao);
	}

	/**
	 * Unidades (caminhos ltree) onde a permissão foi concedida; vale nas descendentes.
	 */
	public List<String> escoposDe(String permissao) {
		return concessoes.getOrDefault(permissao, List.of());
	}

	/**
	 * A permissão vale na unidade de caminho informado (concedida nela ou numa
	 * ancestral).
	 */
	public boolean podeEm(String permissao, String caminhoUnidade) {
		return escoposDe(permissao).stream().anyMatch(p -> dentroDe(caminhoUnidade, p));
	}

	/**
	 * Mesmo contexto, com o escopo efetivo reduzido às unidades onde a permissão vale.
	 */
	public ContextoAcesso restritoA(String permissao) {
		return new ContextoAcesso(tenantId, usuarioId, concessoes, escoposDe(permissao));
	}

	/**
	 * Escopo efetivo onde todas as permissões valem ao mesmo tempo (interseção das
	 * subárvores): ex. o assistente lê indicadores só onde a pessoa pode usar o
	 * assistente <em>e</em> ler indicadores.
	 */
	public ContextoAcesso restritoATodas(String... permissoes) {
		List<String> comum = null;
		for (var permissao : permissoes) {
			var caminhos = escoposDe(permissao);
			if (comum == null) {
				comum = caminhos;
				continue;
			}
			var intersecao = new ArrayList<String>();
			for (var a : comum) {
				for (var b : caminhos) {
					if (dentroDe(a, b)) {
						intersecao.add(a);
					}
					else if (dentroDe(b, a)) {
						intersecao.add(b);
					}
				}
			}
			comum = intersecao.stream().distinct().toList();
		}
		return new ContextoAcesso(tenantId, usuarioId, concessoes, comum == null ? List.of() : comum);
	}

	/**
	 * Escopo inclui a unidade raiz do tenant (caminho ltree de um nível): atua sobre o
	 * tenant inteiro.
	 */
	public boolean abrangeTenantInteiro() {
		return escopos.stream().anyMatch(caminho -> caminho.indexOf('.') < 0);
	}

	public static Optional<ContextoAcesso> atual() {
		return Optional.ofNullable(ATUAL.get());
	}

	public static ContextoAcesso exigir() {
		return atual().orElseThrow(() -> new IllegalStateException("contexto de acesso não definido"));
	}

	/**
	 * Executa a ação dentro do contexto informado (ex.: tarefas agendadas por tenant).
	 */
	public static <T> T executar(ContextoAcesso contexto, Callable<T> acao) throws Exception {
		var anterior = trocar(contexto);
		try {
			return acao.call();
		}
		finally {
			trocar(anterior);
		}
	}

	/**
	 * Troca o contexto da thread e devolve o anterior, para quem não consegue envolver a
	 * ação num {@link #executar} (ex.: interceptadores com início e fim separados).
	 */
	public static @Nullable ContextoAcesso trocar(@Nullable ContextoAcesso novo) {
		var anterior = ATUAL.get();
		if (novo == null) {
			ATUAL.remove();
		}
		else {
			ATUAL.set(novo);
		}
		return anterior;
	}

	/** {@code a.b.c} está dentro de {@code a.b} (e de si mesmo). */
	static boolean dentroDe(String caminho, String ancestral) {
		return caminho.equals(ancestral) || caminho.startsWith(ancestral + ".");
	}

	private static Map<String, List<String>> todasEm(Set<String> permissoes, List<String> escopos) {
		var mapa = new HashMap<String, List<String>>();
		permissoes.forEach(p -> mapa.put(p, escopos));
		return mapa;
	}

}
