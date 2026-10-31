package dev.aurora.contratos;

import java.time.LocalDate;
import java.util.List;

/** API do módulo de contratos para os demais módulos. */
public interface PrazosContratuais {

	/**
	 * Prazos do escopo a acompanhar: próximo vencimento de prazos e obrigações aprovados
	 * até {@code itensAte} (vencidos inclusive) e contratos cuja vigência termina entre
	 * hoje e {@code contratosAte}.
	 */
	List<PrazoContratual> pendentes(LocalDate hoje, LocalDate itensAte, LocalDate contratosAte);

}
