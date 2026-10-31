package dev.aurora.alertas.service;

import dev.aurora.alertas.model.PreferenciaEmail;
import dev.aurora.alertas.repository.PreferenciaRepository;
import dev.aurora.contexto.ContextoAcesso;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Preferência de e-mail dos alertas da própria pessoa (ADR 0024). */
@Service
public class PreferenciaService {

	private final PreferenciaRepository preferencias;

	private final EnvioDeEmail envio;

	PreferenciaService(PreferenciaRepository preferencias, EnvioDeEmail envio) {
		this.preferencias = preferencias;
		this.envio = envio;
	}

	/**
	 * @param envioLigado falso quando o ambiente não tem SMTP (a tela avisa)
	 */
	public record Preferencias(PreferenciaEmail email, boolean envioLigado) {
	}

	@Transactional(readOnly = true)
	public Preferencias minhas() {
		var usuario = ContextoAcesso.exigir().usuarioId();
		return new Preferencias(preferencias.buscar(usuario).orElse(PreferenciaEmail.CRITICOS), envio.ligado());
	}

	@Transactional
	public Preferencias definir(PreferenciaEmail email) {
		preferencias.salvar(ContextoAcesso.exigir().usuarioId(), email);
		return new Preferencias(email, envio.ligado());
	}

}
