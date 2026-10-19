package co.gov.epq.sigca.contratos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RN-36: cada 15 minutos avisa a los asesores las reconexiones que superaron su plazo de 24 horas. */
@Component
public class OrdenesProgramadas {

    private static final Logger log = LoggerFactory.getLogger(OrdenesProgramadas.class);

    private final OrdenService ordenes;

    public OrdenesProgramadas(OrdenService ordenes) {
        this.ordenes = ordenes;
    }

    @Scheduled(cron = "${sigca.ordenes.cron-reconexiones-vencidas:0 */15 * * * *}", zone = "America/Bogota")
    public void avisarReconexionesVencidas() {
        try {
            int n = ordenes.avisarReconexionesVencidas();
            if (n > 0) {
                log.info("Avisadas {} reconexiones vencidas", n);
            }
        } catch (RuntimeException e) {
            log.error("Fallo avisando reconexiones vencidas", e);
        }
    }
}
