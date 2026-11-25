package dev.aurora.logradouros.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.Matricula;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.logradouros.model.Cobertura;
import dev.aurora.logradouros.model.DadosLogradouro;
import dev.aurora.logradouros.model.DadosTrecho;
import dev.aurora.logradouros.model.ImovelNoLogradouro;
import dev.aurora.logradouros.model.Logradouro;
import dev.aurora.logradouros.model.Trecho;
import dev.aurora.logradouros.repository.LogradouroRepository;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta e correção de logradouros e trechos (J3). Os imóveis do logradouro mostram a
 * matrícula inteira só a quem tem {@code dados_pessoais:ler} na unidade do imóvel; a
 * exibição fica na trilha (doc 09, 9.7).
 */
@Service
public class LogradouroService {

	static final int TAMANHO_DA_PAGINA = 50;

	static final int MAXIMO_DE_IMOVEIS = 2_000;

	private static final String DADOS_PESSOAIS = "dados_pessoais:ler";

	private final LogradouroRepository logradouros;

	private final Unidades unidades;

	private final Auditoria auditoria;

	LogradouroService(LogradouroRepository logradouros, Unidades unidades, Auditoria auditoria) {
		this.logradouros = logradouros;
		this.unidades = unidades;
		this.auditoria = auditoria;
	}

	public record Pagina(List<Logradouro> itens, long total, int pagina, int tamanho) {
	}

	@Transactional(readOnly = true)
	public Pagina buscar(@Nullable UUID unidade, String termo, int pagina) {
		var caminho = unidade == null ? null : caminho(unidade);
		var limpa = termo.strip();
		var numero = Math.max(0, pagina);
		return new Pagina(logradouros.buscar(caminho, limpa, TAMANHO_DA_PAGINA, numero * TAMANHO_DA_PAGINA),
				logradouros.contar(caminho, limpa), numero, TAMANHO_DA_PAGINA);
	}

	public record Detalhe(Logradouro logradouro, List<Trecho> trechos) {
	}

	@Transactional(readOnly = true)
	public Detalhe detalhe(UUID id) {
		var logradouro = logradouros.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		return new Detalhe(logradouro, logradouros.trechos(id));
	}

	@Transactional
	public List<ImovelNoLogradouro> imoveis(UUID id) {
		logradouros.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		var contexto = ContextoAcesso.exigir();
		var podePorUnidade = new HashMap<UUID, Boolean>();
		var expostos = new int[1];
		var lista = logradouros.imoveis(id, MAXIMO_DE_IMOVEIS).stream().map(i -> {
			boolean pode = podePorUnidade.computeIfAbsent(i.unidadeId(),
					u -> unidades.caminhoNoEscopo(u).map(c -> contexto.podeEm(DADOS_PESSOAIS, c)).orElse(false));
			if (pode) {
				expostos[0]++;
				return i.imovel();
			}
			return i.imovel().comMatricula(Matricula.mascarar(i.imovel().matricula()));
		}).toList();
		if (expostos[0] > 0) {
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("quantidade", expostos[0]);
			detalhes.put("motivo", "imoveis-do-logradouro");
			auditoria.registrar(TipoEvento.ACESSO_DADO_PESSOAL, "logradouro/" + id, detalhes);
		}
		return lista;
	}

	@Transactional(readOnly = true)
	public Cobertura cobertura(UUID unidade) {
		return logradouros.cobertura(caminho(unidade));
	}

	@Transactional
	public Detalhe corrigir(UUID id, DadosLogradouro dados) {
		var limpos = dados.normalizados();
		if (!logradouros.atualizar(id, limpos, usuario())) {
			throw new RecursoNaoEncontradoException();
		}
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("tipo", limpos.tipo());
		detalhes.put("nome", limpos.nome());
		detalhes.put("bairro", limpos.bairro());
		auditoria.registrar(TipoEvento.LOGRADOURO_CORRIGIDO, "logradouro/" + id, detalhes);
		return detalhe(id);
	}

	/**
	 * Corrige a faixa do trecho e escolhe de novo o trecho dos endereços do município.
	 */
	@Transactional
	public Detalhe corrigirTrecho(UUID trecho, DadosTrecho dados) {
		dados.validar();
		var logradouro = logradouros.atualizarTrecho(trecho, dados).orElseThrow(RecursoNaoEncontradoException::new);
		var municipio = logradouros.buscar(logradouro).orElseThrow().unidadeId();
		logradouros.vincularTrechos(municipio, null);
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("numeroInicial", dados.numeroInicial());
		detalhes.put("numeroFinal", dados.numeroFinal());
		detalhes.put("lado", dados.lado().name());
		auditoria.registrar(TipoEvento.TRECHO_CORRIGIDO, "trecho/" + trecho, detalhes);
		return detalhe(logradouro);
	}

	private String caminho(UUID unidade) {
		return unidades.caminhoNoEscopo(unidade)
			.orElseThrow(() -> new RegraVioladaException("Escolha uma unidade do seu escopo."));
	}

	private static UUID usuario() {
		var usuario = ContextoAcesso.exigir().usuarioId();
		if (usuario == null) {
			throw new IllegalStateException("Correção fora de uma requisição");
		}
		return usuario;
	}

}
