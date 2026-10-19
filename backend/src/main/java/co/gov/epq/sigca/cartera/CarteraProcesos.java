package co.gov.epq.sigca.cartera;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.tiempo.Periodos;

/**
 * Proceso diario de acuerdos (P-12, RN-07): las cuotas pendientes vencidas (más ACUERDO_DIAS_TOLERANCIA) pasan a
 * VENCIDA; TRG_CUOTA_AU marca el acuerdo INCUMPLIDO y TRG_ACUERDO_AIU devuelve las facturas a VENCIDA y reactiva el
 * cobro. Un acuerdo por transacción.
 */
@Component
public class CarteraProcesos {

    private static final Logger log = LoggerFactory.getLogger(CarteraProcesos.class);

    private final CarteraCuotaRepository cuotas;
    private final CarteraAcuerdoRepository acuerdos;
    private final CarteraConsultas consultas;
    private final Parametros parametros;
    private final Auditoria auditoria;
    private final AvisosCartera avisos;
    private final TransactionTemplate tx;

    public CarteraProcesos(CarteraCuotaRepository cuotas, CarteraAcuerdoRepository acuerdos, CarteraConsultas consultas,
            Parametros parametros, Auditoria auditoria, AvisosCartera avisos, PlatformTransactionManager tm) {
        this.cuotas = cuotas;
        this.acuerdos = acuerdos;
        this.consultas = consultas;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.avisos = avisos;
        this.tx = new TransactionTemplate(tm);
    }

    @Scheduled(cron = "${sigca.procesos.cartera.cron:0 30 0 * * *}", zone = "America/Bogota")
    public void ejecutar() {
        log.info("Proceso de cuotas vencidas: {} acuerdos incumplidos", vencerCuotas());
    }

    /** @return número de acuerdos que quedaron INCUMPLIDOS. */
    public int vencerCuotas() {
        LocalDate limite = limite();
        int n = 0;
        for (Long acuerdoId : cuotas.acuerdosConCuotasVencidas(limite)) {
            try {
                n += procesar(acuerdoId, limite) ? 1 : 0;
            } catch (RuntimeException e) {
                log.warn("No se pudo procesar el acuerdo {}: {}", acuerdoId, e.getMessage());
            }
        }
        return n;
    }

    /** Vence las cuotas atrasadas de un acuerdo en su propia transacción; true si quedó INCUMPLIDO. */
    boolean procesar(Long acuerdoId, LocalDate limite) {
        Boolean ok = tx.execute(s -> {
            List<CuotaAcuerdo> vencidas = cuotas.cuotasVencidas(acuerdoId, limite);
            if (vencidas.isEmpty()) {
                return false;
            }
            for (CuotaAcuerdo c : vencidas) {
                c.setEstado(EstadoCuota.VENCIDA);
            }
            cuotas.saveAllAndFlush(vencidas);
            AcuerdoPago a = acuerdos.findById(acuerdoId).orElseThrow();
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("numero", a.getNumeroAcuerdo());
            d.put("cuotasVencidas", vencidas.stream().map(CuotaAcuerdo::getNumeroCuota).toList());
            auditoria.exito(OperacionAuditoria.INCUMPLIR_ACUERDO, AcuerdoService.ENTIDAD, acuerdoId, d);
            consultas.usuario(a.getUsuarioId()).ifPresent(u -> avisos.incumplido(u, a, vencidas));
            return true;
        });
        return Boolean.TRUE.equals(ok);
    }

    LocalDate limite() {
        return Periodos.hoy().minusDays(parametros.entero(Parametros.ACUERDO_DIAS_TOLERANCIA));
    }
}
