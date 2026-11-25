package dev.aurora.campanhas.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.model.DadosDispositivo;
import dev.aurora.campanhas.model.Dispositivo;
import dev.aurora.campanhas.repository.DispositivoRepository;
import dev.aurora.comum.AcessoNegadoException;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Celulares do app de campo (J13; ADR 0045). Toda chamada do app identifica o celular; o
 * revogado perde o acesso e apaga os dados locais na próxima conexão. Só a fila pendente
 * ainda é aceita (marcada como de aparelho revogado).
 */
@Service
public class DispositivoService {

	static final String NAO_CADASTRADO = "DISPOSITIVO_NAO_CADASTRADO";

	static final String REVOGADO = "DISPOSITIVO_REVOGADO";

	private final DispositivoRepository dispositivos;

	private final Auditoria auditoria;

	DispositivoService(DispositivoRepository dispositivos, Auditoria auditoria) {
		this.dispositivos = dispositivos;
		this.auditoria = auditoria;
	}

	/** Primeiro login no celular (ou novo login de outra pessoa no mesmo celular). */
	@Transactional
	public Dispositivo cadastrar(@Nullable UUID id, DadosDispositivo dados) {
		if (id == null) {
			throw new RegraVioladaException("Informe o identificador do celular.");
		}
		var usuario = OrdemServicoService.usuario();
		var anterior = dispositivos.buscar(id);
		if (anterior.isPresent() && anterior.get().revogado()) {
			throw new AcessoNegadoException("Este celular foi bloqueado. Procure o administrador.", REVOGADO);
		}
		dispositivos.cadastrar(id, usuario, texto(dados.modelo(), 80, "Modelo"), texto(dados.sistema(), 40, "Sistema"),
				texto(dados.versaoApp(), 20, "Versão do app"));
		if (anterior.isEmpty() || !anterior.get().usuarioId().equals(usuario)) {
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("modelo", dados.modelo().strip());
			detalhes.put("sistema", dados.sistema().strip());
			detalhes.put("usuarioAnterior", anterior.map(d -> d.usuarioId().toString()).orElse(null));
			auditoria.registrar(TipoEvento.DISPOSITIVO_CADASTRADO, "dispositivo/" + id, detalhes);
		}
		return dispositivos.buscar(id).orElseThrow();
	}

	/**
	 * O celular da chamada, cadastrado para quem chama.
	 * @param aceitaRevogado só para enviar a fila pendente e confirmar a limpeza
	 */
	@Transactional
	public Dispositivo exigir(@Nullable UUID id, boolean aceitaRevogado) {
		var dispositivo = id == null ? null
				: dispositivos.buscar(id).filter(d -> d.usuarioId().equals(OrdemServicoService.usuario())).orElse(null);
		if (dispositivo == null) {
			throw new AcessoNegadoException("Cadastre este celular para usar o app.", NAO_CADASTRADO);
		}
		if (dispositivo.revogado() && !aceitaRevogado) {
			throw new AcessoNegadoException("Este celular foi bloqueado. Procure o administrador.", REVOGADO);
		}
		dispositivos.tocar(dispositivo.id());
		return dispositivo;
	}

	/** O app apagou os dados locais depois da revogação. */
	@Transactional
	public void confirmarApagado(@Nullable UUID id) {
		var dispositivo = exigir(id, true);
		if (!dispositivo.revogado()) {
			throw new ConflitoException("O celular não está bloqueado.");
		}
		dispositivos.apagado(dispositivo.id());
	}

	@Transactional(readOnly = true)
	public List<Dispositivo> listar() {
		return dispositivos.listar();
	}

	/** J13: bloqueia o celular (perdido, roubado, trocado). */
	@Transactional
	public Dispositivo revogar(UUID id) {
		var dispositivo = dispositivos.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (!dispositivos.revogar(id, OrdemServicoService.usuario())) {
			throw new ConflitoException("Este celular já está bloqueado.");
		}
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("usuario", dispositivo.usuarioId().toString());
		detalhes.put("modelo", dispositivo.modelo());
		auditoria.registrar(TipoEvento.DISPOSITIVO_REVOGADO, "dispositivo/" + id, detalhes);
		return dispositivos.buscar(id).orElseThrow();
	}

	private static String texto(@Nullable String valor, int maximo, String campo) {
		var limpo = valor == null ? "" : valor.strip();
		if (limpo.isEmpty() || limpo.length() > maximo) {
			throw new RegraVioladaException(campo + ": de 1 a " + maximo + " caracteres.");
		}
		return limpo;
	}

}
