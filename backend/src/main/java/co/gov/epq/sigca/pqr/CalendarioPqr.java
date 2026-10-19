package co.gov.epq.sigca.pqr;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import co.gov.epq.sigca.catalogo.Festivo;
import co.gov.epq.sigca.catalogo.FestivoRepository;

/**
 * Días hábiles restantes de una PQR para la bandeja (RN-19): excluye sábados, domingos y la tabla FESTIVO.
 * El plazo oficial lo calcula la BD; esto solo sirve para mostrar y priorizar.
 */
@Component
public class CalendarioPqr {

    private final FestivoRepository festivos;

    public CalendarioPqr(FestivoRepository festivos) {
        this.festivos = festivos;
    }

    /** Festivos en una ventana alrededor de hoy, para evaluar muchas PQR con una sola consulta. */
    public Set<LocalDate> festivosEntre(LocalDate desde, LocalDate hasta) {
        return festivos.findByFechaBetweenOrderByFecha(desde, hasta).stream().map(Festivo::getFecha)
                .collect(Collectors.toSet());
    }

    /**
     * Días hábiles desde {@code hoy} (excluido) hasta {@code limite} (incluido); negativo si el límite ya pasó
     * (cuántos días hábiles lleva vencida) y 0 si vence hoy.
     */
    public static int diasHabiles(LocalDate hoy, LocalDate limite, Set<LocalDate> festivos) {
        if (limite == null) {
            return 0;
        }
        int signo = limite.isBefore(hoy) ? -1 : 1;
        LocalDate desde = signo > 0 ? hoy : limite;
        LocalDate hasta = signo > 0 ? limite : hoy;
        int n = 0;
        for (LocalDate d = desde.plusDays(1); !d.isAfter(hasta); d = d.plusDays(1)) {
            if (esHabil(d, festivos)) {
                n++;
            }
        }
        return signo * n;
    }

    public static boolean esHabil(LocalDate d, Set<LocalDate> festivos) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !festivos.contains(d);
    }
}
