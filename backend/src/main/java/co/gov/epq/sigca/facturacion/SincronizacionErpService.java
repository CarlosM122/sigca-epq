package co.gov.epq.sigca.facturacion;

import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.pagos.Pago;
import co.gov.epq.sigca.pagos.PagoRepository;
import co.gov.epq.sigca.usuarios.Usuario;

/**
 * Envía al ERP las facturas y pagos pendientes (SWR-17). Cada registro en su transacción: un fallo lo deja en
 * ERROR y se reintenta en la siguiente corrida. Si el ERP no responde, la operación comercial sigue (MOD-03).
 */
@Service
public class SincronizacionErpService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionErpService.class);
    private static final int LOTE = 200;

    private final ErpFinanciero erp;
    private final FacturaRepository facturas;
    private final PagoRepository pagos;
    private final FacturacionUsuariosRepository usuarios;
    private final Auditoria auditoria;
    private final TransactionTemplate nuevaTx;

    public SincronizacionErpService(ErpFinanciero erp, FacturaRepository facturas, PagoRepository pagos,
            FacturacionUsuariosRepository usuarios, Auditoria auditoria, PlatformTransactionManager tm) {
        this.erp = erp;
        this.facturas = facturas;
        this.pagos = pagos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.nuevaTx = new TransactionTemplate(tm);
        this.nuevaTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Resultado(int facturas, int pagos, int errores) {
    }

    @Scheduled(cron = "${sigca.integraciones.erp.cron:0 30 2 * * *}", zone = "America/Bogota")
    public void programado() {
        sincronizar();
    }

    public Resultado sincronizar() {
        int okF = 0;
        int okP = 0;
        int errores = 0;
        for (Long id : facturas.pendientesErp(PageRequest.of(0, LOTE))) {
            if (Boolean.TRUE.equals(nuevaTx.execute(s -> factura(id)))) {
                okF++;
            } else {
                errores++;
            }
        }
        for (Long id : pagos.pendientesErp(PageRequest.of(0, LOTE))) {
            if (Boolean.TRUE.equals(nuevaTx.execute(s -> pago(id)))) {
                okP++;
            } else {
                errores++;
            }
        }
        Resultado r = new Resultado(okF, okP, errores);
        if (okF + okP + errores > 0) {
            nuevaTx.executeWithoutResult(s -> auditoria.exito(OperacionAuditoria.SINCRONIZAR_ERP, "ERP", null,
                    Map.of("facturas", r.facturas(), "pagos", r.pagos(), "errores", r.errores())));
            log.info("Sincronización ERP: {} facturas, {} pagos, {} errores", okF, okP, errores);
        }
        return r;
    }

    private boolean factura(Long id) {
        Factura f = facturas.findById(id).orElseThrow();
        try {
            String ref = erp.registrarFactura(new ErpFinanciero.FacturaErp(f.getNumeroFactura(), codigo(f.getUsuarioId()),
                    f.getPeriodo(), f.getFechaEmision(), f.getFechaVencimiento(), f.getTotalAPagar(), f.getEstado().name()));
            f.setReferenciaErp(ref);
            f.setFechaSyncErp(LocalDateTime.now(Periodos.ZONA));
            f.setEstadoSyncErp(EstadoSyncErp.SINCRONIZADA);
            return true;
        } catch (RuntimeException e) {
            log.warn("ERP rechazó la factura {}: {}", f.getNumeroFactura(), e.getMessage());
            f.setEstadoSyncErp(EstadoSyncErp.ERROR);
            return false;
        }
    }

    private boolean pago(Long id) {
        Pago p = pagos.findById(id).orElseThrow();
        try {
            String referencia = p.getFacturaId() != null
                    ? facturas.findById(p.getFacturaId()).map(Factura::getNumeroFactura).orElse(null)
                    : "CUOTA-" + p.getCuotaId();
            String ref = erp.registrarPago(new ErpFinanciero.PagoErp(p.getCodigoPago(), codigo(p.getUsuarioId()), referencia,
                    p.getFechaPago(), p.getValor(), p.getCanalPago().name()));
            p.setReferenciaErp(ref);
            p.setFechaSyncErp(LocalDateTime.now(Periodos.ZONA));
            p.setEstadoSyncErp(EstadoSyncErp.SINCRONIZADA);
            return true;
        } catch (RuntimeException e) {
            log.warn("ERP rechazó el pago {}: {}", p.getCodigoPago(), e.getMessage());
            p.setEstadoSyncErp(EstadoSyncErp.ERROR);
            return false;
        }
    }

    private String codigo(Long usuarioId) {
        return usuarios.findById(usuarioId).map(Usuario::getCodigo).orElse(null);
    }

}
