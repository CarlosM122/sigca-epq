package co.gov.epq.sigca.pqr;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;

/**
 * Textos y destinatarios de las notificaciones de PQR (RF-28). El ciudadano recibe correo en el contacto que
 * registró; gestores y jefes, aviso en la bandeja interna. Se encolan en la transacción del trámite.
 */
@Component
public class AvisosPqr {

    static final String RECURSO_DIAS_PLAZO = "RECURSO_DIAS_PLAZO";
    private static final String ENTIDAD = "PQR";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));

    private final Notificaciones notificaciones;
    private final Parametros parametros;

    public AvisosPqr(Notificaciones notificaciones, Parametros parametros) {
        this.notificaciones = notificaciones;
        this.parametros = parametros;
    }

    void radicada(Pqr p) {
        ciudadano(p, EventoNotificacion.PQR_RADICADA, "Radicamos su solicitud " + p.getNumeroRadicado(),
                "Hola " + p.getNombreContacto() + ".\n\nRecibimos su " + tipo(p) + " con el radicado " + p.getNumeroRadicado()
                        + ". La responderemos a más tardar el " + FECHA.format(p.getFechaLimiteRespuesta())
                        + ".\nPuede consultar el estado con el radicado y su identificación en el portal de EPQ.");
    }

    void asignada(Pqr p) {
        notificaciones.funcionario(EventoNotificacion.PQR_ASIGNADA, p.getGestorId(), "PQR asignada: " + p.getNumeroRadicado(),
                "Se le asignó la " + tipo(p) + " " + p.getNumeroRadicado() + ", con fecha límite de respuesta el "
                        + FECHA.format(p.getFechaLimiteRespuesta()) + ".", ENTIDAD, p.getId());
    }

    void respondida(Pqr p) {
        String decision = p.getDecisionRecurso() == null ? "" : "\nDecisión sobre el recurso: " + p.getDecisionRecurso() + ".";
        ciudadano(p, EventoNotificacion.PQR_RESPONDIDA, "Respuesta a su solicitud " + p.getNumeroRadicado(),
                "Hola " + p.getNombreContacto() + ".\n\nEPQ respondió su " + tipo(p) + " " + p.getNumeroRadicado() + ":\n\n"
                        + p.getRespuesta() + decision + recursoDisponible(p));
    }

    void trasladada(Pqr p) {
        ciudadano(p, EventoNotificacion.PQR_TRASLADADA_SSPD, "Su recurso " + p.getNumeroRadicado() + " pasó a la Superservicios",
                "Hola " + p.getNombreContacto() + ".\n\nEPQ confirmó la decisión recurrida:\n\n" + p.getRespuesta()
                        + "\n\nComo pidió el recurso de apelación, el expediente se trasladó a la Superintendencia de Servicios"
                        + " Públicos Domiciliarios, que tomará la decisión final.");
    }

    void decisionSspd(Pqr p) {
        ciudadano(p, EventoNotificacion.PQR_RESPONDIDA, "Decisión de la Superservicios sobre " + p.getNumeroRadicado(),
                "Hola " + p.getNombreContacto() + ".\n\nLa Superintendencia de Servicios Públicos resolvió la apelación del recurso "
                        + p.getNumeroRadicado() + ". Decisión: " + p.getDecisionSspd() + ".");
    }

    void alertaVencimiento(Pqr p, Iterable<Long> jefes, int horas) {
        String texto = "La " + tipo(p) + " " + p.getNumeroRadicado() + " vence el " + FECHA.format(p.getFechaLimiteRespuesta())
                + " (quedan menos de " + horas + " horas) y aún no tiene respuesta.";
        for (Long f : responsables(p, jefes)) {
            notificaciones.funcionario(EventoNotificacion.PQR_ALERTA_48H, f, "PQR por vencer: " + p.getNumeroRadicado(),
                    texto, ENTIDAD, p.getId());
        }
    }

    void vencida(Pqr p, Iterable<Long> jefes) {
        for (Long f : responsables(p, jefes)) {
            notificaciones.funcionario(EventoNotificacion.PQR_VENCIDA, f, "PQR vencida: " + p.getNumeroRadicado(),
                    "La " + tipo(p) + " " + p.getNumeroRadicado() + " venció sin respuesta. Opera el silencio administrativo"
                            + " positivo: reconozca sus efectos antes de " + p.getFechaLimiteReconocimiento() + ".",
                    ENTIDAD, p.getId());
        }
        ciudadano(p, EventoNotificacion.PQR_SILENCIO_POSITIVO, "Silencio administrativo positivo en " + p.getNumeroRadicado(),
                "Hola " + p.getNombreContacto() + ".\n\nEPQ no respondió su " + tipo(p) + " " + p.getNumeroRadicado()
                        + " dentro del plazo legal. Opera a su favor el silencio administrativo positivo (Ley 142 de 1994,"
                        + " art. 158) y EPQ debe reconocer sus efectos.");
    }

    void reconocimientoPendiente(Pqr p, Iterable<Long> jefes) {
        for (Long f : responsables(p, jefes)) {
            notificaciones.funcionario(EventoNotificacion.PQR_ALERTA_RECONOCIMIENTO, f,
                    "Reconozca el silencio positivo de " + p.getNumeroRadicado(),
                    "El silencio administrativo positivo de la PQR " + p.getNumeroRadicado()
                            + " no se ha reconocido. Plazo: " + p.getFechaLimiteReconocimiento() + ".", ENTIDAD, p.getId());
        }
    }

    private void ciudadano(Pqr p, EventoNotificacion evento, String asunto, String cuerpo) {
        notificaciones.correo(evento, p.getCorreoContacto(), p.getUsuarioId(), asunto, cuerpo, ENTIDAD, p.getId());
    }

    private String recursoDisponible(Pqr p) {
        if (p.getTipo() == TipoPqr.RECURSO || p.getContratoId() == null) {
            return "";
        }
        return "\n\nSi no está de acuerdo, puede presentar un recurso de reposición (y en subsidio de apelación) dentro de"
                + " los " + parametros.entero(RECURSO_DIAS_PLAZO) + " días hábiles siguientes, desde el portal o en nuestras oficinas.";
    }

    private static Set<Long> responsables(Pqr p, Iterable<Long> jefes) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(p.getGestorId());
        jefes.forEach(ids::add);
        return ids;
    }

    private static String tipo(Pqr p) {
        return switch (p.getTipo()) {
            case PETICION -> "petición";
            case QUEJA -> "queja";
            case RECLAMO -> "reclamo";
            case RECURSO -> "recurso";
        };
    }

}
