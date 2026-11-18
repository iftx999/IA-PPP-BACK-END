package dev.aurora.servicos.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.servicos.MotivoDaOrdem;
import dev.aurora.servicos.ServicoDaOrdem;
import dev.aurora.servicos.Servicos;
import dev.aurora.servicos.model.Calendario;
import dev.aurora.servicos.model.Catalogo;
import dev.aurora.servicos.model.Codigo;
import dev.aurora.servicos.model.DadosGrupo;
import dev.aurora.servicos.model.DadosHabilidade;
import dev.aurora.servicos.model.DadosMotivo;
import dev.aurora.servicos.model.DadosServico;
import dev.aurora.servicos.model.DadosTipoVeiculo;
import dev.aurora.servicos.model.DefinicaoServico;
import dev.aurora.servicos.model.Grupo;
import dev.aurora.servicos.model.Habilidade;
import dev.aurora.servicos.model.Motivo;
import dev.aurora.servicos.model.Servico;
import dev.aurora.servicos.model.TipoMotivo;
import dev.aurora.servicos.model.TipoVeiculo;
import dev.aurora.servicos.model.VersaoServico;
import dev.aurora.servicos.repository.CatalogoRepository;
import dev.aurora.servicos.repository.CatalogoRepository.Tabela;
import dev.aurora.servicos.repository.FeriadoRepository;
import dev.aurora.servicos.repository.ParaOrdemRepository;
import dev.aurora.servicos.repository.ServicoRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de serviços (J1, J2, RN-20). Alterar a definição de um serviço cria nova
 * versão; nada é apagado (inativar mantém o histórico). Toda mudança é auditada.
 */
@Service
public class CatalogoService implements Servicos {

	private final CatalogoRepository catalogo;

	private final ServicoRepository servicos;

	private final ParaOrdemRepository paraOrdem;

	private final FeriadoRepository feriados;

	private final Auditoria auditoria;

	CatalogoService(CatalogoRepository catalogo, ServicoRepository servicos, ParaOrdemRepository paraOrdem,
			FeriadoRepository feriados, Auditoria auditoria) {
		this.catalogo = catalogo;
		this.servicos = servicos;
		this.paraOrdem = paraOrdem;
		this.feriados = feriados;
		this.auditoria = auditoria;
	}

	@Override
	@Transactional
	public void semearCatalogoInicial() {
		catalogo.semear();
	}

	// Para as OS (ADR 0044)

	@Override
	@Transactional(readOnly = true)
	public List<ServicoDaOrdem> servicosParaOrdem() {
		return paraOrdem.servicosAtivos();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ServicoDaOrdem> servicoParaOrdem(UUID id) {
		return paraOrdem.servico(id);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ServicoDaOrdem> servicoParaOrdem(String codigo) {
		return paraOrdem.servico(codigo);
	}

	@Override
	@Transactional(readOnly = true)
	public List<MotivoDaOrdem> motivosParaOrdem() {
		return paraOrdem.motivosAtivos();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<MotivoDaOrdem> motivo(UUID id) {
		return paraOrdem.motivo(id);
	}

	@Override
	@Transactional(readOnly = true)
	public LocalDate somarDiasUteis(LocalDate inicio, int dias, UUID unidade) {
		// 365 dias úteis (o maior prazo) cabem em menos de dois anos corridos.
		var locais = feriados.daUnidade(unidade, inicio, inicio.plusYears(2));
		return Calendario.somarDiasUteis(inicio, dias, locais::contains);
	}

	@Transactional(readOnly = true)
	public Catalogo completo() {
		return new Catalogo(catalogo.grupos(), servicos.listar(), catalogo.motivos(), catalogo.veiculos(),
				catalogo.habilidades());
	}

	// Serviços

	@Transactional
	public Servico cadastrarServico(DadosServico dados) {
		var codigo = Codigo.normalizar(dados.codigo());
		var id = UUID.randomUUID();
		if (catalogo.codigoEmUso(Tabela.SERVICO, codigo, id, TipoMotivo.PARALISACAO)) {
			throw new ConflitoException("Já existe um serviço com o código " + codigo + ".");
		}
		exigirGrupoAtivo(dados.grupoId());
		var definicao = validar(dados.definicao());
		servicos.inserir(id, dados.grupoId(), codigo, dados.ativo());
		servicos.inserirVersao(id, 1, definicao, usuario());
		auditoria.registrar(TipoEvento.SERVICO_CADASTRADO, "servico/" + id, detalhes(codigo, 1, definicao));
		return servicos.buscar(id).orElseThrow();
	}

	/**
	 * Grupo e situação mudam sem nova versão; a definição só cria versão quando difere da
	 * atual.
	 */
	@Transactional
	public Servico alterarServico(UUID id, DadosServico dados) {
		var versaoAtual = servicos.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		var atual = servicos.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (!dados.grupoId().equals(atual.grupoId())) {
			exigirGrupoAtivo(dados.grupoId());
		}
		var definicao = validar(dados.definicao());
		var novaVersao = !definicao.equals(atual.definicao().normalizada());
		var versao = novaVersao ? versaoAtual + 1 : versaoAtual;
		if (novaVersao) {
			servicos.inserirVersao(id, versao, definicao, usuario());
		}
		servicos.atualizar(id, dados.grupoId(), dados.ativo(), versao);
		var detalhes = detalhes(atual.codigo(), versao, definicao);
		detalhes.put("novaVersao", novaVersao);
		detalhes.put("ativo", dados.ativo());
		auditoria.registrar(TipoEvento.SERVICO_ALTERADO, "servico/" + id, detalhes);
		return servicos.buscar(id).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<VersaoServico> versoes(UUID servico) {
		var versoes = servicos.versoes(servico);
		if (versoes.isEmpty()) {
			throw new RecursoNaoEncontradoException();
		}
		return versoes;
	}

	private DefinicaoServico validar(DefinicaoServico definicao) {
		var normalizada = definicao.normalizada();
		if (normalizada.tipoVeiculoId() != null && !catalogo.veiculoAtivo(normalizada.tipoVeiculoId())) {
			throw new RegraVioladaException("Escolha um tipo de veículo ativo.");
		}
		if (normalizada.habilidadeId() != null && !catalogo.habilidadeAtiva(normalizada.habilidadeId())) {
			throw new RegraVioladaException("Escolha uma habilidade ativa.");
		}
		return normalizada;
	}

	private void exigirGrupoAtivo(UUID grupo) {
		if (!servicos.grupoAtivo(grupo)) {
			throw new RegraVioladaException("Escolha um grupo ativo.");
		}
	}

	// Grupos

	@Transactional
	public Grupo cadastrarGrupo(DadosGrupo dados) {
		var id = UUID.randomUUID();
		var limpos = dados.normalizados();
		exigirCodigoLivre(Tabela.GRUPO, limpos.codigo(), id, null);
		catalogo.inserirGrupo(id, limpos);
		auditoria.registrar(TipoEvento.ITEM_DO_CATALOGO_CADASTRADO, "grupo_servico/" + id,
				item(limpos.codigo(), limpos.nome(), limpos.ativo()));
		return catalogo.grupo(id).orElseThrow();
	}

	@Transactional
	public Grupo alterarGrupo(UUID id, DadosGrupo dados) {
		var limpos = dados.normalizados();
		exigirCodigoLivre(Tabela.GRUPO, limpos.codigo(), id, null);
		if (!limpos.ativo() && catalogo.servicosAtivosNoGrupo(id) > 0) {
			throw new ConflitoException("O grupo tem serviços ativos. Inative-os ou mude-os de grupo antes.");
		}
		if (!catalogo.atualizarGrupo(id, limpos)) {
			throw new RecursoNaoEncontradoException();
		}
		auditoria.registrar(TipoEvento.ITEM_DO_CATALOGO_ALTERADO, "grupo_servico/" + id,
				item(limpos.codigo(), limpos.nome(), limpos.ativo()));
		return catalogo.grupo(id).orElseThrow();
	}

	// Tipos de veículo

	@Transactional
	public TipoVeiculo cadastrarVeiculo(DadosTipoVeiculo dados) {
		var id = UUID.randomUUID();
		var limpos = dados.normalizados();
		exigirCodigoLivre(Tabela.VEICULO, limpos.codigo(), id, null);
		catalogo.inserirVeiculo(id, limpos);
		auditoria.registrar(TipoEvento.ITEM_DO_CATALOGO_CADASTRADO, "tipo_veiculo/" + id,
				item(limpos.codigo(), limpos.nome(), limpos.ativo()));
		return veiculo(id);
	}

	@Transactional
	public TipoVeiculo alterarVeiculo(UUID id, DadosTipoVeiculo dados) {
		var limpos = dados.normalizados();
		exigirCodigoLivre(Tabela.VEICULO, limpos.codigo(), id, null);
		if (!catalogo.atualizarVeiculo(id, limpos)) {
			throw new RecursoNaoEncontradoException();
		}
		auditoria.registrar(TipoEvento.ITEM_DO_CATALOGO_ALTERADO, "tipo_veiculo/" + id,
				item(limpos.codigo(), limpos.nome(), limpos.ativo()));
		return veiculo(id);
	}

	private TipoVeiculo veiculo(UUID id) {
		return catalogo.veiculos().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
	}

	// Habilidades

	@Transactional
	public Habilidade cadastrarHabilidade(DadosHabilidade dados) {
		var id = UUID.randomUUID();
		var limpos = dados.normalizados();
		exigirCodigoLivre(Tabela.HABILIDADE, limpos.codigo(), id, null);
		catalogo.inserirHabilidade(id, limpos);
		auditoria.registrar(TipoEvento.ITEM_DO_CATALOGO_CADASTRADO, "habilidade/" + id,
				item(limpos.codigo(), limpos.nome(), limpos.ativo()));
		return habilidade(id);
	}

	@Transactional
	public Habilidade alterarHabilidade(UUID id, DadosHabilidade dados) {
		var limpos = dados.normalizados();
		exigirCodigoLivre(Tabela.HABILIDADE, limpos.codigo(), id, null);
		if (!catalogo.atualizarHabilidade(id, limpos)) {
			throw new RecursoNaoEncontradoException();
		}
		auditoria.registrar(TipoEvento.ITEM_DO_CATALOGO_ALTERADO, "habilidade/" + id,
				item(limpos.codigo(), limpos.nome(), limpos.ativo()));
		return habilidade(id);
	}

	private Habilidade habilidade(UUID id) {
		return catalogo.habilidades().stream().filter(h -> h.id().equals(id)).findFirst().orElseThrow();
	}

	// Motivos

	@Transactional
	public Motivo cadastrarMotivo(DadosMotivo dados) {
		var id = UUID.randomUUID();
		var limpos = validar(dados, id);
		catalogo.inserirMotivo(id, limpos);
		auditoria.registrar(TipoEvento.ITEM_DO_CATALOGO_CADASTRADO, "motivo/" + id, motivo(limpos));
		return buscarMotivo(id);
	}

	@Transactional
	public Motivo alterarMotivo(UUID id, DadosMotivo dados) {
		var limpos = validar(dados, id);
		if (!catalogo.atualizarMotivo(id, limpos)) {
			throw new RecursoNaoEncontradoException();
		}
		auditoria.registrar(TipoEvento.ITEM_DO_CATALOGO_ALTERADO, "motivo/" + id, motivo(limpos));
		return buscarMotivo(id);
	}

	private DadosMotivo validar(DadosMotivo dados, UUID id) {
		var limpos = dados.normalizados();
		exigirCodigoLivre(Tabela.MOTIVO, limpos.codigo(), id, limpos.tipo());
		if (limpos.servicoId() != null && servicos.buscar(limpos.servicoId()).isEmpty()) {
			throw new RegraVioladaException("Serviço do motivo não encontrado.");
		}
		return limpos;
	}

	private Motivo buscarMotivo(UUID id) {
		return catalogo.motivos().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
	}

	// Apoio

	private void exigirCodigoLivre(Tabela tabela, String codigo, UUID id, @Nullable TipoMotivo tipo) {
		if (catalogo.codigoEmUso(tabela, codigo, id, tipo == null ? TipoMotivo.PARALISACAO : tipo)) {
			throw new ConflitoException("Código " + codigo + " já está em uso.");
		}
	}

	private static UUID usuario() {
		var usuario = ContextoAcesso.exigir().usuarioId();
		if (usuario == null) {
			throw new IllegalStateException("Catálogo alterado fora de uma requisição");
		}
		return usuario;
	}

	private static Map<String, @Nullable Object> detalhes(String codigo, int versao, DefinicaoServico d) {
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("codigo", codigo);
		detalhes.put("versao", versao);
		detalhes.put("nome", d.nome());
		detalhes.put("alvo", d.alvo().name());
		detalhes.put("parametros", d.parametros().size());
		return detalhes;
	}

	private static Map<String, @Nullable Object> item(String codigo, String nome, boolean ativo) {
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("codigo", codigo);
		detalhes.put("nome", nome);
		detalhes.put("ativo", ativo);
		return detalhes;
	}

	private static Map<String, @Nullable Object> motivo(DadosMotivo m) {
		var detalhes = item(m.codigo(), m.nome(), m.ativo());
		detalhes.put("tipo", m.tipo().name());
		detalhes.put("servico", m.servicoId() == null ? null : m.servicoId().toString());
		return detalhes;
	}

}
