package br.com.fatec.georural.service.indicador;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * Janela de tempo em que os focos de calor sao contados para o IFC (US#26.02).
 *
 * <p>Os dois extremos sao inclusivos e a janela cobre exatamente {@code meses} meses:
 * terminando em 31/10/2026 com 12 meses, vai de 01/11/2025 a 31/10/2026. Assim dois
 * periodos consecutivos nao contam o mesmo dia duas vezes.
 */
public record PeriodoApuracaoIfc(LocalDate inicio, LocalDate fim, int meses) {

    public PeriodoApuracaoIfc {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("O periodo do IFC precisa de inicio e fim.");
        }
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("O inicio do periodo do IFC (" + inicio
                    + ") nao pode ser depois do fim (" + fim + ").");
        }
    }

    /** Janela de {@code meses} meses que termina em {@code fim}, inclusive. */
    public static PeriodoApuracaoIfc terminandoEm(LocalDate fim, int meses) {
        if (fim == null) {
            throw new IllegalArgumentException("A data de referencia do IFC e obrigatoria.");
        }
        if (meses < 1) {
            throw new IllegalArgumentException(
                    "O periodo do IFC precisa ter ao menos 1 mes (veio " + meses + ").");
        }
        // somar o dia antes de voltar os meses evita o erro de fim de mes: 30/04 menos
        // 1 mes cairia em 30/03 e a janela comecaria em 31/03 em vez de 01/04
        return new PeriodoApuracaoIfc(fim.plusDays(1).minusMonths(meses), fim, meses);
    }

    /**
     * Janela que termina no ultimo dia da competencia. Aceita "aaaa-MM" (termina no fim do
     * mes) e "aaaa" (termina em 31/12).
     */
    public static PeriodoApuracaoIfc daCompetencia(String competencia, int meses) {
        if (competencia == null || competencia.isBlank()) {
            throw new IllegalArgumentException("A competencia do IFC e obrigatoria.");
        }
        String texto = competencia.trim();
        try {
            LocalDate fim = texto.length() == 4
                    ? Year.parse(texto).atMonth(12).atEndOfMonth()
                    : YearMonth.parse(texto).atEndOfMonth();
            return terminandoEm(fim, meses);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Competencia '" + competencia
                    + "' nao e reconhecida: use aaaa-MM ou aaaa.", e);
        }
    }

    public boolean contem(LocalDate data) {
        return data != null && !data.isBefore(inicio) && !data.isAfter(fim);
    }
}
