package co.gov.epq.sigca.facturacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.tiempo.Periodos;

/**
 * Procesos programados de facturación (A-08): el lote mensual el primer día hábil (RN-02) y el vencimiento diario
 * de facturas (RF-21). Los horarios se configuran con {@code sigca.facturacion.*}.
 */
@Component
public class ProcesosFacturacion {

    private static final Logger log = LoggerFactory.getLogger(ProcesosFacturacion.class);

    private final LoteFacturacionService lotes;
    private final FacturaService facturas;
    private final CalendarioHabil calendario;
    private final boolean loteAutomatico;

    public ProcesosFacturacion(LoteFacturacionService lotes, FacturaService facturas, CalendarioHabil calendario,
            @Value("${sigca.facturacion.lote.automatico:true}") boolean loteAutomatico) {
        this.lotes = lotes;
        this.facturas = facturas;
        this.calendario = calendario;
        this.loteAutomatico = loteAutomatico;
    }

    /** Corre a diario; solo factura el primer día hábil del mes y si el periodo no tiene lote completado. */
    @Scheduled(cron = "${sigca.facturacion.lote.cron:0 0 2 * * *}", zone = "America/Bogota")
    public void loteMensual() {
        if (!loteAutomatico || !Periodos.hoy().equals(calendario.primerDiaHabil(Periodos.aMes(Periodos.actual())))) {
            return;
        }
        int periodo = Periodos.anterior();
        if (lotes.completado(periodo)) {
            log.info("El periodo {} ya tiene lote en proceso o completado", periodo);
            return;
        }
        try {
            lotes.ejecutar(periodo);
        } catch (NegocioException e) {
            log.warn("No se lanzó el lote automático del periodo {}: {}", periodo, e.getMessage());
        }
    }

    @Scheduled(cron = "${sigca.facturacion.vencimiento.cron:0 30 0 * * *}", zone = "America/Bogota")
    public void vencerFacturas() {
        facturas.vencerFacturas();
    }
}
