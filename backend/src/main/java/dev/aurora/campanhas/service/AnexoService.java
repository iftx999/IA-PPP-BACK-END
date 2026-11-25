package dev.aurora.campanhas.service;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.model.Anexo;
import dev.aurora.campanhas.model.ArquivoDeFoto;
import dev.aurora.campanhas.repository.AnexoRepository;
import dev.aurora.campanhas.repository.ExecucaoRepository;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.comum.TriagemDeArquivos;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fotos das OS (10.9.3; ADR 0045): o celular envia em partes, na ordem, e retoma de onde
 * parou depois de perder o sinal. Completo, o arquivo é conferido pelo SHA-256 informado,
 * pelo tipo real do conteúdo e pelo antivírus (ADR 0033); o recusado é descartado.
 */
@Service
public class AnexoService {

	private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

	private static final Pattern PARAMETRO = Pattern.compile("^[a-z][a-z0-9_]{0,39}$");

	private final AnexoRepository anexos;

	private final ExecucaoRepository execucao;

	private final DispositivoService dispositivos;

	private final TriagemDeArquivos triagem;

	private final Auditoria auditoria;

	AnexoService(AnexoRepository anexos, ExecucaoRepository execucao, DispositivoService dispositivos,
			TriagemDeArquivos triagem, Auditoria auditoria) {
		this.anexos = anexos;
		this.execucao = execucao;
		this.dispositivos = dispositivos;
		this.triagem = triagem;
		this.auditoria = auditoria;
	}

	/** Parte de uma foto: {@code Content-Range: bytes inicio-fim/total}. */
	public record Parte(UUID os, @Nullable String parametro, @Nullable String tipoMidia, @Nullable String intervalo,
			@Nullable String sha256, byte[] conteudo) {
	}

	@Transactional
	public Anexo receber(@Nullable UUID dispositivoId, UUID id, Parte parte) {
		var dispositivo = dispositivos.exigir(dispositivoId, false);
		var faixa = ArquivoDeFoto.intervalo(parte.intervalo(), parte.conteudo().length);
		var anexo = anexos.buscar(id).orElse(null);
		if (anexo == null) {
			var unidade = execucao.unidadeParaAnexo(parte.os(), OrdemServicoService.usuario())
				.orElseThrow(RecursoNaoEncontradoException::new);
			var tipo = ArquivoDeFoto.tipo(parte.tipoMidia());
			var hash = parte.sha256() == null ? "" : parte.sha256().strip().toLowerCase(java.util.Locale.ROOT);
			if (!SHA256.matcher(hash).matches()) {
				throw new RegraVioladaException("Informe o SHA-256 da foto (64 caracteres hexadecimais).");
			}
			if (parte.parametro() != null && !PARAMETRO.matcher(parte.parametro()).matches()) {
				throw new RegraVioladaException("Parâmetro da foto inválido.");
			}
			anexos.criar(id, parte.os(), unidade, parte.parametro(), tipo, faixa.total(), hash,
					OrdemServicoService.usuario(), dispositivo.id());
			anexo = anexos.buscar(id).orElseThrow();
		}
		if (!anexo.osId().equals(parte.os()) || anexo.tamanho() != faixa.total()) {
			throw new ConflitoException("Já existe uma foto com este identificador e outros dados.");
		}
		switch (anexo.situacao()) {
			case "DISPONIVEL" -> {
				return anexo;
			}
			case "RECUSADO" -> throw new RegraVioladaException("Foto recusada: " + anexo.recusa());
			default -> {
				// Recebendo.
			}
		}
		if (faixa.fim() < anexo.recebido()) {
			// Parte já recebida (reenvio depois de perder a resposta).
			return anexo;
		}
		if (faixa.inicio() != anexo.recebido() || !anexos.acrescentar(id, faixa.inicio(), parte.conteudo())) {
			throw new ConflitoException("Envie a foto a partir do byte " + anexo.recebido() + ".");
		}
		if (faixa.fim() + 1 == faixa.total()) {
			concluir(anexo);
		}
		return anexos.buscar(id).orElseThrow();
	}

	@Transactional
	public Anexo estado(@Nullable UUID dispositivoId, UUID id) {
		dispositivos.exigir(dispositivoId, false);
		return anexos.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
	}

	/** Foto disponível de uma OS, para a web (fica na trilha). */
	@Transactional
	public byte[] conteudo(UUID os, UUID id) {
		var anexo = anexos.buscar(id)
			.filter(a -> a.osId().equals(os) && "DISPONIVEL".equals(a.situacao()))
			.orElseThrow(RecursoNaoEncontradoException::new);
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("anexo", id.toString());
		detalhes.put("parametro", anexo.parametro());
		auditoria.registrar(TipoEvento.ANEXO_DE_ORDEM_LIDO, "ordem-de-servico/" + os, detalhes);
		return anexos.conteudo(id);
	}

	public String tipoMidia(UUID id) {
		return anexos.buscar(id).map(Anexo::tipoMidia).orElseThrow(RecursoNaoEncontradoException::new);
	}

	private void concluir(Anexo anexo) {
		var conteudo = anexos.conteudo(anexo.id());
		var recusa = recusa(anexo, conteudo);
		anexos.concluir(anexo.id(), recusa == null, recusa);
		if (recusa != null) {
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("anexo", anexo.id().toString());
			detalhes.put("motivo", recusa);
			auditoria.registrar(TipoEvento.ANEXO_RECUSADO, "ordem-de-servico/" + anexo.osId(), detalhes);
		}
	}

	private @Nullable String recusa(Anexo anexo, byte[] conteudo) {
		if (!ArquivoDeFoto.sha256(conteudo).equals(anexo.sha256())) {
			return "o conteúdo não confere com o SHA-256 informado (envio corrompido)";
		}
		if (!ArquivoDeFoto.tipoConfere(anexo.tipoMidia(), conteudo)) {
			return "o conteúdo não é " + anexo.tipoMidia();
		}
		return triagem.ameaca(conteudo).map(a -> "ameaça encontrada pelo antivírus (" + a + ")").orElse(null);
	}

}
