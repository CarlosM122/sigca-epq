package co.gov.epq.sigca.pagos;

import org.springframework.stereotype.Component;

import co.gov.epq.sigca.facturacion.DocumentoPdf;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;
import co.gov.epq.sigca.usuarios.Usuario;

/**
 * Texto y destinatario del correo con el comprobante de pago (SWR-08), como {@code AvisosPqr} en PQR. Se encola en la
 * transacción del pago: si el pago se revierte, el correo no sale.
 */
@Component
public class AvisosPago {

    private static final String ENTIDAD = "PAGO";

    private final Notificaciones notificaciones;

    public AvisosPago(Notificaciones notificaciones) {
        this.notificaciones = notificaciones;
    }

    /** Correo al titular con los datos del pago ya registrado (código y fecha los asigna la BD). */
    void comprobante(Pago p, Obligacion o, Usuario titular) {
        notificaciones.correo(EventoNotificacion.COMPROBANTE_PAGO, titular.getCorreo(), titular.getId(),
                "Comprobante de pago " + p.getCodigoPago(), cuerpo(p, o, titular), ENTIDAD, p.getId());
    }

    static String cuerpo(Pago p, Obligacion o, Usuario titular) {
        String codigoTransaccion = p.getCodigoTransaccion();
        return "Hola " + titular.getNombreCompleto() + ",\n\nRecibimos su pago.\n"
                + "Comprobante: " + p.getCodigoPago() + "\n"
                + "Referencia pagada: " + o.referencia() + "\n"
                + "Valor: " + DocumentoPdf.pesos(p.getValor()) + "\n"
                + "Fecha: " + DocumentoPdf.fechaHora(p.getFechaPago()) + "\n"
                + "Canal: " + p.getCanalPago() + (codigoTransaccion != null ? " (transacción " + codigoTransaccion + ")" : "")
                + "\n\nPuede descargar el comprobante en el portal de EPQ.\n";
    }
}
