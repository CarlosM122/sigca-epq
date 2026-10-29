package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.cartera.AcuerdoPago;
import co.gov.epq.sigca.cartera.CuotaAcuerdo;
import co.gov.epq.sigca.cartera.EstadoAcuerdo;
import co.gov.epq.sigca.cartera.EstadoCuota;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.facturacion.Factura;
import co.gov.epq.sigca.facturacion.FacturaRepository;
import co.gov.epq.sigca.facturacion.FacturacionUsuariosRepository;
import co.gov.epq.sigca.facturacion.LineaFacturaRepository;
import co.gov.epq.sigca.usuarios.Usuario;

/**
 * Resuelve la obligación a pagar a partir de su referencia (P-18) o de su id, y dice si admite pago y por cuánto.
 * Las cuotas se pagan en orden (TRG_PAGO_BI): una referencia de acuerdo sin cuota toma la siguiente pendiente.
 */
@Component
@Transactional(readOnly = true)
public class Obligaciones {

    /** Formato de la referencia de pago (P-18); lo usa la validación de {@link PagoController.PagoRequest}. */
    public static final String PATRON_REFERENCIA = "^(FAC-\\d{6}-\\d{7}|AP-\\d{4}-\\d{6}(-\\d{2})?)$";
    private static final Pattern FACTURA = Pattern.compile("^FAC-\\d{6}-\\d{7}$");
    private static final Pattern ACUERDO = Pattern.compile("^(AP-\\d{4}-\\d{6})(?:-(\\d{2}))?$");

    private final FacturaRepository facturas;
    private final LineaFacturaRepository lineas;
    private final PagosCuotasRepository cuotas;
    private final PagosAcuerdosRepository acuerdos;
    private final FacturacionUsuariosRepository usuarios;

    public Obligaciones(FacturaRepository facturas, LineaFacturaRepository lineas, PagosCuotasRepository cuotas,
            PagosAcuerdosRepository acuerdos, FacturacionUsuariosRepository usuarios) {
        this.facturas = facturas;
        this.lineas = lineas;
        this.cuotas = cuotas;
        this.acuerdos = acuerdos;
        this.usuarios = usuarios;
    }

    public Obligacion porReferencia(String referencia) {
        String ref = referencia == null ? "" : referencia.trim().toUpperCase();
        if (FACTURA.matcher(ref).matches()) {
            return deFactura(facturas.findByNumeroFactura(ref)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Factura", ref)));
        }
        Matcher m = ACUERDO.matcher(ref);
        if (m.matches()) {
            AcuerdoPago a = acuerdos.findByNumeroAcuerdo(m.group(1))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Acuerdo de pago", m.group(1)));
            CuotaAcuerdo c = m.group(2) != null
                    ? cuotas.findByAcuerdoIdAndNumeroCuota(a.getId(), Integer.valueOf(m.group(2)))
                            .orElseThrow(() -> new RecursoNoEncontradoException("Cuota", ref))
                    : siguientePendiente(a).orElseThrow(() -> new NegocioException("SIN_CUOTAS_PENDIENTES",
                            "El acuerdo " + a.getNumeroAcuerdo() + " no tiene cuotas pendientes"));
            return deCuota(c, a);
        }
        throw new NegocioException("REFERENCIA_INVALIDA",
                "La referencia debe ser un número de factura (FAC-AAAAMM-NNNNNNN) o de acuerdo (AP-AAAA-NNNNNN[-NN])");
    }

    public Obligacion porId(TipoObligacion tipo, Long id) {
        if (tipo == TipoObligacion.FACTURA) {
            return deFactura(facturas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Factura", id)));
        }
        CuotaAcuerdo c = cuotas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cuota", id));
        return deCuota(c, acuerdos.findById(c.getAcuerdoId()).orElseThrow());
    }

    public Optional<CuotaAcuerdo> siguientePendiente(AcuerdoPago a) {
        return cuotas.findByAcuerdoIdOrderByNumeroCuota(a.getId()).stream()
                .filter(c -> c.getEstado() != EstadoCuota.PAGADA).findFirst()
                .filter(c -> c.getEstado() == EstadoCuota.PENDIENTE);
    }

    private Obligacion deFactura(Factura f) {
        Usuario u = usuarios.findById(f.getUsuarioId()).orElseThrow();
        BigDecimal exigible = f.getSaldoPendiente().subtract(f.getValorEnReclamo());
        String motivo = null;
        switch (f.getEstado()) {
            case PAGADA -> motivo = "La factura ya está pagada";
            case ANULADA -> motivo = "La factura está anulada";
            case EN_ACUERDO -> motivo = "La factura está incluida en el acuerdo de pago "
                    + acuerdos.numerosPorFactura(f.getId()).stream().findFirst().orElse("")
                    + ": se pagan las cuotas del acuerdo";
            default -> {
                if (exigible.signum() <= 0) {
                    motivo = "Todo el saldo de la factura está en reclamo y no es exigible mientras se resuelve (RN-33)";
                }
            }
        }
        return new Obligacion(TipoObligacion.FACTURA, f.getId(), f.getNumeroFactura(), u.getId(), u.getCodigo(),
                u.getNombreCompleto(), f.getEstado().name(), f.getSaldoPendiente(), f.getValorEnReclamo(),
                exigible.max(BigDecimal.ZERO), f.getFechaVencimiento(), motivo == null, motivo, lineas.detalle(f.getId()));
    }

    private Obligacion deCuota(CuotaAcuerdo c, AcuerdoPago a) {
        Usuario u = usuarios.findById(a.getUsuarioId()).orElseThrow();
        String motivo = null;
        if (c.getEstado() == EstadoCuota.PAGADA) {
            motivo = "La cuota ya está pagada";
        } else if (a.getEstado() != EstadoAcuerdo.VIGENTE) {
            motivo = "El acuerdo de pago está " + a.getEstado();
        } else if (c.getEstado() != EstadoCuota.PENDIENTE) {
            motivo = "La cuota está " + c.getEstado();
        } else {
            Optional<CuotaAcuerdo> siguiente = siguientePendiente(a);
            if (siguiente.isPresent() && !siguiente.get().getId().equals(c.getId())) {
                motivo = "Las cuotas se pagan en orden: primero la cuota " + siguiente.get().getNumeroCuota();
            }
        }
        return new Obligacion(TipoObligacion.CUOTA, c.getId(), Obligacion.referenciaCuota(a.getNumeroAcuerdo(),
                c.getNumeroCuota()), u.getId(), u.getCodigo(), u.getNombreCompleto(), c.getEstado().name(),
                c.getValorCuota(), BigDecimal.ZERO, c.getValorCuota(), c.getFechaVencimiento(), motivo == null, motivo,
                List.of());
    }
}
