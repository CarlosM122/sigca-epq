package co.gov.epq.sigca.soporte;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import co.gov.epq.sigca.seguridad.JwtService;

/**
 * Base de las pruebas de integración contra el Oracle local (perfil dev, esquema oficial + dev/semilla-dev.sql).
 * Las pruebas confirman sus transacciones como en producción (las restricciones diferidas solo se evalúan al
 * confirmar), así que cada prueba crea sus propios datos con valores únicos.
 */
@SpringBootTest(properties = { "sigca.seguridad.limite-por-minuto=100000", "sigca.facturacion.lote.automatico=false" })
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public abstract class PruebaIntegracion {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /** JWT de un funcionario de la semilla (asesor, gestor.circasia, gestor.salento, jefe.pqr, cartera, gerente, admin). */
    protected RequestPostProcessor como(String username) {
        var f = jdbc.queryForMap("SELECT ID, NOMBRE_COMPLETO, MUNICIPIO_ID FROM SIGCA.FUNCIONARIO WHERE USERNAME = ?", username);
        Long id = ((Number) f.get("ID")).longValue();
        List<String> roles = jdbc.queryForList("""
                SELECT R.CODIGO FROM SIGCA.FUNCIONARIO_ROL FR JOIN SIGCA.ROL R ON R.ID = FR.ROL_ID
                WHERE FR.FUNCIONARIO_ID = ?""", String.class, id);
        List<String> permisos = jdbc.queryForList("""
                SELECT DISTINCT P.CODIGO FROM SIGCA.FUNCIONARIO_ROL FR
                JOIN SIGCA.ROL_PERMISO RP ON RP.ROL_ID = FR.ROL_ID JOIN SIGCA.PERMISO P ON P.ID = RP.PERMISO_ID
                WHERE FR.FUNCIONARIO_ID = ?""", String.class, id);
        Object mun = f.get("MUNICIPIO_ID");
        return jwt().jwt(j -> {
            j.subject(username)
                    .claim(JwtService.TIPO, "FUNCIONARIO")
                    .claim(JwtService.FUNCIONARIO, id)
                    .claim(JwtService.NOMBRE, f.get("NOMBRE_COMPLETO"))
                    .claim(JwtService.ROLES, roles)
                    .claim(JwtService.PERMISOS, permisos);
            if (mun != null) {
                j.claim(JwtService.MUNICIPIO, ((Number) mun).longValue());
            }
        }).authorities(permisos.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList());
    }

    /** JWT de un ciudadano (titular) con los permisos del rol CIUDADANO. */
    protected RequestPostProcessor ciudadano(Long usuarioId) {
        String codigo = jdbc.queryForObject("SELECT CODIGO FROM SIGCA.USUARIO WHERE ID = ?", String.class, usuarioId);
        List<String> permisos = jdbc.queryForList("""
                SELECT P.CODIGO FROM SIGCA.ROL R JOIN SIGCA.ROL_PERMISO RP ON RP.ROL_ID = R.ID
                JOIN SIGCA.PERMISO P ON P.ID = RP.PERMISO_ID WHERE R.CODIGO = 'CIUDADANO'""", String.class);
        return jwt().jwt(j -> j.subject(codigo)
                .claim(JwtService.TIPO, "CIUDADANO")
                .claim(JwtService.USUARIO, usuarioId)
                .claim(JwtService.NOMBRE, "Ciudadano de prueba")
                .claim(JwtService.ROLES, List.of("CIUDADANO"))
                .claim(JwtService.PERMISOS, permisos))
                .authorities(permisos.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList());
    }

    /** Sufijo único para datos de prueba (seriales, documentos). */
    protected static String unico() {
        return Long.toString(System.nanoTime() % 1_000_000_000L);
    }
}
