package co.gov.epq.sigca.seguridad;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import co.gov.epq.sigca.seguridad.UsuarioActual.TipoActor;

/**
 * Emite los JWT de acceso. Los permisos viajan en el token y son las autoridades de Spring Security;
 * el claim {@code inicio} fija el comienzo de la sesión para limitar las renovaciones.
 */
@Service
public class JwtService {

    public static final String TIPO = "tipo";
    public static final String FUNCIONARIO = "fid";
    public static final String USUARIO = "uid";
    public static final String NOMBRE = "nombre";
    public static final String ROLES = "roles";
    public static final String PERMISOS = "permisos";
    public static final String MUNICIPIO = "municipio";
    public static final String INICIO = "inicio";
    public static final String CAMBIO_PASSWORD = "cambioPassword";

    private final JwtEncoder encoder;
    private final SeguridadProperties props;

    public JwtService(JwtEncoder encoder, SeguridadProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    /**
     * @param cambioPassword con la contraseña temporal el token no lleva permisos: solo sirve para cambiarla
     */
    public TokenEmitido emitir(TipoActor tipo, Long id, String sujeto, String nombre, List<String> roles,
            List<String> permisos, Long municipioId, boolean cambioPassword, Instant inicioSesion) {
        Instant ahora = Instant.now();
        Instant limite = inicioSesion.plus(props.duracionRenovacion());
        Instant expira = ahora.plus(props.duracionAcceso());
        if (expira.isAfter(limite)) {
            expira = limite;
        }
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("sigca-epq")
                .issuedAt(ahora)
                .expiresAt(expira)
                .subject(sujeto)
                .claim(TIPO, tipo.name())
                .claim(tipo == TipoActor.FUNCIONARIO ? FUNCIONARIO : USUARIO, id)
                .claim(NOMBRE, nombre)
                .claim(ROLES, roles)
                .claim(PERMISOS, cambioPassword ? List.of() : permisos)
                .claim(INICIO, inicioSesion.getEpochSecond())
                .claim(CAMBIO_PASSWORD, cambioPassword);
        if (municipioId != null) {
            claims.claim(MUNICIPIO, municipioId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new TokenEmitido(token, expira);
    }

    /** Inicio de la sesión registrado en el token, para renovarlo sin extenderla más allá del máximo. */
    public static Instant inicioSesion(Jwt jwt) {
        Object v = jwt.getClaim(INICIO);
        return v instanceof Number n ? Instant.ofEpochSecond(n.longValue()) : jwt.getIssuedAt();
    }

    public boolean renovable(Jwt jwt) {
        return Instant.now().isBefore(inicioSesion(jwt).plus(props.duracionRenovacion()));
    }

    public record TokenEmitido(String token, Instant expiraEn) {
    }
}
