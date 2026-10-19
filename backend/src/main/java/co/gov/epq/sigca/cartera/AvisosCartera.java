package co.gov.epq.sigca.cartera;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;

/** Notificaciones de cartera (RF-28): correo y, si hay celular, SMS al titular; bandeja a los asesores. */
@Component
public class AvisosCartera {

    private static final Pattern CELULAR = Pattern.compile("^(\\+57)?3[0-9]{9}$");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));

    private final Notificaciones notificaciones;

    public AvisosCartera(Notificaciones notificaciones) {
        this.notificaciones = notificaciones;
    }

    void avisoSuspension(CarteraConsultas.UsuarioCartera u, CobroPersuasivo c) {
        String texto = "EPQ: su cuenta registra " + c.getPeriodosMora() + " periodos en mora por " + pesos(c.getValorDeuda())
                + ". Si no paga o firma un acuerdo, se ordenará la suspensión del servicio.";
        notificaciones.correo(EventoNotificacion.AVISO_SUSPENSION, u.correo(), u.id(), "Aviso previo de suspensión del servicio",
                "Hola " + u.nombre() + ".\n\n" + texto
                        + "\n\nPuede pagar en el portal o acercarse a nuestras oficinas para suscribir un acuerdo de pago.",
                "COBRO_PERSUASIVO", c.getId());
        sms(u, EventoNotificacion.AVISO_SUSPENSION, texto, "COBRO_PERSUASIVO", c.getId());
    }

    void ordenSuspension(CarteraConsultas.UsuarioCartera u, List<String> contratos, Long cobroId, List<Long> asesores,
            List<String> ordenes) {
        notificaciones.correo(EventoNotificacion.ORDEN_SUSPENSION, u.correo(), u.id(), "Orden de suspensión del servicio",
                "Hola " + u.nombre() + ".\n\nPor mora en el pago se ordenó la suspensión de los contratos "
                        + String.join(", ", contratos) + ". Al pagar o firmar un acuerdo de pago se ordena la reconexión.",
                "COBRO_PERSUASIVO", cobroId);
        for (Long a : asesores) {
            notificaciones.funcionario(EventoNotificacion.ORDEN_SUSPENSION, a, "Órdenes de suspensión por mora",
                    "Se generaron las órdenes " + String.join(", ", ordenes) + " por mora.", "COBRO_PERSUASIVO", cobroId);
        }
    }

    void acuerdoCreado(CarteraConsultas.UsuarioCartera u, AcuerdoPago a, CalculadoraCuotas.Plan plan, String numero) {
        StringBuilder sb = new StringBuilder("Hola ").append(u.nombre()).append(".\n\nSuscribió el acuerdo de pago ")
                .append(numero).append(" por ").append(pesos(a.getTotalDeuda())).append(" en ").append(plan.numeroCuotas())
                .append(" cuotas mensuales más la cuota inicial.\n\nPlan de pagos:\n");
        for (CalculadoraCuotas.Cuota c : plan.cuotas()) {
            sb.append(" - Cuota ").append(c.numero()).append(": ").append(pesos(c.valor())).append(", vence el ")
                    .append(FECHA.format(c.fechaVencimiento())).append('\n');
        }
        sb.append("\nSi una cuota no se paga a tiempo el acuerdo se incumple y la deuda vuelve a cobro.");
        notificaciones.correo(EventoNotificacion.ACUERDO_CREADO, u.correo(), u.id(), "Acuerdo de pago " + numero,
                sb.toString(), "ACUERDO_PAGO", a.getId());
    }

    void incumplido(CarteraConsultas.UsuarioCartera u, AcuerdoPago a, List<CuotaAcuerdo> vencidas) {
        CuotaAcuerdo primera = vencidas.getFirst();
        notificaciones.correo(EventoNotificacion.CUOTA_VENCIDA, u.correo(), u.id(), "Cuota vencida del acuerdo " + a.getNumeroAcuerdo(),
                "Hola " + u.nombre() + ".\n\nLa cuota " + primera.getNumeroCuota() + " del acuerdo " + a.getNumeroAcuerdo()
                        + " por " + pesos(primera.getValorCuota()) + " venció el " + FECHA.format(primera.getFechaVencimiento())
                        + " sin pago.", "ACUERDO_PAGO", a.getId());
        String texto = "EPQ: el acuerdo de pago " + a.getNumeroAcuerdo()
                + " quedó incumplido por cuota vencida. Las facturas vuelven a cobro.";
        notificaciones.correo(EventoNotificacion.ACUERDO_INCUMPLIDO, u.correo(), u.id(), "Acuerdo " + a.getNumeroAcuerdo() + " incumplido",
                "Hola " + u.nombre() + ".\n\n" + texto, "ACUERDO_PAGO", a.getId());
        sms(u, EventoNotificacion.ACUERDO_INCUMPLIDO, texto, "ACUERDO_PAGO", a.getId());
    }

    private void sms(CarteraConsultas.UsuarioCartera u, EventoNotificacion e, String texto, String entidad, Long id) {
        String tel = u.telefono() == null ? "" : u.telefono().replace(" ", "");
        if (CELULAR.matcher(tel).matches()) {
            notificaciones.sms(e, tel, u.id(), texto.length() > 300 ? texto.substring(0, 300) : texto, entidad, id);
        }
    }

    static String pesos(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        f.setMaximumFractionDigits(2);
        return f.format(v);
    }
}
