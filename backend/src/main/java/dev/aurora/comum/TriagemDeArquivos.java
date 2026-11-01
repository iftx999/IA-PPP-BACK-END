package dev.aurora.comum;

import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Passa o arquivo enviado pelo antivírus (ADR 0033). Quem chama decide o que fazer com
 * uma ameaça (recusar e registrar na trilha). Arquivo não verificado é recusado quando o
 * antivírus é obrigatório (produção) e só gera aviso no log quando não é.
 */
@Component
public class TriagemDeArquivos {

	private static final Logger LOG = LoggerFactory.getLogger(TriagemDeArquivos.class);

	private final Antivirus antivirus;

	private final boolean obrigatorio;

	private final MeterRegistry metricas;

	TriagemDeArquivos(Antivirus antivirus, @Value("${aurora.antivirus.obrigatorio:false}") boolean obrigatorio,
			MeterRegistry metricas) {
		this.antivirus = antivirus;
		this.obrigatorio = obrigatorio;
		this.metricas = metricas;
	}

	/**
	 * @return nome da ameaça, se o antivírus encontrou alguma
	 * @throws ServicoExternoIndisponivelException antivírus obrigatório e fora do ar
	 */
	public Optional<String> ameaca(byte[] conteudo) {
		var verificacao = antivirus.verificar(conteudo);
		metricas.counter("aurora.antivirus.verificacoes", "resultado", verificacao.getClass().getSimpleName())
			.increment();
		return switch (verificacao) {
			case Antivirus.Limpo limpo -> Optional.empty();
			case Antivirus.Infectado infectado -> Optional.of(infectado.assinatura());
			case Antivirus.NaoVerificado nao -> {
				if (obrigatorio) {
					throw new ServicoExternoIndisponivelException(
							"Não foi possível verificar o arquivo com o antivírus agora. Tente novamente em instantes.",
							null);
				}
				LOG.warn("Arquivo aceito sem verificação de antivírus: {}", nao.motivo());
				yield Optional.empty();
			}
		};
	}

}
