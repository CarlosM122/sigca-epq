package co.gov.epq.sigca.seguridad;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import co.gov.epq.sigca.common.error.NegocioException;

/**
 * Quién hace la petición, tomado del JWT. Un funcionario trae {@code funcionarioId}; un ciudadano, {@code usuarioId}.
 */
public record UsuarioActual(TipoActor tipo, Long funcionarioId, Long usuarioId, String username, String nombre,
        List<String> roles, List<String> permisos, Long municipioId) {

    public enum TipoActor {
        FUNCIONARIO, CIUDADANO
    }

    public static Optional<UsuarioActual> opcional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(new UsuarioActual(
                TipoActor.valueOf(jwt.getClaimAsString(JwtService.TIPO)),
                largo(jwt, JwtService.FUNCIONARIO),
                largo(jwt, JwtService.USUARIO),
                jwt.getSubject(),
                jwt.getClaimAsString(JwtService.NOMBRE),
                lista(jwt, JwtService.ROLES),
                lista(jwt, JwtService.PERMISOS),
                largo(jwt, JwtService.MUNICIPIO)));
    }

    public static UsuarioActual requerido() {
        return opcional().orElseThrow(() -> new NegocioException("NO_AUTENTICADO", "Debe iniciar sesión", HttpStatus.UNAUTHORIZED));
    }

    /** Funcionario autenticado; falla si quien llama es un ciudadano. */
    public static UsuarioActual funcionario() {
        UsuarioActual u = requerido();
        if (u.tipo != TipoActor.FUNCIONARIO) {
            throw new NegocioException("ACCESO_DENEGADO", "Operación reservada a funcionarios de EPQ", HttpStatus.FORBIDDEN);
        }
        return u;
    }

    /** Ciudadano autenticado en el portal; falla si quien llama es un funcionario. */
    public static UsuarioActual ciudadano() {
        UsuarioActual u = requerido();
        if (u.tipo != TipoActor.CIUDADANO) {
            throw new NegocioException("ACCESO_DENEGADO", "Operación del portal ciudadano", HttpStatus.FORBIDDEN);
        }
        return u;
    }

    /** Nombre que queda en CREADO_POR / ACTUALIZADO_POR y en CLIENT_IDENTIFIER de la sesión Oracle. */
    public static String usernameOSistema() {
        return opcional().map(UsuarioActual::username).orElse("SISTEMA");
    }

    public boolean esCiudadano() {
        return tipo == TipoActor.CIUDADANO;
    }

    public boolean tienePermiso(String permiso) {
        return permisos.contains(permiso);
    }

    public boolean tieneRol(String rol) {
        return roles.contains(rol);
    }

    private static Long largo(Jwt jwt, String claim) {
        Object v = jwt.getClaim(claim);
        return v instanceof Number n ? n.longValue() : null;
    }

    private static List<String> lista(Jwt jwt, String claim) {
        List<String> v = jwt.getClaimAsStringList(claim);
        return v == null ? List.of() : List.copyOf(v);
    }
}
