package dev.aurora.integracoes.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.integracoes.model.RegistroLocalizacao;
import dev.aurora.integracoes.model.ResultadoLocalizacao;
import dev.aurora.integracoes.repository.FonteRepository;
import dev.aurora.integracoes.repository.LocalizacaoRepository;
import dev.aurora.unidades.Unidades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E6: o Agente Aurora cruza matrícula e endereço com o CNEFE/IBGE dentro da rede da
 * concessionária e envia só matrícula, ponto e precisão. Imóvel sem correspondência exata
 * chega com precisão de logradouro ou bairro e aparece como "localização aproximada".
 */
@Service
public class LocalizacaoService {

	private static final Logger LOG = LoggerFactory.getLogger(LocalizacaoService.class);

	private static final Set<String> RECEBEM_IMOVEIS = Set.of("CONCESSAO", "MUNICIPIO", "SETOR_COMERCIAL");

	static final int MAXIMO_DE_REGISTROS = 500_000;

	private final LocalizacaoRepository localizacoes;

	private final FonteRepository fontes;

	private final Unidades unidades;

	private final Auditoria auditoria;

	LocalizacaoService(LocalizacaoRepository localizacoes, FonteRepository fontes, Unidades unidades,
			Auditoria auditoria) {
		this.localizacoes = localizacoes;
		this.fontes = fontes;
		this.unidades = unidades;
		this.auditoria = auditoria;
	}

	@Transactional
	public ResultadoLocalizacao gravar(UUID fonteId, List<RegistroLocalizacao> registros, String credencial) {
		var fonte = fontes.buscar(fonteId).orElseThrow(RecursoNaoEncontradoException::new);
		if (!fonte.ativo()) {
			throw new ConflitoException("A fonte " + fonte.nome() + " está inativa.");
		}
		if (registros.isEmpty()) {
			throw new RegraVioladaException("O envio não tem registros.");
		}
		if (registros.size() > MAXIMO_DE_REGISTROS) {
			throw new RegraVioladaException(
					"O envio passa de " + MAXIMO_DE_REGISTROS + " registros; divida em partes.");
		}
		var validacao = ValidadorLocalizacao.validar(registros, unidadesQueRecebemImoveis());
		if (validacao.validas().isEmpty()) {
			throw new RegraVioladaException("Nenhuma localização válida: " + validacao.rejeicoes().getFirst().motivo());
		}
		var validas = validacao.validas();
		var contagem = localizacoes.gravar(validas.stream().map(ValidadorLocalizacao.Valida::matricula).toList(),
				validas.stream().map(ValidadorLocalizacao.Valida::unidade).toList(),
				validas.stream().map(ValidadorLocalizacao.Valida::longitude).toList(),
				validas.stream().map(ValidadorLocalizacao.Valida::latitude).toList(),
				validas.stream().map(ValidadorLocalizacao.Valida::precisao).toList(),
				ContextoAcesso.exigir().usuarioId());
		var inalterados = validas.size() - contagem.novos() - contagem.atualizados() - contagem.preservados();
		var aproximadas = validas.stream().filter(v -> !"ENDERECO".equals(v.precisao())).count();

		var detalhes = new HashMap<String, Object>();
		detalhes.put("fonte", fonteId.toString());
		detalhes.put("credencial", credencial);
		detalhes.put("lidos", registros.size());
		detalhes.put("novos", contagem.novos());
		detalhes.put("atualizados", contagem.atualizados());
		detalhes.put("preservados", contagem.preservados());
		detalhes.put("aproximadas", aproximadas);
		detalhes.put("rejeitados", validacao.rejeitados());
		auditoria.registrar(TipoEvento.IMOVEIS_GEOCODIFICADOS, "fontes/" + fonteId + "/localizacoes", detalhes);
		LOG.info("Localizações da fonte {}: {} novas, {} atualizadas, {} preservadas, {} rejeitadas", fonteId,
				contagem.novos(), contagem.atualizados(), contagem.preservados(), validacao.rejeitados());
		return new ResultadoLocalizacao(registros.size(), contagem.novos(), contagem.atualizados(), inalterados,
				contagem.preservados(), validacao.rejeitados(), List.copyOf(validacao.rejeicoes()));
	}

	private Map<String, UUID> unidadesQueRecebemImoveis() {
		var mapa = new HashMap<String, UUID>();
		unidades.doEscopo()
			.stream()
			.filter(u -> RECEBEM_IMOVEIS.contains(u.tipo()))
			.forEach(u -> mapa.put(ValidadorCarga.codigo(u.codigo()), u.id()));
		return mapa;
	}

}
