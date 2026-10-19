package co.gov.epq.sigca.pqr;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Procesos programados de PQR (RF-11, RN-19, RN-21): alerta previa al vencimiento, paso a VENCIDA con silencio
 * administrativo positivo y recordatorio de reconocimiento pendiente. Cada PQR va en su propia transacción para que
 * un error en una no detenga las demás.
 */
@Component
public class PqrProcesos {

    private static final Logger log = LoggerFactory.getLogger(PqrProcesos.class);

    private final PqrRepository repo;
    private final PqrConsultas consultas;
    private final AvisosPqr avisos;
    private final Parametros parametros;
    private final Auditoria auditoria;
    private final TransactionTemplate tx;

    public PqrProcesos(PqrRepository repo, PqrConsultas consultas, AvisosPqr avisos, Parametros parametros,
            Auditoria auditoria, PlatformTransactionManager tm) {
        this.repo = repo;
        this.consultas = consultas;
        this.avisos = avisos;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.tx = new TransactionTemplate(tm);
    }

    @Scheduled(cron = "${sigca.procesos.pqr.cron:0 10 0,8,14 * * *}", zone = "America/Bogota")
    public void ejecutar() {
        int v = vencer();
        int a = alertar();
        log.info("Procesos PQR: {} vencidas, {} alertas previas", v, a);
    }

    /** Una vez al día: recordatorio de silencios positivos pendientes de reconocer (PQR_ALERTA_RECONOCIMIENTO). */
    @Scheduled(cron = "${sigca.procesos.pqr.cron-reconocimiento:0 5 8 * * MON-FRI}", zone = "America/Bogota")
    public void recordar() {
        log.info("Procesos PQR: {} recordatorios de silencio positivo", recordarReconocimientos());
    }

    /** RECIBIDA/EN_TRAMITE con fecha límite pasada → VENCIDA (la BD marca el silencio positivo). */
    public int vencer() {
        int n = 0;
        for (Long id : repo.idsFueraDePlazo(List.of(EstadoPqr.RECIBIDA, EstadoPqr.EN_TRAMITE), Periodos.hoy())) {
            try {
                Boolean ok = tx.execute(s -> {
                    Pqr p = repo.findById(id).orElse(null);
                    if (p == null || (p.getEstado() != EstadoPqr.RECIBIDA && p.getEstado() != EstadoPqr.EN_TRAMITE)) {
                        return false;
                    }
                    EstadoPqr anterior = p.getEstado();
                    p.setEstado(EstadoPqr.VENCIDA);
                    repo.saveAndFlush(p);
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("radicado", p.getNumeroRadicado());
                    d.put("estadoAnterior", anterior.name());
                    d.put("fechaLimite", p.getFechaLimiteRespuesta().toString());
                    auditoria.exito(OperacionAuditoria.VENCER_PQR, RadicacionPqrService.ENTIDAD, p.getId(), d);
                    avisos.vencida(p, jefes());
                    return true;
                });
                n += Boolean.TRUE.equals(ok) ? 1 : 0;
            } catch (RuntimeException e) {
                log.warn("No se pudo vencer la PQR {}: {}", id, e.getMessage());
            }
        }
        return n;
    }

    /** Alerta al gestor y a los jefes cuando faltan menos de PQR_HORAS_ALERTA horas (una sola vez por PQR). */
    public int alertar() {
        int horas = parametros.entero(Parametros.PQR_HORAS_ALERTA);
        LocalDate hasta = LocalDateTime.now(Periodos.ZONA).plusHours(horas).toLocalDate();
        int n = 0;
        for (Long id : repo.idsPorAlertar(PqrConsultas.ABIERTAS, hasta)) {
            try {
                Boolean ok = tx.execute(s -> {
                    Pqr p = repo.findById(id).orElse(null);
                    if (p == null || Boolean.TRUE.equals(p.getAlerta48hEnviada())
                            || (p.getEstado() != EstadoPqr.RECIBIDA && p.getEstado() != EstadoPqr.EN_TRAMITE)) {
                        return false;
                    }
                    p.setAlerta48hEnviada(true);
                    repo.saveAndFlush(p);
                    avisos.alertaVencimiento(p, jefes(), horas);
                    return true;
                });
                n += Boolean.TRUE.equals(ok) ? 1 : 0;
            } catch (RuntimeException e) {
                log.warn("No se pudo alertar la PQR {}: {}", id, e.getMessage());
            }
        }
        return n;
    }

    /** Silencios positivos aún sin reconocer: recordatorio al responsable y a los jefes. */
    public int recordarReconocimientos() {
        int n = 0;
        for (Pqr pendiente : repo.silenciosSinReconocer()) {
            try {
                tx.executeWithoutResult(s -> repo.findById(pendiente.getId())
                        .ifPresent(p -> avisos.reconocimientoPendiente(p, jefes())));
                n++;
            } catch (RuntimeException e) {
                log.warn("No se pudo recordar el reconocimiento de la PQR {}: {}", pendiente.getId(), e.getMessage());
            }
        }
        return n;
    }

    private List<Long> jefes() {
        return consultas.cargas("JEFE_PQR", null).stream().map(PqrConsultas.Carga::id).toList();
    }
}
