package co.gov.epq.sigca.seguridad;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.Auditoria.Actor;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.auditoria.ResultadoAuditoria;
import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.cifrado.CifradorIdentificacion;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.validation.Normalizador;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;
import co.gov.epq.sigca.seguridad.UsuarioActual.TipoActor;
import co.gov.epq.sigca.seguridad.cuentas.Funcionario;
import co.gov.epq.sigca.seguridad.cuentas.FuncionarioRepository;

/**
 * Inicio de sesión de funcionarios con contraseña y TOTP obligatorio (P-20), enrolamiento del segundo factor,
 * cambio de contraseña, renovación del token y cierre de sesión. Los intentos fallidos se confirman aunque la
 * petición termine en error ({@code noRollbackFor}), porque de eso depende el bloqueo (RN-25).
 */
@Service
public class AutenticacionService {

    static final String ENTIDAD = "FUNCIONARIO";

    private final FuncionarioRepository funcionarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Parametros parametros;
    private final Auditoria auditoria;
    private final Notificaciones notificaciones;
    private final CifradorIdentificacion cifrador;
    private final SeguridadProperties props;
    private final PoliticaPassword politica;
    private final PortalCuentasService portal;
    private final Clock reloj = Clock.systemDefaultZone();
    /** Hash de referencia para gastar el mismo tiempo cuando el usuario no existe (no enumerar cuentas). */
    private final String hashFicticio;

    public AutenticacionService(FuncionarioRepository funcionarios, PasswordEncoder encoder, JwtService jwt,
            Parametros parametros, Auditoria auditoria, Notificaciones notificaciones, CifradorIdentificacion cifrador,
            SeguridadProperties props, PoliticaPassword politica, PortalCuentasService portal) {
        this.funcionarios = funcionarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.cifrador = cifrador;
        this.props = props;
        this.politica = politica;
        this.portal = portal;
        this.hashFicticio = encoder.encode(Secretos.nuevoToken());
    }

    @Transactional(noRollbackFor = NegocioException.class)
    public Sesion login(String username, String password, String codigo) {
        Funcionario f = verificarCredenciales(username, password);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        String secreto = f.getSecretoDobleFactor() == null ? null : cifrador.descifrar(f.getSecretoDobleFactor());
        boolean sinCodigo = codigo == null || codigo.isBlank();
        boolean enrolando = !Boolean.TRUE.equals(f.getDobleFactorActivo());
        if (enrolando && (secreto == null || sinCodigo)) {
            throw new NegocioException("DOBLE_FACTOR_ENROLAR",
                    "Debe registrar una aplicación autenticadora para completar el ingreso", HttpStatus.UNAUTHORIZED);
        }
        if (sinCodigo) {
            throw new NegocioException("DOBLE_FACTOR_REQUERIDO", "Ingrese el código de su aplicación autenticadora",
                    HttpStatus.UNAUTHORIZED);
        }
        if (!Totp.verificar(secreto, codigo.trim(), reloj)) {
            registrarFallo(f, ahora, "CODIGO_DOBLE_FACTOR");
            throw new NegocioException("DOBLE_FACTOR_INVALIDO", "El código de verificación no es correcto",
                    HttpStatus.UNAUTHORIZED);
        }
        f.setDobleFactorActivo(true);
        f.setIntentosFallidos(0);
        f.setBloqueadoHasta(null);
        // CK_FUNC_2FA_OBLIGATORIO: el acceso se registra en la misma fila que activa el segundo factor
        f.setUltimoAcceso(ahora);
        funcionarios.saveAndFlush(f);
        Actor actor = Actor.funcionario(f.getId());
        if (enrolando) {
            auditoria.como(actor, OperacionAuditoria.ENROLAR_2FA, ResultadoAuditoria.EXITO, ENTIDAD, f.getId(), null);
        }
        auditoria.como(actor, OperacionAuditoria.INICIO_SESION, ResultadoAuditoria.EXITO, ENTIDAD, f.getId(), null);
        return emitir(f, Instant.now(reloj));
    }

    /**
     * Genera (o regenera, mientras no esté activo) el secreto TOTP. Se guarda cifrado; el código de la app se
     * confirma en el siguiente inicio de sesión, que es el que activa el segundo factor.
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public Enrolamiento enrolar(String username, String password) {
        Funcionario f = verificarCredenciales(username, password);
        if (Boolean.TRUE.equals(f.getDobleFactorActivo())) {
            throw new NegocioException("DOBLE_FACTOR_ACTIVO",
                    "El segundo factor ya está registrado. Si perdió el acceso, pida al administrador reiniciarlo",
                    HttpStatus.CONFLICT);
        }
        String secreto = Totp.nuevoSecreto();
        f.setSecretoDobleFactor(cifrador.cifrar(secreto));
        funcionarios.saveAndFlush(f);
        return new Enrolamiento(secreto, Totp.uri(props.emisorDobleFactor(), f.getUsername(), secreto));
    }

    @Transactional
    public Sesion cambiarPassword(String actual, String nueva, String confirmacion) {
        UsuarioActual u = UsuarioActual.requerido();
        politica.validar(nueva, confirmacion);
        if (actual.equals(nueva)) {
            throw new NegocioException("PASSWORD_IGUAL_ACTUAL", "La nueva contraseña debe ser distinta de la actual");
        }
        Instant inicio = inicioSesionActual();
        if (u.esCiudadano()) {
            return portal.cambiarPassword(u.usuarioId(), actual, nueva, inicio);
        }
        Funcionario f = funcionarios.findById(u.funcionarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Funcionario", u.funcionarioId()));
        if (!encoder.matches(actual, f.getPasswordHash())) {
            auditoria.fallo(OperacionAuditoria.CAMBIAR_PASSWORD, ENTIDAD, f.getId(), Map.of("motivo", "PASSWORD_ACTUAL"));
            throw passwordActualIncorrecta();
        }
        f.setPasswordHash(encoder.encode(nueva));
        f.setCambioPasswordRequerido(false);
        funcionarios.saveAndFlush(f);
        auditoria.exito(OperacionAuditoria.CAMBIAR_PASSWORD, ENTIDAD, f.getId(), null);
        return emitir(f, inicio);
    }

    /** Token nuevo con permisos y estado releídos, sin pasar del máximo de sesión. */
    @Transactional(readOnly = true)
    public Sesion renovar() {
        UsuarioActual u = UsuarioActual.requerido();
        Jwt actual = jwtActual();
        if (actual == null || !jwt.renovable(actual)) {
            throw new NegocioException("SESION_EXPIRADA", "La sesión alcanzó su duración máxima. Ingrese de nuevo",
                    HttpStatus.UNAUTHORIZED);
        }
        Instant inicio = inicioSesionActual();
        if (u.esCiudadano()) {
            return portal.renovar(u.usuarioId(), inicio);
        }
        Funcionario f = funcionarios.findById(u.funcionarioId()).orElseThrow(PortalCuentasService::sesionInvalida);
        if (!Boolean.TRUE.equals(f.getActivo()) || ControlIntentos.bloqueada(f.getBloqueadoHasta(), LocalDateTime.now(reloj))) {
            throw PortalCuentasService.sesionInvalida();
        }
        return emitir(f, inicio);
    }

    @Transactional
    public void salir() {
        UsuarioActual u = UsuarioActual.requerido();
        auditoria.exito(OperacionAuditoria.CIERRE_SESION, u.esCiudadano() ? PortalCuentasService.ENTIDAD : ENTIDAD,
                u.esCiudadano() ? u.usuarioId() : u.funcionarioId(), null);
    }

    Sesion emitir(Funcionario f, Instant inicio) {
        List<String> roles = funcionarios.rolesActivos(f.getId());
        List<String> permisos = funcionarios.permisos(f.getId());
        boolean cambio = Boolean.TRUE.equals(f.getCambioPasswordRequerido());
        JwtService.TokenEmitido t = jwt.emitir(TipoActor.FUNCIONARIO, f.getId(), f.getUsername(), f.getNombreCompleto(),
                roles, permisos, f.getMunicipioId(), cambio, inicio);
        return new Sesion(t.token(), t.expiraEn(), TipoActor.FUNCIONARIO, f.getNombreCompleto(), roles,
                cambio ? List.of() : permisos, f.getMunicipioId(), cambio);
    }

    /**
     * Contraseña, bloqueo y estado. Un usuario inexistente y una contraseña errada responden igual; el bloqueo y la
     * inactividad solo se revelan a quien ya conoce la contraseña.
     */
    private Funcionario verificarCredenciales(String username, String password) {
        String u = Normalizador.correo(username);
        Funcionario f = funcionarios.findByUsername(u).orElse(null);
        if (f == null) {
            encoder.matches(password, hashFicticio);
            auditoria.como(Actor.ANONIMO, OperacionAuditoria.INICIO_SESION_FALLIDO, ResultadoAuditoria.FALLO, ENTIDAD, null,
                    detalle(u, "CREDENCIALES"));
            throw credencialesInvalidas();
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        boolean passwordOk = encoder.matches(password, f.getPasswordHash());
        if (ControlIntentos.bloqueada(f.getBloqueadoHasta(), ahora)) {
            auditarFallo(f, "CUENTA_BLOQUEADA");
            if (passwordOk) {
                throw cuentaBloqueada();
            }
            throw credencialesInvalidas();
        }
        if (!passwordOk) {
            registrarFallo(f, ahora, "CREDENCIALES");
            throw credencialesInvalidas();
        }
        if (!Boolean.TRUE.equals(f.getActivo())) {
            auditarFallo(f, "INACTIVO");
            throw new NegocioException("CUENTA_INACTIVA", "La cuenta está inactiva", HttpStatus.FORBIDDEN);
        }
        return f;
    }

    private void registrarFallo(Funcionario f, LocalDateTime ahora, String motivo) {
        int minutos = parametros.entero(Parametros.PORTAL_MINUTOS_BLOQUEO);
        ControlIntentos.Resultado r = ControlIntentos.fallo(f.getIntentosFallidos(),
                parametros.entero(Parametros.PORTAL_MAX_INTENTOS), minutos, ahora);
        f.setIntentosFallidos(r.intentos());
        f.setBloqueadoHasta(r.bloqueadaHasta());
        funcionarios.saveAndFlush(f);
        auditarFallo(f, motivo);
        if (r.bloqueoNuevo()) {
            auditoria.como(Actor.ANONIMO, OperacionAuditoria.BLOQUEO_CUENTA, ResultadoAuditoria.EXITO, ENTIDAD, f.getId(),
                    Map.of("minutos", minutos));
            notificaciones.correo(EventoNotificacion.CUENTA_BLOQUEADA, f.getCorreo(), null,
                    "SIGCA: cuenta bloqueada temporalmente",
                    "Hola " + f.getNombreCompleto() + ". Su cuenta de SIGCA quedó bloqueada " + minutos
                            + " minutos por intentos fallidos de ingreso. Si no fue usted, informe al administrador.",
                    ENTIDAD, f.getId());
        }
    }

    private void auditarFallo(Funcionario f, String motivo) {
        auditoria.como(Actor.ANONIMO, OperacionAuditoria.INICIO_SESION_FALLIDO, ResultadoAuditoria.FALLO, ENTIDAD, f.getId(),
                detalle(f.getUsername(), motivo));
    }

    private static Map<String, Object> detalle(String username, String motivo) {
        Map<String, Object> d = new LinkedHashMap<>();
        if (username != null) {
            d.put("username", username.length() > 60 ? username.substring(0, 60) : username);
        }
        d.put("motivo", motivo);
        return d;
    }

    static Jwt jwtActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt j ? j : null;
    }

    /** Inicio de la sesión vigente; las renovaciones y el cambio de contraseña no lo extienden. */
    static Instant inicioSesionActual() {
        Jwt j = jwtActual();
        Instant inicio = j == null ? null : JwtService.inicioSesion(j);
        return inicio == null ? Instant.now() : inicio;
    }

    static NegocioException credencialesInvalidas() {
        return new NegocioException("CREDENCIALES_INVALIDAS", "Usuario o contraseña incorrectos", HttpStatus.UNAUTHORIZED);
    }

    static NegocioException cuentaBloqueada() {
        return new NegocioException("CUENTA_BLOQUEADA",
                "La cuenta está bloqueada temporalmente por intentos fallidos. Intente de nuevo más tarde", HttpStatus.LOCKED);
    }

    static NegocioException passwordActualIncorrecta() {
        return new NegocioException("PASSWORD_ACTUAL_INCORRECTA", "La contraseña actual no es correcta");
    }

    /** Secreto TOTP en Base32 y URI otpauth:// para mostrar como código QR; se entregan una sola vez. */
    public record Enrolamiento(String secreto, String uriOtp) {
    }
}
