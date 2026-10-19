package co.gov.epq.sigca.seguridad;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta de inicio de sesión, cambio de contraseña y renovación. Con {@code cambioPasswordRequerido} el
 * token no trae permisos: solo sirve para cambiar la contraseña temporal.
 */
public record Sesion(String token, Instant expiraEn, UsuarioActual.TipoActor tipo, String nombre, List<String> roles,
        List<String> permisos, Long municipioId, boolean cambioPasswordRequerido) {
}
