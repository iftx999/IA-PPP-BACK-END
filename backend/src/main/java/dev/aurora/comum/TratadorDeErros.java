package dev.aurora.comum;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converte exceções de negócio em respostas Problem Details (RFC 9457). */
@RestControllerAdvice
class TratadorDeErros {

	@ExceptionHandler(RecursoNaoEncontradoException.class)
	ProblemDetail naoEncontrado(RecursoNaoEncontradoException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, erro.getMessage());
	}

	@ExceptionHandler(ConflitoException.class)
	ProblemDetail conflito(ConflitoException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, erro.getMessage());
	}

	@ExceptionHandler(RegraVioladaException.class)
	ProblemDetail regraViolada(RegraVioladaException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, erro.getMessage());
	}

	@ExceptionHandler(LimiteDeUsoException.class)
	ProblemDetail limiteDeUso(LimiteDeUsoException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, erro.getMessage());
	}

	@ExceptionHandler(ServicoExternoIndisponivelException.class)
	ProblemDetail servicoExternoIndisponivel(ServicoExternoIndisponivelException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, erro.getMessage());
	}

}
