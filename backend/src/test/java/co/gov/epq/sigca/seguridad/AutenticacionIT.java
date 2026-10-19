package co.gov.epq.sigca.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.cifrado.CifradorIdentificacion;
import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import co.gov.epq.sigca.soporte.PruebaIntegracion;

/**
 * Flujos completos de autenticación contra Oracle: enrolamiento y uso del 2FA, contraseña temporal, bloqueo por
 * intentos, renovación y cierre; y en el portal, activación, recuperación e ingreso del ciudadano.
 */
class AutenticacionIT extends PruebaIntegracion {

    private static final String CLAVE = "Inicial.2026x";
    private static final Pattern ENLACE = Pattern.compile("token=([A-Za-z0-9_-]{43})");

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    CifradorIdentificacion cifrador;

    @Autowired
    Parametros parametros;

    // ------------------------------------------------------------------ funcionarios

    @Test
    void primerIngresoEnrolaDobleFactorYCambiaLaContrasenaTemporal() throws Exception {
        String username = crearFuncionario("ASESOR", true);
        Long id = jdbc.queryForObject("SELECT ID FROM SIGCA.FUNCIONARIO WHERE USERNAME = ?", Long.class, username);

        login(username, CLAVE, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("DOBLE_FACTOR_ENROLAR"));

        String enrolamiento = postJson("/api/auth/doble-factor/enrolar",
                "{\"username\":\"" + username + "\",\"password\":\"" + CLAVE + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uriOtp").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
                .andReturn().getResponse().getContentAsString();
        String secreto = JsonPath.read(enrolamiento, "$.secreto");
        String guardado = jdbc.queryForObject("SELECT SECRETO_DOBLE_FACTOR FROM SIGCA.FUNCIONARIO WHERE ID = ?", String.class, id);
        assertThat(guardado).isNotEqualTo(secreto);
        assertThat(cifrador.descifrar(guardado)).isEqualTo(secreto);

        // Un código errado cuenta como intento fallido
        login(username, CLAVE, codigoErrado(secreto)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("DOBLE_FACTOR_INVALIDO"));
        assertThat(jdbc.queryForObject("SELECT INTENTOS_FALLIDOS FROM SIGCA.FUNCIONARIO WHERE ID = ?", Integer.class, id))
                .isEqualTo(1);

        String sesion = login(username, CLAVE, codigo(secreto)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("FUNCIONARIO"))
                .andExpect(jsonPath("$.expiraEn").value(org.hamcrest.Matchers.matchesPattern("^\\d{4}-\\d{2}-\\d{2}T.*Z$")))
                .andExpect(jsonPath("$.cambioPasswordRequerido").value(true))
                .andExpect(jsonPath("$.permisos.length()").value(0))
                .andExpect(jsonPath("$.roles[0]").value("ASESOR"))
                .andReturn().getResponse().getContentAsString();
        var fila = jdbc.queryForMap("SELECT DOBLE_FACTOR_ACTIVO, ULTIMO_ACCESO, INTENTOS_FALLIDOS FROM SIGCA.FUNCIONARIO WHERE ID = ?", id);
        assertThat(fila.get("DOBLE_FACTOR_ACTIVO")).isEqualTo("S");
        assertThat(fila.get("ULTIMO_ACCESO")).isNotNull();
        assertThat(((Number) fila.get("INTENTOS_FALLIDOS")).intValue()).isZero();
        assertThat(auditorias("ENROLAR_2FA", "FUNCIONARIO", id)).isEqualTo(1);
        assertThat(auditorias("INICIO_SESION", "FUNCIONARIO", id)).isEqualTo(1);

        // Con la contraseña temporal el token no tiene permisos
        String temporal = JsonPath.read(sesion, "$.token");
        mvc.perform(get("/api/admin/funcionarios").header("Authorization", "Bearer " + temporal))
                .andExpect(status().isForbidden());

        String nueva = "Nueva.Clave2026";
        postJson("/api/auth/cambiar-password", "{\"actual\":\"" + CLAVE + "\",\"nueva\":\"" + CLAVE
                + "\",\"confirmacion\":\"" + CLAVE + "\"}", temporal)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PASSWORD_IGUAL_ACTUAL"));
        postJson("/api/auth/cambiar-password", "{\"actual\":\"Otra.Clave2026\",\"nueva\":\"" + nueva
                + "\",\"confirmacion\":\"" + nueva + "\"}", temporal)
                .andExpect(jsonPath("$.codigo").value("PASSWORD_ACTUAL_INCORRECTA"));
        String conPermisos = postJson("/api/auth/cambiar-password", "{\"actual\":\"" + CLAVE + "\",\"nueva\":\"" + nueva
                + "\",\"confirmacion\":\"" + nueva + "\"}", temporal)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambioPasswordRequerido").value(false))
                .andExpect(jsonPath("$.permisos").value(org.hamcrest.Matchers.hasItem("USUARIO_CONSULTAR")))
                .andReturn().getResponse().getContentAsString();
        assertThat(jdbc.queryForObject("SELECT CAMBIO_PASSWORD_REQUERIDO FROM SIGCA.FUNCIONARIO WHERE ID = ?", String.class, id))
                .isEqualTo("N");

        // Con el 2FA activo, sin código se pide el código y no se cuenta como fallo
        login(username, nueva, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("DOBLE_FACTOR_REQUERIDO"));
        postJson("/api/auth/doble-factor/enrolar", "{\"username\":\"" + username + "\",\"password\":\"" + nueva + "\"}")
                .andExpect(status().isConflict());

        String token = JsonPath.read(conPermisos, "$.token");
        postJson("/api/auth/renovar", "", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.permisos").value(org.hamcrest.Matchers.hasItem("USUARIO_CONSULTAR")));
        postJson("/api/auth/salir", "", token).andExpect(status().isNoContent());
        assertThat(auditorias("CIERRE_SESION", "FUNCIONARIO", id)).isEqualTo(1);
        assertThat(auditorias("CAMBIAR_PASSWORD", "FUNCIONARIO", id)).isEqualTo(1);
    }

    @Test
    void intentosFallidosBloqueanSinRevelarSiElUsuarioExiste() throws Exception {
        String username = crearFuncionario("ASESOR", false);
        Long id = jdbc.queryForObject("SELECT ID FROM SIGCA.FUNCIONARIO WHERE USERNAME = ?", Long.class, username);
        int maximo = parametros.entero(Parametros.PORTAL_MAX_INTENTOS);

        login("no.existe." + unico(), "Cualquier.2026", null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        for (int i = 0; i < maximo; i++) {
            login(username, "Errada.2026x", null).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.FUNCIONARIO WHERE ID = ? AND BLOQUEADO_HASTA IS NOT NULL",
                Integer.class, id)).isEqualTo(1);
        // Bloqueada: con la contraseña correcta se informa el bloqueo; con una errada, la respuesta genérica
        login(username, CLAVE, null).andExpect(status().isLocked()).andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));
        login(username, "Errada.2026x", null).andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        assertThat(auditorias("BLOQUEO_CUENTA", "FUNCIONARIO", id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.NOTIFICACION WHERE EVENTO = 'CUENTA_BLOQUEADA' AND ENTIDAD = 'FUNCIONARIO'
                   AND ENTIDAD_ID = ?""", Integer.class, id)).isEqualTo(1);

        // El administrador lo desbloquea y puede volver a intentar
        mvc.perform(post("/api/admin/funcionarios/" + id + "/desbloqueo").with(como("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bloqueadoHasta").doesNotExist());
        login(username, CLAVE, null).andExpect(jsonPath("$.codigo").value("DOBLE_FACTOR_ENROLAR"));
    }

    @Test
    void funcionarioInactivoNoIngresa() throws Exception {
        String username = crearFuncionario("ASESOR", false);
        jdbc.update("UPDATE SIGCA.FUNCIONARIO SET ACTIVO = 'N', FECHA_DESACTIVACION = SYSTIMESTAMP WHERE USERNAME = ?", username);
        login(username, CLAVE, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CUENTA_INACTIVA"));
    }

    // ------------------------------------------------------------------ portal ciudadano

    @Test
    void ciudadanoActivaSuCuentaIngresaSeBloqueaYRecuperaLaContrasena() throws Exception {
        String numero = "7" + String.format("%09d", Long.parseLong(unico()) % 1_000_000_000L);
        Long usuarioId = crearUsuario(numero);
        String solicitud = "{\"tipoIdentificacion\":\"CC\",\"numeroIdentificacion\":\"" + numero + "\"}";

        // Identificación inexistente: misma respuesta
        postJson("/api/auth/activacion/solicitar", "{\"tipoIdentificacion\":\"CC\",\"numeroIdentificacion\":\"1000000001\"}")
                .andExpect(status().isAccepted());

        postJson("/api/auth/activacion/solicitar", solicitud).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT ESTADO FROM SIGCA.CUENTA_PORTAL WHERE USUARIO_ID = ?", String.class, usuarioId))
                .isEqualTo("PENDIENTE");
        String primero = ultimoEnlace(usuarioId);
        // Una segunda solicitud invalida el enlace anterior (UX_TOKEN_PENDIENTE)
        postJson("/api/auth/activacion/solicitar", solicitud).andExpect(status().isAccepted());
        String segundo = ultimoEnlace(usuarioId);
        assertThat(segundo).isNotEqualTo(primero);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.TOKEN_ACCESO WHERE USUARIO_ID = ? AND USADO_EN IS NULL",
                Integer.class, usuarioId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT TOKEN_HASH FROM SIGCA.TOKEN_ACCESO WHERE USUARIO_ID = ? AND USADO_EN IS NULL",
                String.class, usuarioId)).isEqualTo(Secretos.sha256(segundo));

        String clave = "Portal.Clave2026";
        confirmar("activacion", primero, clave).andExpect(status().isGone());
        confirmar("activacion", segundo, "Corta.1").andExpect(status().isBadRequest());
        confirmar("activacion", segundo, "Clave." + numero).andExpect(jsonPath("$.codigo").value("PASSWORD_ES_IDENTIFICACION"));
        confirmar("activacion", segundo, clave).andExpect(status().isNoContent());
        confirmar("activacion", segundo, clave).andExpect(status().isGone());
        assertThat(jdbc.queryForObject("SELECT ESTADO FROM SIGCA.CUENTA_PORTAL WHERE USUARIO_ID = ?", String.class, usuarioId))
                .isEqualTo("ACTIVA");
        assertThat(auditorias("ACTIVAR_CUENTA_PORTAL", "CUENTA_PORTAL", usuarioId)).isEqualTo(1);

        String sesion = loginCiudadano(numero, clave).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CIUDADANO"))
                .andExpect(jsonPath("$.roles[0]").value("CIUDADANO"))
                .andExpect(jsonPath("$.permisos").value(org.hamcrest.Matchers.hasItem("PORTAL_CONSULTAR")))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(sesion, "$.token");
        postJson("/api/auth/renovar", "", token).andExpect(status().isOk());

        int maximo = parametros.entero(Parametros.PORTAL_MAX_INTENTOS);
        for (int i = 0; i < maximo; i++) {
            loginCiudadano(numero, "Errada.2026x").andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        }
        loginCiudadano(numero, clave).andExpect(status().isLocked());
        assertThat(auditorias("BLOQUEO_CUENTA", "CUENTA_PORTAL", usuarioId)).isEqualTo(1);

        // La recuperación fija otra contraseña y levanta el bloqueo
        postJson("/api/auth/recuperacion/solicitar", solicitud).andExpect(status().isAccepted());
        String recuperacion = ultimoEnlace(usuarioId);
        confirmar("activacion", recuperacion, "Otra.Clave2026").andExpect(status().isGone());
        confirmar("recuperacion", recuperacion, "Otra.Clave2026").andExpect(status().isNoContent());
        assertThat(auditorias("RECUPERAR_PASSWORD", "CUENTA_PORTAL", usuarioId)).isEqualTo(1);
        loginCiudadano(numero, clave).andExpect(status().isUnauthorized());
        String otra = JsonPath.read(loginCiudadano(numero, "Otra.Clave2026").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");

        postJson("/api/auth/cambiar-password", "{\"actual\":\"Otra.Clave2026\",\"nueva\":\"Tercera.Clave26\","
                + "\"confirmacion\":\"Tercera.Clave26\"}", otra).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CIUDADANO"));
        loginCiudadano(numero, "Tercera.Clave26").andExpect(status().isOk());
    }

    @Test
    void recuperacionSinCuentaActivaNoCreaEnlace() throws Exception {
        String numero = "8" + String.format("%09d", Long.parseLong(unico()) % 1_000_000_000L);
        Long usuarioId = crearUsuario(numero);
        postJson("/api/auth/recuperacion/solicitar", "{\"tipoIdentificacion\":\"CC\",\"numeroIdentificacion\":\"" + numero + "\"}")
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.TOKEN_ACCESO WHERE USUARIO_ID = ?", Integer.class, usuarioId))
                .isZero();
        loginCiudadano(numero, "Cualquier.2026").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    // ------------------------------------------------------------------ apoyo

    private ResultActions login(String username, String password, String codigo) throws Exception {
        String c = codigo == null ? "" : ",\"codigoDobleFactor\":\"" + codigo + "\"";
        return postJson("/api/auth/login", "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"" + c + "}");
    }

    private ResultActions loginCiudadano(String numero, String password) throws Exception {
        return postJson("/api/auth/login-ciudadano",
                "{\"tipoIdentificacion\":\"CC\",\"numeroIdentificacion\":\"" + numero + "\",\"password\":\"" + password + "\"}");
    }

    private ResultActions confirmar(String tipo, String token, String password) throws Exception {
        return postJson("/api/auth/" + tipo + "/confirmar",
                "{\"token\":\"" + token + "\",\"password\":\"" + password + "\",\"confirmacion\":\"" + password + "\"}");
    }

    private ResultActions postJson(String url, String cuerpo) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions postJson(String url, String cuerpo, String token) throws Exception {
        var req = post(url).header("Authorization", "Bearer " + token);
        if (!cuerpo.isEmpty()) {
            req.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(req);
    }

    private String crearFuncionario(String rol, boolean temporal) {
        String u = "prueba.seg" + unico();
        jdbc.update("""
                INSERT INTO SIGCA.FUNCIONARIO (CODIGO_FUNCIONARIO, NOMBRE_COMPLETO, CORREO, USERNAME, PASSWORD_HASH,
                    CAMBIO_PASSWORD_REQUERIDO, MUNICIPIO_ID)
                VALUES (?, 'Funcionario de Prueba', ?, ?, ?, ?, (SELECT ID FROM SIGCA.MUNICIPIO WHERE CODIGO_DANE = '63190'))""",
                "SEG-" + unico(), u + "@epq.gov.co", u, encoder.encode(CLAVE), temporal ? "S" : "N");
        jdbc.update("""
                INSERT INTO SIGCA.FUNCIONARIO_ROL (FUNCIONARIO_ID, ROL_ID)
                SELECT F.ID, R.ID FROM SIGCA.FUNCIONARIO F, SIGCA.ROL R WHERE F.USERNAME = ? AND R.CODIGO = ?""", u, rol);
        return u;
    }

    private Long crearUsuario(String numero) {
        jdbc.update("""
                INSERT INTO SIGCA.USUARIO (TIPO_PERSONA, TIPO_IDENTIFICACION, NUMERO_IDENTIFICACION_CIFRADO,
                    NUMERO_IDENTIFICACION_HASH, NUMERO_IDENTIFICACION_MASCARA, NOMBRE_COMPLETO, CORREO, TELEFONO,
                    DIRECCION_NOTIFICACION, MUNICIPIO_ID, AUTORIZA_TRATAMIENTO_DATOS, FECHA_AUTORIZACION_DATOS)
                VALUES ('NATURAL', 'CC', ?, ?, ?, 'Ciudadana de Prueba', ?, '3101234567', 'Calle 5 # 10-20',
                    (SELECT ID FROM SIGCA.MUNICIPIO WHERE CODIGO_DANE = '63190'), 'S', SYSTIMESTAMP)""",
                cifrador.cifrar(numero), cifrador.hash(TipoIdentificacion.CC, numero), CifradorIdentificacion.mascara(numero),
                "ciudadana" + numero + "@correo.co");
        return jdbc.queryForObject("SELECT ID FROM SIGCA.USUARIO WHERE NUMERO_IDENTIFICACION_HASH = ?", Long.class,
                cifrador.hash(TipoIdentificacion.CC, numero));
    }

    /** Toma el token del último correo encolado para el usuario (el backend solo guarda su hash). */
    private String ultimoEnlace(Long usuarioId) {
        String cuerpo = jdbc.queryForObject("""
                SELECT CUERPO FROM SIGCA.NOTIFICACION WHERE USUARIO_ID = ? AND ENTIDAD = 'CUENTA_PORTAL' AND EVENTO = 'GENERAL'
                 ORDER BY ID DESC FETCH FIRST 1 ROW ONLY""", String.class, usuarioId);
        Matcher m = ENLACE.matcher(cuerpo);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private int auditorias(String operacion, String entidad, Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = ? AND ENTIDAD = ? AND ENTIDAD_ID = ? AND RESULTADO = 'EXITO'",
                Integer.class, operacion, entidad, id);
    }

    private static String codigo(String secreto) {
        return Totp.codigo(Totp.decodificarBase32(secreto), Instant.now().getEpochSecond() / 30);
    }

    /** Un código que no corresponde a ningún paso de la ventana aceptada. */
    private static String codigoErrado(String secreto) {
        long paso = Instant.now().getEpochSecond() / 30;
        byte[] clave = Totp.decodificarBase32(secreto);
        for (int n = 0; ; n++) {
            String c = String.format("%06d", n);
            boolean usado = false;
            for (int i = -2; i <= 2; i++) {
                usado |= c.equals(Totp.codigo(clave, paso + i));
            }
            if (!usado) {
                return c;
            }
        }
    }
}
