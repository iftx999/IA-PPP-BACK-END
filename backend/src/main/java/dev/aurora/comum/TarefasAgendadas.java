package dev.aurora.comum;

import java.time.Duration;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Trava de tarefas agendadas no banco ({@code tarefa_agendada}, sem RLS): com várias
 * instâncias da API, só uma executa cada tarefa por vez. A trava expira sozinha se a
 * instância cair no meio.
 */
@Repository
public class TarefasAgendadas {

	private final JdbcClient jdbc;

	TarefasAgendadas(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * @return true se esta instância ficou com a tarefa (até concluir ou até o prazo)
	 */
	public boolean iniciar(String nome, Duration prazo) {
		return jdbc.sql("""
				UPDATE tarefa_agendada SET executando_ate = now() + make_interval(secs => ?)
				 WHERE nome = ? AND (executando_ate IS NULL OR executando_ate < now())""")
			.params(prazo.toSeconds(), nome)
			.update() == 1;
	}

	public void concluir(String nome, String resultado) {
		jdbc.sql("""
				UPDATE tarefa_agendada SET executando_ate = NULL, ultima_execucao = now(), ultimo_resultado = ?
				 WHERE nome = ?""")
			.params(resultado.length() > 500 ? resultado.substring(0, 500) : resultado, nome)
			.update();
	}

}
