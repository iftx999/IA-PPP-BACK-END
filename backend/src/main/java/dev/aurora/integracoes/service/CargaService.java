package dev.aurora.integracoes.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import dev.aurora.adesao.Medicoes;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.integracoes.CargaConcluida;
import dev.aurora.integracoes.model.RegistroCarga;
import dev.aurora.integracoes.model.ResultadoCarga;
import dev.aurora.integracoes.repository.CargaRepository;
import dev.aurora.integracoes.repository.FonteRepository;
import dev.aurora.unidades.Unidades;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carga da situação mensal dos imóveis (H3, RN-01, RN-08), pelo Agente Aurora ou por
 * arquivo. A execução é registrada antes de começar (uma falha também fica no histórico);
 * dados, medições e conclusão são gravados juntos; ao fim, publica
 * {@link CargaConcluida}.
 */
@Service
public class CargaService {

	private static final Logger LOG = LoggerFactory.getLogger(CargaService.class);

	/** Tipos de unidade que recebem imóveis (o grupo e as estações operacionais não). */
	private static final Set<String> RECEBEM_IMOVEIS = Set.of("CONCESSAO", "MUNICIPIO", "SETOR_COMERCIAL");

	static final int MAXIMO_DE_REGISTROS = 500_000;

	private final CargaRepository cargas;

	private final FonteRepository fontes;

	private final Unidades unidades;

	private final Medicoes medicoes;

	private final Auditoria auditoria;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transacao;

	private final JsonMapper json;

	private final Clock relogio;

	private final MeterRegistry metricas;

	CargaService(CargaRepository cargas, FonteRepository fontes, Unidades unidades, Medicoes medicoes,
			Auditoria auditoria, ApplicationEventPublisher eventos, TransactionTemplate transacao, JsonMapper json,
			Clock relogio, MeterRegistry metricas) {
		this.metricas = metricas;
		this.cargas = cargas;
		this.fontes = fontes;
		this.unidades = unidades;
		this.medicoes = medicoes;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.transacao = transacao;
		this.json = json;
		this.relogio = relogio;
	}

	/** Origem da carga, como registrada na execução. */
	public enum Origem {

		AGENTE, ARQUIVO

	}

	/** Carga por arquivo CSV enviado pela tela (exige atuar no tenant inteiro). */
	public ResultadoCarga carregarArquivo(UUID fonte, YearMonth competencia, InputStream arquivo) throws IOException {
		if (!ContextoAcesso.exigir().abrangeTenantInteiro()) {
			throw new AccessDeniedException(
					"Cargas atualizam o cliente inteiro: exigem a permissão na unidade principal.");
		}
		return carregar(fonte, competencia, LeitorCsvCarga.ler(arquivo), Origem.ARQUIVO, null);
	}

	/**
	 * @param detalheAuditoria o que identifica quem enviou além do usuário (ex.: prefixo
	 * da credencial do agente)
	 */
	public ResultadoCarga carregar(UUID fonteId, YearMonth competencia, List<RegistroCarga> registros, Origem origem,
			String detalheAuditoria) {
		var fonte = emTransacao(() -> fontes.buscar(fonteId)).orElseThrow(RecursoNaoEncontradoException::new);
		if (!fonte.ativo()) {
			throw new ConflitoException("A fonte " + fonte.nome() + " está inativa.");
		}
		if (competencia.isAfter(YearMonth.now(relogio))) {
			throw new RegraVioladaException("A competência " + competencia + " ainda não começou.");
		}
		if (registros.isEmpty()) {
			throw new RegraVioladaException("A carga não tem registros.");
		}
		if (registros.size() > MAXIMO_DE_REGISTROS) {
			throw new RegraVioladaException(
					"A carga passa de " + MAXIMO_DE_REGISTROS + " registros; divida em partes.");
		}

		var execucao = UUID.randomUUID();
		var usuario = ContextoAcesso.exigir().usuarioId();
		emTransacao(() -> {
			cargas.iniciar(execucao, fonteId, competencia, origem.name(), usuario, registros.size());
			return null;
		});

		var validacao = ValidadorCarga.validar(registros, unidadesQueRecebemImoveis());
		var rejeicoesJson = json.writeValueAsString(validacao.rejeicoes());
		if (validacao.validos().isEmpty()) {
			falhar(execucao, "Nenhum registro válido.", validacao.rejeitados(), rejeicoesJson);
			throw new RegraVioladaException("Nenhum registro válido. Veja as recusas no histórico de cargas.");
		}

		var cronometro = Timer.start(metricas);
		try {
			var resultado = emTransacao(() -> gravar(fonteId, execucao, competencia, registros.size(), validacao,
					rejeicoesJson, origem, detalheAuditoria));
			cronometro.stop(metricas.timer("aurora.carga.duracao", "origem", origem.name(), "resultado", "sucesso"));
			metricas.counter("aurora.carga.registros", "origem", origem.name(), "resultado", "aceito")
				.increment(validacao.validos().size());
			metricas.counter("aurora.carga.registros", "origem", origem.name(), "resultado", "rejeitado")
				.increment(validacao.rejeitados());
			LOG.info("Carga {} da fonte {} ({}): {} novos, {} atualizados, {} rejeitados, {} medições", execucao,
					fonteId, competencia, resultado.novos(), resultado.atualizados(), resultado.rejeitados(),
					resultado.medicoes());
			return resultado;
		}
		catch (RuntimeException erro) {
			LOG.error("Carga {} falhou", execucao, erro);
			cronometro.stop(metricas.timer("aurora.carga.duracao", "origem", origem.name(), "resultado", "falha"));
			falhar(execucao, "Falha ao gravar a carga. Tente novamente; se persistir, acione o suporte.",
					validacao.rejeitados(), rejeicoesJson);
			throw erro;
		}
	}

	private ResultadoCarga gravar(UUID fonte, UUID execucao, YearMonth competencia, long lidos,
			ValidadorCarga.Resultado validacao, String rejeicoesJson, Origem origem, String detalheAuditoria) {
		var validos = validacao.validos();
		var contagem = cargas.gravar(fonte, execucao, competencia,
				validos.stream().map(ValidadorCarga.Valido::matricula).toList(),
				validos.stream().map(ValidadorCarga.Valido::unidade).toList(),
				validos.stream().map(ValidadorCarga.Valido::situacao).toList());
		var inalterados = validos.size() - contagem.novos() - contagem.atualizados();
		var recalculadas = medicoes.recalcular(competencia);
		cargas.concluir(execucao, contagem.novos() + contagem.atualizados(), contagem.novos(), contagem.atualizados(),
				inalterados, validacao.rejeitados(), rejeicoesJson);
		var detalhes = new HashMap<String, Object>();
		detalhes.put("fonte", fonte.toString());
		detalhes.put("competencia", competencia.toString());
		detalhes.put("origem", origem.name());
		detalhes.put("lidos", lidos);
		detalhes.put("novos", contagem.novos());
		detalhes.put("atualizados", contagem.atualizados());
		detalhes.put("rejeitados", validacao.rejeitados());
		if (detalheAuditoria != null) {
			detalhes.put("credencial", detalheAuditoria);
		}
		auditoria.registrar(TipoEvento.CARGA_CONCLUIDA, "carga/" + execucao, detalhes);
		eventos.publishEvent(new CargaConcluida(ContextoAcesso.exigir().tenantId(), fonte, execucao, competencia));
		return new ResultadoCarga(execucao, competencia, lidos, contagem.novos(), contagem.atualizados(), inalterados,
				validacao.rejeitados(), List.copyOf(validacao.rejeicoes()), recalculadas);
	}

	/** Código (maiúsculo) -> id das unidades ativas do escopo que recebem imóveis. */
	private Map<String, UUID> unidadesQueRecebemImoveis() {
		var mapa = new HashMap<String, UUID>();
		emTransacao(unidades::doEscopo).stream()
			.filter(u -> RECEBEM_IMOVEIS.contains(u.tipo()))
			.forEach(u -> mapa.put(ValidadorCarga.codigo(u.codigo()), u.id()));
		return mapa;
	}

	private void falhar(UUID execucao, String erro, long rejeitados, String rejeicoesJson) {
		emTransacao(() -> {
			cargas.falhar(execucao, erro, rejeitados, rejeicoesJson);
			return null;
		});
	}

	private <T> T emTransacao(Supplier<T> acao) {
		return transacao.execute(status -> acao.get());
	}

}
