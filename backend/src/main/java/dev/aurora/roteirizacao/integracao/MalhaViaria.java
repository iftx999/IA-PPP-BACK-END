package dev.aurora.roteirizacao.integracao;

import java.util.List;

import dev.aurora.roteirizacao.model.Calculo;
import dev.aurora.roteirizacao.model.Coordenada;
import dev.aurora.roteirizacao.model.Matriz;
import dev.aurora.roteirizacao.model.PerfilRota;

/**
 * Deslocamentos pela malha viária (ADR 0046): matriz de tempos e distâncias e o traçado
 * da rota para o mapa. Nunca falha por indisponibilidade do serviço de rotas: cai para a
 * estimativa em linha reta e informa no {@link Resultado}.
 */
public interface MalhaViaria {

	/** Matriz {@code pontos × pontos} no perfil do veículo. */
	Resultado matriz(List<Coordenada> pontos, PerfilRota perfil);

	/**
	 * Traçado passando pelas paradas, em ordem; vazio quando só há estimativa em linha
	 * reta.
	 */
	List<List<Coordenada>> tracado(List<Coordenada> paradas, PerfilRota perfil);

	record Resultado(Matriz matriz, Calculo calculo) {
	}

}
