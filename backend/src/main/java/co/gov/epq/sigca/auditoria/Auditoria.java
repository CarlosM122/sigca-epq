package co.gov.epq.sigca.auditoria;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import co.gov.epq.sigca.seguridad.UsuarioActual;
import tools.jackson.databind.json.JsonMapper;

/**
 * Log de auditoría (RF-15, RNF-16). La fecha la pone la BD y la tabla es de solo inserción.
 * El detalle es JSON (CK_LOG_DETALLE_JSON) y nunca debe llevar contraseñas, tokens ni identificaciones completas.
 */
@Service
public class Auditoria {

    private static final Logger log = LoggerFactory.getLogger(Auditoria.class);

    private final LogAuditoriaRepository repo;
    private final JsonMapper json;

    public Auditoria(LogAuditoriaRepository repo, JsonMapper json) {
        this.repo = repo;
        this.json = json;
    }

    /** Operación exitosa: se guarda en la misma transacción, así un rollback no deja rastro falso. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void exito(OperacionAuditoria op, String entidad, Long entidadId, Map<String, ?> detalle) {
        guardar(op, entidad, entidadId, ResultadoAuditoria.EXITO, detalle, actor());
    }

    /** Intento fallido (login, pago rechazado): transacción propia para que sobreviva al rollback del negocio. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallo(OperacionAuditoria op, String entidad, Long entidadId, Map<String, ?> detalle) {
        guardar(op, entidad, entidadId, ResultadoAuditoria.FALLO, detalle, actor());
    }

    /** Para operaciones previas a la autenticación (login, activación), donde el actor aún no está en el contexto. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void como(Actor actor, OperacionAuditoria op, ResultadoAuditoria resultado, String entidad, Long entidadId,
            Map<String, ?> detalle) {
        guardar(op, entidad, entidadId, resultado, detalle, actor);
    }

    private void guardar(OperacionAuditoria op, String entidad, Long entidadId, ResultadoAuditoria resultado,
            Map<String, ?> detalle, Actor actor) {
        LogAuditoria l = new LogAuditoria();
        l.setActorTipo(actor.tipo());
        l.setFuncionarioId(actor.funcionarioId());
        l.setUsuarioId(actor.usuarioId());
        l.setOperacion(op);
        l.setEntidad(entidad);
        l.setEntidadId(entidadId);
        l.setResultado(resultado);
        l.setDetalle(detalle == null || detalle.isEmpty() ? null : json.writeValueAsString(detalle));
        l.setIpOrigen(ipActual());
        repo.save(l);
        log.debug("Auditoría {} {} {}#{}", op, resultado, entidad, entidadId);
    }

    private static Actor actor() {
        return UsuarioActual.opcional()
                .map(u -> u.esCiudadano() ? Actor.ciudadano(u.usuarioId()) : Actor.funcionario(u.funcionarioId()))
                .orElse(Actor.SISTEMA);
    }

    public static String ipActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String ip = attrs.getRequest().getRemoteAddr();
            return ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip;
        }
        return null;
    }

    public record Actor(ActorTipo tipo, Long funcionarioId, Long usuarioId) {

        public static final Actor SISTEMA = new Actor(ActorTipo.SISTEMA, null, null);
        public static final Actor ANONIMO = new Actor(ActorTipo.ANONIMO, null, null);

        public static Actor funcionario(Long id) {
            return new Actor(ActorTipo.FUNCIONARIO, id, null);
        }

        public static Actor ciudadano(Long usuarioId) {
            return new Actor(ActorTipo.CIUDADANO, null, usuarioId);
        }
    }
}
