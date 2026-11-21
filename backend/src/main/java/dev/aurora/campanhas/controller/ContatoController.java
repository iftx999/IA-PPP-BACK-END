package dev.aurora.campanhas.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import dev.aurora.campanhas.model.ImportacaoDeContatos;
import dev.aurora.campanhas.service.ContatoService;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ExigePermissao;
import org.jspecify.annotations.Nullable;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Celulares dos imóveis para o aviso de chegada (ADR 0052), com a mesma permissão do
 * vínculo matrícula → endereço. O número nunca volta nas respostas.
 */
@RestController
@RequestMapping("/api/v1/contatos-dos-imoveis")
@ExigePermissao("logradouro:gerenciar")
class ContatoController {

	static final long TAMANHO_MAXIMO = 20L * 1024 * 1024;

	record ResumoDeContatosResponse(long total, @Nullable Instant atualizadoEm) {
	}

	record RejeicaoDeContatoResponse(int linha, String motivo) {
	}

	record ImportacaoDeContatosResponse(int linhas, int gravados, int removidos, int rejeitados,
			List<RejeicaoDeContatoResponse> rejeicoes) {

		static ImportacaoDeContatosResponse de(ImportacaoDeContatos i) {
			return new ImportacaoDeContatosResponse(i.linhas(), i.gravados(), i.removidos(), i.rejeitados(),
					i.rejeicoes().stream().map(r -> new RejeicaoDeContatoResponse(r.linha(), r.motivo())).toList());
		}

	}

	private final ContatoService contatos;

	ContatoController(ContatoService contatos) {
		this.contatos = contatos;
	}

	@GetMapping
	ResumoDeContatosResponse resumo() {
		var r = contatos.resumo();
		return new ResumoDeContatosResponse(r.total(), r.atualizadoEm());
	}

	/** CSV {@code matricula;telefone}; telefone em branco tira o imóvel do aviso. */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ImportacaoDeContatosResponse importar(@RequestPart("arquivo") MultipartFile arquivo) throws IOException {
		if (arquivo.getSize() > TAMANHO_MAXIMO) {
			throw new RegraVioladaException("O arquivo passa de 20 MB.");
		}
		try (var entrada = arquivo.getInputStream()) {
			return ImportacaoDeContatosResponse.de(contatos.importar(entrada));
		}
	}

}
