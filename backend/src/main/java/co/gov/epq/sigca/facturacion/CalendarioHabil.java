package co.gov.epq.sigca.facturacion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.catalogo.Festivo;
import co.gov.epq.sigca.catalogo.FestivoRepository;

/** Días hábiles con la tabla FESTIVO, igual que PKG_SIGCA.es_dia_habil (RN-02, P-09, P-26). */
@Component
@Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
public class CalendarioHabil {

    private final FestivoRepository festivos;

    public CalendarioHabil(FestivoRepository festivos) {
        this.festivos = festivos;
    }

    public boolean esHabil(LocalDate fecha) {
        return esHabil(fecha, festivosEntre(fecha, fecha));
    }

    /** RN-02: primer día hábil del mes. */
    public LocalDate primerDiaHabil(YearMonth mes) {
        Set<LocalDate> f = festivosEntre(mes.atDay(1), mes.atEndOfMonth());
        LocalDate d = mes.atDay(1);
        while (!esHabil(d, f)) {
            d = d.plusDays(1);
        }
        return d;
    }

    public LocalDate sumarHabiles(LocalDate desde, int dias) {
        Set<LocalDate> f = festivosEntre(desde, desde.plusDays(dias * 2L + 15));
        LocalDate d = desde;
        int contados = 0;
        while (contados < dias) {
            d = d.plusDays(1);
            if (esHabil(d, f)) {
                contados++;
            }
        }
        return d;
    }

    private Set<LocalDate> festivosEntre(LocalDate desde, LocalDate hasta) {
        return festivos.findByFechaBetweenOrderByFecha(desde, hasta).stream().map(Festivo::getFecha)
                .collect(Collectors.toSet());
    }

    private static boolean esHabil(LocalDate d, Set<LocalDate> festivos) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !festivos.contains(d);
    }
}
