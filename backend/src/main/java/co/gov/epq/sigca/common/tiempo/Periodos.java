package co.gov.epq.sigca.common.tiempo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import co.gov.epq.sigca.common.error.NegocioException;

/**
 * Periodos de facturación en formato AAAAMM (NUMBER(6) en la BD), con las mismas funciones que PKG_SIGCA.
 */
public final class Periodos {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private Periodos() {
    }

    public static YearMonth aMes(int periodo) {
        int anio = periodo / 100;
        int mes = periodo % 100;
        if (anio < 2000 || anio > 2999 || mes < 1 || mes > 12) {
            throw new NegocioException("PERIODO_INVALIDO", "El periodo debe tener formato AAAAMM: " + periodo);
        }
        return YearMonth.of(anio, mes);
    }

    public static int dePeriodo(YearMonth mes) {
        return mes.getYear() * 100 + mes.getMonthValue();
    }

    public static int actual() {
        return dePeriodo(YearMonth.now(ZONA));
    }

    /** Último periodo cerrado (el mes anterior): el que se factura este mes. */
    public static int anterior() {
        return dePeriodo(YearMonth.now(ZONA).minusMonths(1));
    }

    public static int siguiente(int periodo) {
        return dePeriodo(aMes(periodo).plusMonths(1));
    }

    public static LocalDate inicio(int periodo) {
        return aMes(periodo).atDay(1);
    }

    public static LocalDate fin(int periodo) {
        return aMes(periodo).atEndOfMonth();
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }
}
