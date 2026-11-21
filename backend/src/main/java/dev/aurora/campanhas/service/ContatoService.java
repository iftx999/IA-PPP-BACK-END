package dev.aurora.campanhas.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.model.ImportacaoDeContatos;
import dev.aurora.campanhas.model.ImportacaoDeContatos.Rejeicao;
import dev.aurora.campanhas.model.ResumoDeContatos;
import dev.aurora.campanhas.repository.ContatoRepository;
import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Telefones dos imóveis para o aviso de chegada (ADR 0052), vindos da concessionária em
 * CSV {@code matricula;telefone} (com ou sem cabeçalho; ";" ou ","). Só celular
 * brasileiro (SMS); telefone em branco tira o imóvel do aviso. Só imóveis com endereço
 * importado.
 */
@Service
public class ContatoService {

	static final int MAXIMO_DE_LINHAS = 200_000;

	static final int REJEICOES_MOSTRADAS = 100;

	private final ContatoRepository contatos;

	private final CifraDeContatos cifra;

	private final Auditoria auditoria;

	ContatoService(ContatoRepository contatos, CifraDeContatos cifra, Auditoria auditoria) {
		this.contatos = contatos;
		this.cifra = cifra;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public ResumoDeContatos resumo() {
		return contatos.resumo();
	}

	@Transactional
	public ImportacaoDeContatos importar(InputStream arquivo) {
		record Linha(int numero, String matricula, @Nullable String telefone) {
		}
		var lidas = new ArrayList<Linha>();
		var rejeicoes = new ArrayList<Rejeicao>();
		int rejeitadas = 0;
		int deDados = 0;
		try (var leitor = new BufferedReader(new InputStreamReader(arquivo, StandardCharsets.UTF_8))) {
			String texto;
			int numero = 0;
			while ((texto = leitor.readLine()) != null) {
				numero++;
				if (numero > MAXIMO_DE_LINHAS) {
					throw new RegraVioladaException("O arquivo passa de " + MAXIMO_DE_LINHAS + " linhas.");
				}
				var linha = texto.strip().replace("﻿", "");
				if (linha.isEmpty() || (numero == 1 && linha.toLowerCase().startsWith("matricula"))) {
					continue;
				}
				deDados++;
				var partes = linha.split("[;,]", -1);
				var matricula = partes[0].strip();
				if (matricula.isEmpty() || partes.length > 2) {
					rejeitadas++;
					adicionar(rejeicoes, numero, "Use duas colunas: matricula;telefone.");
					continue;
				}
				var bruto = partes.length == 2 ? partes[1].strip() : "";
				if (bruto.isEmpty()) {
					lidas.add(new Linha(numero, matricula, null));
					continue;
				}
				var celular = celular(bruto);
				if (celular.isEmpty()) {
					rejeitadas++;
					adicionar(rejeicoes, numero, "Telefone inválido: use um celular com DDD (SMS não chega em fixo).");
					continue;
				}
				lidas.add(new Linha(numero, matricula, celular.get()));
			}
		}
		catch (IOException erro) {
			throw new RegraVioladaException("Não deu para ler o arquivo.");
		}
		var comEndereco = contatos.comEndereco(lidas.stream().map(Linha::matricula).distinct().toList());
		var usuario = OrdemServicoService.usuario();
		int gravados = 0;
		int removidos = 0;
		for (var l : lidas) {
			if (!comEndereco.contains(l.matricula())) {
				rejeitadas++;
				adicionar(rejeicoes, l.numero(), "Imóvel sem endereço importado (ou fora da sua área).");
				continue;
			}
			var telefone = l.telefone();
			if (telefone == null) {
				removidos += contatos.remover(l.matricula()) ? 1 : 0;
			}
			else {
				contatos.gravar(l.matricula(), cifra.cifrar(telefone), telefone.substring(telefone.length() - 4),
						usuario);
				gravados++;
			}
		}
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("gravados", gravados);
		detalhes.put("removidos", removidos);
		detalhes.put("rejeitados", rejeitadas);
		auditoria.registrar(TipoEvento.CONTATOS_DE_IMOVEIS_IMPORTADOS, "contatos-dos-imoveis", detalhes);
		return new ImportacaoDeContatos(deDados, gravados, removidos, rejeitadas, rejeicoes);
	}

	/**
	 * Celular brasileiro em E.164: DDD de 11 a 99 e 9 dígitos começando por 9 (com ou sem
	 * o 55 e a pontuação).
	 */
	static Optional<String> celular(String bruto) {
		var digitos = bruto.replaceAll("\\D", "");
		if (digitos.length() == 13 && digitos.startsWith("55")) {
			digitos = digitos.substring(2);
		}
		if (digitos.length() != 11 || digitos.charAt(0) == '0' || digitos.charAt(2) != '9') {
			return Optional.empty();
		}
		return Optional.of("+55" + digitos);
	}

	private static void adicionar(ArrayList<Rejeicao> rejeicoes, int linha, String motivo) {
		if (rejeicoes.size() < REJEICOES_MOSTRADAS) {
			rejeicoes.add(new Rejeicao(linha, motivo));
		}
	}

}
