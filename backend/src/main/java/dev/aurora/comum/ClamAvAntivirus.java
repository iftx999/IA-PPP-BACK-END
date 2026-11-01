package dev.aurora.comum;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cliente do clamd pelo protocolo INSTREAM (TCP): o arquivo vai em blocos com o tamanho
 * na frente e termina com um bloco vazio; a resposta é "stream: OK" ou "stream: <nome>
 * FOUND". Sem biblioteca: o protocolo é curto e estável.
 */
public class ClamAvAntivirus implements Antivirus {

	private static final Logger LOG = LoggerFactory.getLogger(ClamAvAntivirus.class);

	private static final int BLOCO = 64 * 1024;

	private final String host;

	private final int porta;

	private final Duration tempoMaximo;

	public ClamAvAntivirus(String host, int porta, Duration tempoMaximo) {
		this.host = host;
		this.porta = porta;
		this.tempoMaximo = tempoMaximo;
	}

	@Override
	public Verificacao verificar(byte[] conteudo) {
		try (var socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, porta), (int) Duration.ofSeconds(5).toMillis());
			socket.setSoTimeout((int) tempoMaximo.toMillis());
			var saida = new DataOutputStream(socket.getOutputStream());
			saida.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
			for (int inicio = 0; inicio < conteudo.length; inicio += BLOCO) {
				int tamanho = Math.min(BLOCO, conteudo.length - inicio);
				saida.writeInt(tamanho);
				saida.write(conteudo, inicio, tamanho);
			}
			saida.writeInt(0);
			saida.flush();
			return interpretar(lerResposta(socket));
		}
		catch (IOException erro) {
			LOG.warn("Antivírus indisponível em {}:{}: {}", host, porta, erro.getMessage());
			return new NaoVerificado("O antivírus não respondeu.");
		}
	}

	private static String lerResposta(Socket socket) throws IOException {
		var resposta = new ByteArrayOutputStream();
		var entrada = socket.getInputStream();
		int lido;
		while ((lido = entrada.read()) > 0) {
			resposta.write(lido);
		}
		return resposta.toString(StandardCharsets.US_ASCII).strip();
	}

	/**
	 * "stream: OK", "stream: Eicar-Test-Signature FOUND" ou "INSTREAM size limit
	 * exceeded. ERROR".
	 */
	static Verificacao interpretar(String resposta) {
		if (resposta.endsWith(" OK")) {
			return new Limpo();
		}
		if (resposta.endsWith(" FOUND")) {
			var semPrefixo = resposta.startsWith("stream: ") ? resposta.substring("stream: ".length()) : resposta;
			return new Infectado(semPrefixo.substring(0, semPrefixo.length() - " FOUND".length()).strip());
		}
		LOG.warn("Resposta inesperada do antivírus: {}", resposta);
		return new NaoVerificado("O antivírus não conseguiu verificar o arquivo.");
	}

}
