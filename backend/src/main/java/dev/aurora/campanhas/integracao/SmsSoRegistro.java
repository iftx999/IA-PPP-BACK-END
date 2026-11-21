package dev.aurora.campanhas.integracao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sem provedor de SMS configurado ({@code aurora.sms.provedor=registro}, o padrão): o
 * aviso fica só registrado na trilha da OS, sem mandar nada ao morador. O número não vai
 * para o log.
 */
@Component
@ConditionalOnProperty(name = "aurora.sms.provedor", havingValue = "registro", matchIfMissing = true)
class SmsSoRegistro implements EnvioDeSms {

	private static final Logger LOG = LoggerFactory.getLogger(SmsSoRegistro.class);

	@Override
	public Resultado enviar(String telefone, String texto) {
		LOG.info("Aviso de chegada registrado sem envio (sem provedor de SMS configurado), {} caracteres",
				texto.length());
		return Resultado.registrado("registro");
	}

}
