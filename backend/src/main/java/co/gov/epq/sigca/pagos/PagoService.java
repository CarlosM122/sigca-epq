package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.cartera.AcuerdoPago;
import co.gov.epq.sigca.cartera.CuotaAcuerdo;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.facturacion.DocumentoPdf;
import co.gov.epq.sigca.facturacion.EstadoSyncErp;
import co.gov.epq.sigca.facturacion.Factura;
import co.gov.epq.sigca.facturacion.FacturaRepository;
import co.gov.epq.sigca.facturacion.FacturacionUsuariosRepository;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;
import co.gov.epq.sigca.seguridad.UsuarioActual;
import co.gov.epq.sigca.usuarios.Usuario;
import jakarta.persistence.EntityManager;

/**
 * Registro y consulta de pagos (RF-06, CU-04). Sin abonos libres: se paga exactamente el valor exigible de la
 * factura o la cuota (P-05, CO-02); TRG_PAGO_BI lo vuelve a exigir y TRG_PAGO_AI salda líneas, factura, cuota,
 * acuerdo y ordena la reconexión (RN-36).
 */
@Service
public class PagoService {

    public static final int TAMANO_MAXIMO = 100;

    private final PagoRepository pagos;
    private final Obligaciones obligaciones;
    private final FacturaRepository facturas;
    private final PagosCuotasRepository cuotas;
    private final PagosAcuerdosRepository acuerdos;
    private final FacturacionUsuariosRepository usuarios;
    private final Auditoria auditoria;
    private final Notificaciones notificaciones;
    private final EntityManager em;

    public PagoService(PagoRepository pagos, Obligaciones obligaciones, FacturaRepository facturas,
            PagosCuotasRepository cuotas, PagosAcuerdosRepository acuerdos, FacturacionUsuariosRepository usuarios,
            Auditoria auditoria, Notificaciones notificaciones, EntityManager em) {
        this.pagos = pagos;
        this.obligaciones = obligaciones;
        this.facturas = facturas;
        this.cuotas = cuotas;
        this.acuerdos = acuerdos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.em = em;
    }

    /** Pago en caja (PRESENCIAL) o recaudo bancario registrado a mano (BANCO, P-23). */
    @Transactional
    public PagoVista registrarPresencial(String referencia, CanalPago canal, BigDecimal valor, String referenciaBanco) {
        UsuarioActual funcionario = UsuarioActual.funcionario();
        if (canal == CanalPago.EN_LINEA) {
            throw new NegocioException("CANAL_INVALIDO", "Los pagos en línea solo los confirma la pasarela (RN-05)");
        }
        String refBanco = referenciaBanco == null || referenciaBanco.isBlank() ? null : referenciaBanco.trim().toUpperCase();
        if (canal == CanalPago.BANCO && refBanco == null) {
            throw new NegocioException("REFERENCIA_BANCO_REQUERIDA", "Indique la referencia del recaudo bancario");
        }
        Obligacion o = obligaciones.porReferencia(referencia);
        exigirPagable(o, valor);
        Pago p = aplicar(o, canal, valor, funcionario.funcionarioId(), canal == CanalPago.BANCO ? refBanco : null, null, null);
        return vista(p);
    }

    /** Verifica antes de insertar que la obligación admite pago y que el valor es el exigible. */
    public static void exigirPagable(Obligacion o, BigDecimal valor) {
        if (!o.admitePago()) {
            throw new ConflictoException("NO_ADMITE_PAGOS", o.motivoNoAdmite());
        }
        if (valor == null || valor.compareTo(o.valorExigible()) != 0) {
            throw new NegocioException("VALOR_NO_COINCIDE", "El valor debe ser exactamente el valor exigible de "
                    + DocumentoPdf.pesos(o.valorExigible()) + " (no se reciben abonos, P-05)");
        }
    }

    /**
     * Inserta el pago en la transacción actual, relee la factura o la cuota (los triggers cambiaron su estado),
     * encola el comprobante y audita.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Pago aplicar(Obligacion o, CanalPago canal, BigDecimal valor, Long funcionarioId, String referenciaBanco,
            Long transaccionId, String codigoTransaccion) {
        Usuario u = usuarios.findById(o.usuarioId()).orElseThrow();
        boolean conCorreo = u.getCorreo() != null && !u.getCorreo().isBlank();
        Pago p = new Pago();
        p.setUsuarioId(o.usuarioId());
        if (o.tipo() == TipoObligacion.FACTURA) {
            p.setFacturaId(o.id());
        } else {
            p.setCuotaId(o.id());
        }
        p.setValor(valor);
        p.setCanalPago(canal);
        p.setFuncionarioId(funcionarioId);
        p.setReferenciaBanco(referenciaBanco);
        p.setTransaccionPasarelaId(transaccionId);
        p.setCodigoTransaccion(codigoTransaccion);
        p.setComprobanteEnviado(conCorreo);
        p.setEstadoSyncErp(EstadoSyncErp.PENDIENTE);
        pagos.saveAndFlush(p);
        refrescarObligacion(o);

        notificaciones.correo(EventoNotificacion.COMPROBANTE_PAGO, u.getCorreo(), u.getId(),
                "Comprobante de pago " + p.getCodigoPago(),
                "Hola " + u.getNombreCompleto() + ",\n\nRecibimos su pago.\n"
                        + "Comprobante: " + p.getCodigoPago() + "\n"
                        + "Referencia pagada: " + o.referencia() + "\n"
                        + "Valor: " + DocumentoPdf.pesos(p.getValor()) + "\n"
                        + "Fecha: " + DocumentoPdf.fechaHora(p.getFechaPago()) + "\n"
                        + "Canal: " + canal + (codigoTransaccion != null ? " (transacción " + codigoTransaccion + ")" : "")
                        + "\n\nPuede descargar el comprobante en el portal de EPQ.\n",
                "PAGO", p.getId());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("codigoPago", p.getCodigoPago());
        detalle.put("referencia", o.referencia());
        detalle.put("valor", p.getValor());
        detalle.put("canal", canal);
        if (codigoTransaccion != null) {
            detalle.put("codigoTransaccion", codigoTransaccion);
        }
        if (referenciaBanco != null) {
            detalle.put("referenciaBanco", referenciaBanco);
        }
        auditoria.exito(OperacionAuditoria.REGISTRAR_PAGO, "PAGO", p.getId(), detalle);
        return p;
    }

    private void refrescarObligacion(Obligacion o) {
        if (o.tipo() == TipoObligacion.FACTURA) {
            facturas.findById(o.id()).ifPresent(em::refresh);
        } else {
            CuotaAcuerdo c = cuotas.findById(o.id()).orElseThrow();
            em.refresh(c);
            acuerdos.findById(c.getAcuerdoId()).ifPresent(em::refresh);
        }
    }

    public record Filtro(String codigo, String usuario, Long usuarioId, String factura, CanalPago canal, LocalDate desde,
            LocalDate hasta) {
    }

    @Transactional(readOnly = true)
    public Page<PagoResumen> buscar(Filtro f, int pagina, int tamano) {
        return pagos.buscar(mayus(f.codigo()), mayus(f.usuario()), f.usuarioId(), mayus(f.factura()), f.canal(),
                f.desde() == null ? null : f.desde().atStartOfDay(),
                f.hasta() == null ? null : f.hasta().plusDays(1).atStartOfDay(),
                PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO)));
    }

    @Transactional(readOnly = true)
    public Pago obtener(Long id) {
        return pagos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Pago", id));
    }

    @Transactional(readOnly = true)
    public PagoVista detalle(Long id) {
        return vista(obtener(id));
    }

    @Transactional(readOnly = true)
    public PagoVista vista(Pago p) {
        Usuario u = usuarios.findById(p.getUsuarioId()).orElseThrow();
        if (p.getFacturaId() != null) {
            Factura f = facturas.findById(p.getFacturaId()).orElseThrow();
            return new PagoVista(p.getId(), p.getCodigoPago(), TipoObligacion.FACTURA, f.getId(), f.getNumeroFactura(),
                    u.getCodigo(), u.getNombreCompleto(), p.getValor(), p.getCanalPago(), p.getFechaPago(),
                    p.getCodigoTransaccion(), p.getReferenciaBanco(), f.getEstado().name(), f.getSaldoPendiente(),
                    f.getValorEnReclamo(), Boolean.TRUE.equals(p.getComprobanteEnviado()));
        }
        CuotaAcuerdo c = cuotas.findById(p.getCuotaId()).orElseThrow();
        AcuerdoPago a = acuerdos.findById(c.getAcuerdoId()).orElseThrow();
        return new PagoVista(p.getId(), p.getCodigoPago(), TipoObligacion.CUOTA, c.getId(),
                Obligacion.referenciaCuota(a.getNumeroAcuerdo(), c.getNumeroCuota()), u.getCodigo(), u.getNombreCompleto(),
                p.getValor(), p.getCanalPago(), p.getFechaPago(), p.getCodigoTransaccion(), p.getReferenciaBanco(),
                c.getEstado().name() + " · acuerdo " + a.getEstado(), null, null,
                Boolean.TRUE.equals(p.getComprobanteEnviado()));
    }

    /** Comprobante electrónico de pago (SWR-08). */
    @Transactional(readOnly = true)
    public byte[] comprobante(Long id) {
        PagoVista v = detalle(id);
        return DocumentoPdf.generar("Comprobante de pago " + v.codigoPago(), doc -> doc.add(DocumentoPdf.datos(
                "Comprobante", v.codigoPago(),
                "Fecha y hora", DocumentoPdf.fechaHora(v.fechaPago()),
                "Titular", v.titular() + " (" + v.codigoUsuario() + ")",
                "Referencia pagada", v.referencia(),
                "Tipo", v.tipoObligacion() == TipoObligacion.FACTURA ? "Factura" : "Cuota de acuerdo de pago",
                "Canal", v.canal().name(),
                "Valor pagado", DocumentoPdf.pesos(v.valor()),
                "Código de transacción", v.codigoTransaccion(),
                "Referencia bancaria", v.referenciaBanco(),
                "Estado de la obligación", v.estadoObligacion())));
    }

    static String mayus(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }

}
