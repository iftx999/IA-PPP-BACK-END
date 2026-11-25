package dev.aurora.campanhas.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.integracao.EnvioDeSms;
import dev.aurora.campanhas.model.AvisoDeChegada;
import dev.aurora.campanhas.repository.ContatoRepository;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Estou chegando" (ADR 0052): o agente avisa o morador por SMS que a equipe está a
 * caminho. Só OS programada de imóvel, da equipe em jornada, com telefone importado; um
 * aviso por OS. O texto não leva nome de ninguém; o telefone é decifrado só aqui e não
 * vai para o app, o log nem a auditoria.
 */
@Service
public class AvisoDeChegadaService {

	private static final Logger LOG = LoggerFactory.getLogger(AvisoDeChegadaService.class);

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	private final ContatoRepository contatos;

	private final JornadaService jornadas;

	private final CifraDeContatos cifra;

	private final EnvioDeSms sms;

	private final Auditoria auditoria;

	private final Clock relogio;

	private final String remetente;

	AvisoDeChegadaService(ContatoRepository contatos, JornadaService jornadas, CifraDeContatos cifra, EnvioDeSms sms,
			Auditoria auditoria, Clock relogio,
			@Value("${aurora.sms.remetente:Serviço de saneamento}") String remetente) {
		this.contatos = contatos;
		this.jornadas = jornadas;
		this.cifra = cifra;
		this.sms = sms;
		this.auditoria = auditoria;
		this.relogio = relogio;
		this.remetente = remetente;
	}

	@Transactional
	public AvisoDeChegada avisar(@Nullable UUID dispositivo, UUID id, UUID os, int minutos, Instant ocorridoEm) {
		var jaRecebido = contatos.aviso(id);
		if (jaRecebido.isPresent()) {
			return jaRecebido.get();
		}
		if (dispositivo == null) {
			throw new RegraVioladaException("Celular não informado.");
		}
		var equipe = jornadas.jornadaAberta(dispositivo).equipeId();
		var ordem = contatos.ordem(os).orElseThrow(RecursoNaoEncontradoException::new);
		if (!equipe.equals(ordem.equipeId())) {
			throw new RegraVioladaException("Esta OS não é da sua equipe hoje.");
		}
		if (!"PROGRAMADA".equals(ordem.status())) {
			throw new ConflitoException("O aviso de chegada é para OS programada (antes de iniciar).");
		}
		var cifrado = ordem.telefoneCifrado();
		var finalTelefone = ordem.finalTelefone();
		if (cifrado == null || finalTelefone == null) {
			throw new RegraVioladaException("Este imóvel não tem celular cadastrado para o aviso.");
		}
		var anterior = contatos.avisoValidoDaOrdem(os);
		if (anterior.isPresent()) {
			throw new ConflitoException(
					"O morador já foi avisado às " + HORA.format(anterior.get().ocorridoEm().atZone(zona())) + ".");
		}
		if (minutos < 1 || minutos > 120) {
			throw new RegraVioladaException("Informe a chegada entre 1 e 120 minutos.");
		}
		var texto = remetente + ": a equipe está a caminho do seu imóvel (" + ordem.servicoNome()
				+ ") e deve chegar em cerca de " + minutos + " minutos. Não responda esta mensagem.";
		EnvioDeSms.Resultado resultado;
		try {
			resultado = sms.enviar(cifra.decifrar(cifrado), texto);
		}
		catch (RuntimeException erro) {
			LOG.warn("Falha no envio do aviso de chegada da OS {}: {}", os, erro.getClass().getSimpleName());
			resultado = new EnvioDeSms.Resultado(false, "erro", "Falha no envio");
		}
		var situacao = resultado.enviado() ? "ENVIADO" : resultado.erro() == null ? "REGISTRADO" : "FALHOU";
		var agora = relogio.instant();
		contatos.registrar(new ContatoRepository.NovoAviso(id, os, equipe, OrdemServicoService.usuario(), dispositivo,
				minutos, finalTelefone, situacao, resultado.provedor(),
				resultado.erro() == null ? null
						: resultado.erro().substring(0, Math.min(300, resultado.erro().length())),
				ocorridoEm.isAfter(agora) ? agora : ocorridoEm));
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("situacao", situacao);
		detalhes.put("provedor", resultado.provedor());
		detalhes.put("minutos", minutos);
		auditoria.registrar(TipoEvento.AVISO_DE_CHEGADA, "ordem-de-servico/" + os, detalhes);
		// FALHOU fica registrado (o app oferece tentar de novo, com outro id).
		return contatos.aviso(id).orElseThrow();
	}

	private ZoneId zona() {
		return relogio.getZone();
	}

}
