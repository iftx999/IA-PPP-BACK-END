package dev.aurora.servicos.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Dias úteis do prazo das OS (RN-21, P6): sábado, domingo, feriados nacionais (calculados
 * aqui, inclusive a Sexta-feira Santa) e os feriados cadastrados para a unidade. Carnaval
 * e Corpus Christi são pontos facultativos: entram como feriado municipal quando o
 * município os decreta.
 */
public final class Calendario {

	private static final Set<MonthDay> FIXOS = Set.of(MonthDay.of(1, 1), MonthDay.of(4, 21), MonthDay.of(5, 1),
			MonthDay.of(9, 7), MonthDay.of(10, 12), MonthDay.of(11, 2), MonthDay.of(11, 15), MonthDay.of(12, 25));

	/** Dia Nacional de Zumbi e da Consciência Negra (Lei 14.759/2023). */
	private static final MonthDay CONSCIENCIA_NEGRA = MonthDay.of(11, 20);

	private static final int ANO_DA_CONSCIENCIA_NEGRA = 2024;

	/** Limite de dias percorridos (365 dias úteis cabem com folga). */
	private static final int LIMITE_DE_DIAS = 3 * 366;

	private Calendario() {
	}

	/** Feriados nacionais do ano, por data, com o nome. */
	public static Map<LocalDate, String> nacionais(int ano) {
		var mapa = new TreeMap<LocalDate, String>();
		mapa.put(LocalDate.of(ano, 1, 1), "Confraternização Universal");
		mapa.put(pascoa(ano).minusDays(2), "Sexta-feira Santa");
		mapa.put(LocalDate.of(ano, 4, 21), "Tiradentes");
		mapa.put(LocalDate.of(ano, 5, 1), "Dia do Trabalho");
		mapa.put(LocalDate.of(ano, 9, 7), "Independência do Brasil");
		mapa.put(LocalDate.of(ano, 10, 12), "Nossa Senhora Aparecida");
		mapa.put(LocalDate.of(ano, 11, 2), "Finados");
		mapa.put(LocalDate.of(ano, 11, 15), "Proclamação da República");
		if (ano >= ANO_DA_CONSCIENCIA_NEGRA) {
			mapa.put(LocalDate.of(ano, 11, 20), "Dia Nacional de Zumbi e da Consciência Negra");
		}
		mapa.put(LocalDate.of(ano, 12, 25), "Natal");
		return mapa;
	}

	public static boolean feriadoNacional(LocalDate dia) {
		var md = MonthDay.from(dia);
		return FIXOS.contains(md) || (md.equals(CONSCIENCIA_NEGRA) && dia.getYear() >= ANO_DA_CONSCIENCIA_NEGRA)
				|| dia.equals(pascoa(dia.getYear()).minusDays(2));
	}

	/** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher, calendário gregoriano). */
	public static LocalDate pascoa(int ano) {
		int a = ano % 19;
		int b = ano / 100;
		int c = ano % 100;
		int d = b / 4;
		int e = b % 4;
		int f = (b + 8) / 25;
		int g = (b - f + 1) / 3;
		int h = (19 * a + b - d - g + 15) % 30;
		int i = c / 4;
		int k = c % 4;
		int l = (32 + 2 * e + 2 * i - h - k) % 7;
		int m = (a + 11 * h + 22 * l) / 451;
		int mes = (h + l - 7 * m + 114) / 31;
		int dia = (h + l - 7 * m + 114) % 31 + 1;
		return LocalDate.of(ano, mes, dia);
	}

	public static boolean util(LocalDate dia, Predicate<LocalDate> feriadoLocal) {
		var semana = dia.getDayOfWeek();
		return semana != DayOfWeek.SATURDAY && semana != DayOfWeek.SUNDAY && !feriadoNacional(dia)
				&& !feriadoLocal.test(dia);
	}

	/**
	 * O {@code dias}-ésimo dia útil depois de {@code inicio} (o próprio dia de início não
	 * conta).
	 */
	public static LocalDate somarDiasUteis(LocalDate inicio, int dias, Predicate<LocalDate> feriadoLocal) {
		if (dias < 1) {
			throw new IllegalArgumentException("dias úteis: ao menos 1");
		}
		var dia = inicio;
		int contados = 0;
		for (int passos = 0; passos < LIMITE_DE_DIAS; passos++) {
			dia = dia.plusDays(1);
			if (util(dia, feriadoLocal) && ++contados == dias) {
				return dia;
			}
		}
		throw new IllegalArgumentException("prazo além do limite do calendário");
	}

}
