package co.gov.epq.sigca.usuarios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RN-11: reintenta cada hora la verificación RNEC de los usuarios que quedaron PENDIENTES. */
@Component
public class VerificacionRnecProgramada {

    private static final Logger log = LoggerFactory.getLogger(VerificacionRnecProgramada.class);

    private final UsuarioRepository usuarios;
    private final UsuarioService servicio;

    public VerificacionRnecProgramada(UsuarioRepository usuarios, UsuarioService servicio) {
        this.usuarios = usuarios;
        this.servicio = servicio;
    }

    @Scheduled(cron = "${sigca.integraciones.rnec.cron-reverificacion:0 15 * * * *}", zone = "America/Bogota")
    public void reverificarPendientes() {
        var pendientes = usuarios.findByEstadoVerificacionRnecAndActivoTrueOrderById(EstadoVerificacionRnec.PENDIENTE,
                PageRequest.of(0, 100));
        for (Usuario u : pendientes) {
            try {
                servicio.verificarPendiente(u);
            } catch (ValidadorRnec.RnecNoDisponibleException e) {
                log.info("La RNEC sigue sin responder; se reintentará en la próxima ejecución");
                return;
            } catch (RuntimeException e) {
                log.warn("No fue posible reverificar el usuario {}: {}", u.getId(), e.getMessage());
            }
        }
    }
}
