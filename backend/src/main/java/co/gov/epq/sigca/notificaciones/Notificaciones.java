package co.gov.epq.sigca.notificaciones;

import java.time.LocalDateTime;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.catalogo.Parametros;

/**
 * Cola de notificaciones (RF-28, RN-23). Los módulos encolan dentro de su transacción; un proceso programado
 * envía correos y SMS con reintentos espaciados. Las de canal SISTEMA son la bandeja interna del funcionario.
 */
@Service
public class Notificaciones {

    private static final Logger log = LoggerFactory.getLogger(Notificaciones.class);

    private final NotificacionRepository repo;
    private final EnviadorCorreo correo;
    private final EnviadorSms sms;
    private final Parametros parametros;

    public Notificaciones(NotificacionRepository repo, EnviadorCorreo correo, EnviadorSms sms, Parametros parametros) {
        this.repo = repo;
        this.correo = correo;
        this.sms = sms;
        this.parametros = parametros;
    }

    /** Correo a un ciudadano o a una dirección externa. Sin correo válido no se encola (el CHECK lo rechazaría). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void correo(EventoNotificacion evento, String destinatario, Long usuarioId, String asunto, String cuerpo,
            String entidad, Long entidadId) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("Notificación {} sin correo de destino; se omite", evento);
            return;
        }
        Notificacion n = base(evento, CanalNotificacion.EMAIL, entidad, entidadId, cuerpo);
        n.setDestinatario(destinatario.trim().toLowerCase(Locale.ROOT));
        n.setUsuarioId(usuarioId);
        n.setAsunto(recortar(asunto, 200));
        repo.save(n);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sms(EventoNotificacion evento, String telefono, Long usuarioId, String texto, String entidad, Long entidadId) {
        if (telefono == null || telefono.isBlank()) {
            return;
        }
        Notificacion n = base(evento, CanalNotificacion.SMS, entidad, entidadId, texto);
        n.setDestinatario(telefono.trim());
        n.setUsuarioId(usuarioId);
        repo.save(n);
    }

    /** Aviso en la bandeja interna de un funcionario. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void funcionario(EventoNotificacion evento, Long funcionarioId, String asunto, String cuerpo, String entidad,
            Long entidadId) {
        if (funcionarioId == null) {
            return;
        }
        Notificacion n = base(evento, CanalNotificacion.SISTEMA, entidad, entidadId, cuerpo);
        n.setFuncionarioId(funcionarioId);
        n.setAsunto(recortar(asunto, 200));
        // La bandeja no se "envía": queda disponible de inmediato
        n.setEstado(EstadoNotificacion.ENVIADA);
        n.setEnviadaEn(LocalDateTime.now());
        repo.save(n);
    }

    private static Notificacion base(EventoNotificacion evento, CanalNotificacion canal, String entidad, Long entidadId,
            String cuerpo) {
        Notificacion n = new Notificacion();
        n.setEvento(evento);
        n.setCanal(canal);
        n.setCuerpo(cuerpo);
        n.setEntidad(entidad);
        n.setEntidadId(entidadId);
        n.setEstado(EstadoNotificacion.PENDIENTE);
        n.setIntentos(0);
        n.setLeida(false);
        n.setProximoIntento(LocalDateTime.now());
        return n;
    }

    /** Envía lo pendiente. Cada fallo espera el doble que el anterior hasta NOTIFICACION_MAX_INTENTOS. */
    @Scheduled(fixedDelayString = "${sigca.integraciones.correo.intervalo-ms:30000}", initialDelay = 20000)
    @Transactional
    public void despachar() {
        int max = parametros.entero(Parametros.NOTIFICACION_MAX_INTENTOS);
        LocalDateTime ahora = LocalDateTime.now();
        for (Notificacion n : repo.pendientesDeEnvio(ahora, PageRequest.of(0, 50))) {
            try {
                if (n.getCanal() == CanalNotificacion.EMAIL) {
                    correo.enviar(n.getDestinatario(), n.getAsunto(), n.getCuerpo());
                } else {
                    sms.enviar(n.getDestinatario(), n.getCuerpo());
                }
                n.setEstado(EstadoNotificacion.ENVIADA);
                n.setEnviadaEn(LocalDateTime.now());
                n.setUltimoError(null);
            } catch (RuntimeException e) {
                int intentos = n.getIntentos() + 1;
                n.setIntentos(intentos);
                n.setUltimoError(recortar(e.getMessage(), 500));
                if (intentos >= max) {
                    n.setEstado(EstadoNotificacion.FALLIDA);
                } else {
                    n.setProximoIntento(ahora.plusMinutes(1L << Math.min(intentos, 10)));
                }
                log.warn("Fallo enviando notificación {} (intento {}): {}", n.getId(), intentos, e.getMessage());
            }
        }
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
