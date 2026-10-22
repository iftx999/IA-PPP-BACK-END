package dev.aurora.alertas.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record Alerta(UUID id, TipoAlerta tipo, Severidade severidade, @Nullable UUID unidadeId,
		@Nullable String unidadeNome, String chave, String titulo, String descricao, @Nullable String link,
		Instant criadoEm, Instant atualizadoEm, @Nullable String reconhecidoPor, @Nullable Instant reconhecidoEm,
		@Nullable Instant resolvidoEm) {

	public boolean mesmoConteudo(Condicao c) {
		return severidade == c.severidade() && titulo.equals(c.titulo()) && descricao.equals(c.descricao())
				&& c.link().equals(link);
	}

}
