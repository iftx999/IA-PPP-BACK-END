package dev.aurora.comum;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

class ClamAvAntivirusTest {

	/** Arquivo de teste padrão dos antivírus (não é malicioso). */
	static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

	@Test
	void interpretaAsRespostasDoClamd() {
		assertThat(ClamAvAntivirus.interpretar("stream: OK")).isInstanceOf(Antivirus.Limpo.class);
		assertThat(ClamAvAntivirus.interpretar("stream: Win.Test.EICAR_HDB-1 FOUND"))
			.isEqualTo(new Antivirus.Infectado("Win.Test.EICAR_HDB-1"));
		assertThat(ClamAvAntivirus.interpretar("INSTREAM size limit exceeded. ERROR"))
			.isInstanceOf(Antivirus.NaoVerificado.class);
	}

	@Test
	void enviaOArquivoEmBlocosPeloInstream() throws Exception {
		var recebido = new AtomicReference<byte[]>();
		try (var servidor = new ServerSocket(0)) {
			var thread = Thread.ofVirtual().start(() -> {
				try (var conexao = servidor.accept()) {
					var entrada = new DataInputStream(conexao.getInputStream());
					var comando = new byte["zINSTREAM\0".length()];
					entrada.readFully(comando);
					assertThat(new String(comando, StandardCharsets.US_ASCII)).isEqualTo("zINSTREAM\0");
					var conteudo = new ByteArrayOutputStream();
					int tamanho;
					while ((tamanho = entrada.readInt()) > 0) {
						conteudo.write(entrada.readNBytes(tamanho));
					}
					recebido.set(conteudo.toByteArray());
					conexao.getOutputStream().write("stream: Teste.Ameaca FOUND\0".getBytes(StandardCharsets.US_ASCII));
				}
				catch (Exception erro) {
					throw new IllegalStateException(erro);
				}
			});
			var arquivo = new byte[200_000];
			arquivo[199_999] = 7;
			var resultado = new ClamAvAntivirus("localhost", servidor.getLocalPort(), Duration.ofSeconds(5))
				.verificar(arquivo);
			thread.join(5000);

			assertThat(resultado).isEqualTo(new Antivirus.Infectado("Teste.Ameaca"));
			assertThat(recebido.get()).isEqualTo(arquivo);
		}
	}

	@Test
	void foraDoArNaoVerifica() throws Exception {
		int porta;
		try (var livre = new ServerSocket(0)) {
			porta = livre.getLocalPort();
		}
		assertThat(new ClamAvAntivirus("localhost", porta, Duration.ofSeconds(2)).verificar(new byte[] { 1 }))
			.isInstanceOf(Antivirus.NaoVerificado.class);
	}

	/**
	 * Contra um clamd de verdade:
	 * {@code CLAMAV_HOST=localhost ./mvnw test -Dtest=ClamAvAntivirusTest}.
	 */
	@Test
	@EnabledIfEnvironmentVariable(named = "CLAMAV_HOST", matches = ".+")
	void clamdDeVerdadeReconheceOEicar() {
		var clamav = new ClamAvAntivirus(System.getenv("CLAMAV_HOST"), 3310, Duration.ofSeconds(30));
		assertThat(clamav.verificar(EICAR.getBytes(StandardCharsets.US_ASCII))).isInstanceOf(Antivirus.Infectado.class);
		assertThat(clamav.verificar("%PDF-1.7 documento comum".getBytes(StandardCharsets.US_ASCII)))
			.isInstanceOf(Antivirus.Limpo.class);
	}

}
