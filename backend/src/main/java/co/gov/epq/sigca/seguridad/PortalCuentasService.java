package co.gov.epq.sigca.seguridad;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.Auditoria.Actor;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.auditoria.ResultadoAuditoria;
import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.cifrado.CifradorIdentificacion;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.validation.Normalizador;
import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;
import co.gov.epq.sigca.seguridad.UsuarioActual.TipoActor;
import co.gov.epq.sigca.seguridad.cuentas.CuentaPortal;
import co.gov.epq.sigca.seguridad.cuentas.CuentaPortalRepository;
import co.gov.epq.sigca.seguridad.cuentas.CuentasUsuarioRepository;
import co.gov.epq.sigca.seguridad.cuentas.EstadoCuentaPortal;
import co.gov.epq.sigca.seguridad.cuentas.RolRepository;
import co.gov.epq.sigca.seguridad.cuentas.TipoToken;
import co.gov.epq.sigca.seguridad.cuentas.TokenAcceso;
import co.gov.epq.sigca.seguridad.cuentas.TokenAccesoRepository;
import co.gov.epq.sigca.usuarios.Usuario;

/**
 * Cuenta del portal ciudadano (P-13, RF-25, RF-26): ingreso con identificación y contraseña, activación y
 * recuperación por un enlace de un solo uso enviado al correo registrado. Las solicitudes de enlace responden
 * siempre igual para no revelar qué identificaciones están registradas.
 */
@Service
public class PortalCuentasService {

    private static final Logger log = LoggerFactory.getLogger(PortalCuentasService.class);
    static final String ENTIDAD = "CUENTA_PORTAL";
    static final String ROL_CIUDADANO = "CIUDADANO";

    private final CuentasUsuarioRepository usuarios;
    private final CuentaPortalRepository cuentas;
    private final TokenAccesoRepository tokens;
    private final RolRepository roles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Parametros parametros;
    private final Auditoria auditoria;
    private final Notificaciones notificaciones;
    private final CifradorIdentificacion cifrador;
    private final PoliticaPassword politica;
    private final String urlPortal;
    private final Clock reloj = Clock.systemDefaultZone();
    private final String hashFicticio;

    public PortalCuentasService(CuentasUsuarioRepository usuarios, CuentaPortalRepository cuentas,
            TokenAccesoRepository tokens, RolRepository roles, PasswordEncoder encoder, JwtService jwt, Parametros parametros,
            Auditoria auditoria, Notificaciones notificaciones, CifradorIdentificacion cifrador, PoliticaPassword politica,
            @Value("${sigca.portal.url-base}") String urlPortal) {
        this.usuarios = usuarios;
        this.cuentas = cuentas;
        this.tokens = tokens;
        this.roles = roles;
        this.encoder = encoder;
        this.jwt = jwt;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.cifrador = cifrador;
        this.politica = politica;
        this.urlPortal = urlPortal.endsWith("/") ? urlPortal.substring(0, urlPortal.length() - 1) : urlPortal;
        this.hashFicticio = encoder.encode(Secretos.nuevoToken());
    }

    @Transactional(noRollbackFor = NegocioException.class)
    public Sesion login(TipoIdentificacion tipo, String numero, String password) {
        Usuario u = buscar(tipo, numero);
        CuentaPortal c = u == null ? null : cuentas.findById(u.getId()).orElse(null);
        if (c == null || c.getPasswordHash() == null || !Boolean.TRUE.equals(u.getActivo())) {
            encoder.matches(password, hashFicticio);
            auditoria.como(Actor.ANONIMO, OperacionAuditoria.INICIO_SESION_FALLIDO, ResultadoAuditoria.FALLO, ENTIDAD,
                    u == null ? null : u.getId(), Map.of("motivo", u == null ? "CREDENCIALES" : "SIN_CUENTA_ACTIVA"));
            throw AutenticacionService.credencialesInvalidas();
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        boolean passwordOk = encoder.matches(password, c.getPasswordHash());
        if (ControlIntentos.bloqueada(c.getBloqueadaHasta(), ahora)) {
            auditarFallo(u.getId(), "CUENTA_BLOQUEADA");
            throw passwordOk ? AutenticacionService.cuentaBloqueada() : AutenticacionService.credencialesInvalidas();
        }
        if (!passwordOk) {
            registrarFallo(u, c, ahora);
            throw AutenticacionService.credencialesInvalidas();
        }
        if (c.getEstado() != EstadoCuentaPortal.ACTIVA) {
            auditarFallo(u.getId(), "CUENTA_" + c.getEstado());
            throw new NegocioException("CUENTA_INACTIVA", "La cuenta del portal está inactiva", HttpStatus.FORBIDDEN);
        }
        c.setIntentosFallidos(0);
        c.setBloqueadaHasta(null);
        c.setUltimoAcceso(ahora);
        cuentas.saveAndFlush(c);
        auditoria.como(Actor.ciudadano(u.getId()), OperacionAuditoria.INICIO_SESION, ResultadoAuditoria.EXITO, ENTIDAD,
                u.getId(), null);
        return emitir(u, Instant.now(reloj));
    }

    /**
     * Crea un enlace de activación o recuperación si procede. No lanza errores de negocio hacia el cliente: la
     * respuesta es la misma exista o no la identificación.
     */
    @Transactional
    public void solicitarEnlace(TipoToken tipoToken, TipoIdentificacion tipo, String numero) {
        Usuario u = buscar(tipo, numero);
        if (u == null || !Boolean.TRUE.equals(u.getActivo())) {
            log.debug("Solicitud de enlace {} sin usuario activo", tipoToken);
            return;
        }
        CuentaPortal c = cuentas.findById(u.getId()).orElse(null);
        if (tipoToken == TipoToken.ACTIVACION) {
            if (c == null) {
                c = new CuentaPortal();
                c.setUsuarioId(u.getId());
                c.setEstado(EstadoCuentaPortal.PENDIENTE);
                c.setIntentosFallidos(0);
                cuentas.saveAndFlush(c);
            } else if (c.getEstado() != EstadoCuentaPortal.PENDIENTE) {
                // Ya activa: la recuperación es el camino; inactiva: la reactiva un asesor
                log.debug("Activación solicitada para una cuenta en estado {}", c.getEstado());
                return;
            }
        } else if (c == null || c.getEstado() != EstadoCuentaPortal.ACTIVA) {
            log.debug("Recuperación solicitada sin cuenta activa");
            return;
        }
        String token = Secretos.nuevoToken();
        // UX_TOKEN_PENDIENTE: un solo enlace sin usar por cuenta y tipo; el nuevo reemplaza al anterior
        tokens.invalidarPendientes(u.getId(), tipoToken.name());
        tokens.crear(u.getId(), tipoToken.name(), Secretos.sha256(token), parametros.entero(Parametros.TOKEN_HORAS_VIGENCIA));
        String ruta = tipoToken == TipoToken.ACTIVACION ? "activacion" : "recuperacion";
        String enlace = urlPortal + "/cuenta/" + ruta + "?token=" + token;
        int horas = parametros.entero(Parametros.TOKEN_HORAS_VIGENCIA);
        String accion = tipoToken == TipoToken.ACTIVACION ? "activar su cuenta del portal" : "definir una nueva contraseña";
        notificaciones.correo(EventoNotificacion.GENERAL, u.getCorreo(), u.getId(),
                tipoToken == TipoToken.ACTIVACION ? "EPQ: active su cuenta del portal" : "EPQ: recuperación de contraseña",
                "Hola " + u.getNombreCompleto() + ". Para " + accion + " abra este enlace (válido " + horas + " horas, un solo uso): "
                        + enlace + " . Si usted no lo solicitó, ignore este mensaje.",
                ENTIDAD, u.getId());
    }

    @Transactional
    public void confirmarEnlace(TipoToken tipoToken, String token, String password, String confirmacion) {
        politica.validar(password, confirmacion);
        TokenAcceso t = token == null ? null : tokens.findByTokenHash(Secretos.sha256(token)).orElse(null);
        if (t == null || t.getTipo() != tipoToken || t.getUsadoEn() != null || tokens.consumir(t.getId()) == 0) {
            throw new NegocioException("ENLACE_INVALIDO", "El enlace no es válido, ya se usó o venció. Solicite uno nuevo",
                    HttpStatus.GONE);
        }
        Usuario u = usuarios.findById(t.getUsuarioId()).orElseThrow(PortalCuentasService::sesionInvalida);
        politica.distintaDeIdentificacion(password, cifrador.descifrar(u.getNumeroIdentificacionCifrado()));
        CuentaPortal c = cuentas.findById(u.getId()).orElseThrow(PortalCuentasService::sesionInvalida);
        if (tipoToken == TipoToken.RECUPERACION && c.getEstado() != EstadoCuentaPortal.ACTIVA) {
            throw new NegocioException("CUENTA_INACTIVA", "La cuenta del portal está inactiva", HttpStatus.FORBIDDEN);
        }
        c.setPasswordHash(encoder.encode(password));
        c.setCambioPasswordEn(LocalDateTime.now(reloj));
        c.setEstado(EstadoCuentaPortal.ACTIVA);
        c.setIntentosFallidos(0);
        c.setBloqueadaHasta(null);
        cuentas.saveAndFlush(c);
        auditoria.como(Actor.ciudadano(u.getId()), tipoToken == TipoToken.ACTIVACION
                ? OperacionAuditoria.ACTIVAR_CUENTA_PORTAL
                : OperacionAuditoria.RECUPERAR_PASSWORD, ResultadoAuditoria.EXITO, ENTIDAD, u.getId(), null);
    }

    Sesion cambiarPassword(Long usuarioId, String actual, String nueva, Instant inicio) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(PortalCuentasService::sesionInvalida);
        CuentaPortal c = cuentas.findById(usuarioId).orElseThrow(PortalCuentasService::sesionInvalida);
        if (c.getPasswordHash() == null || !encoder.matches(actual, c.getPasswordHash())) {
            auditoria.fallo(OperacionAuditoria.CAMBIAR_PASSWORD, ENTIDAD, usuarioId, Map.of("motivo", "PASSWORD_ACTUAL"));
            throw AutenticacionService.passwordActualIncorrecta();
        }
        politica.distintaDeIdentificacion(nueva, cifrador.descifrar(u.getNumeroIdentificacionCifrado()));
        c.setPasswordHash(encoder.encode(nueva));
        c.setCambioPasswordEn(LocalDateTime.now(reloj));
        cuentas.saveAndFlush(c);
        auditoria.exito(OperacionAuditoria.CAMBIAR_PASSWORD, ENTIDAD, usuarioId, null);
        return emitir(u, inicio);
    }

    Sesion renovar(Long usuarioId, Instant inicio) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(PortalCuentasService::sesionInvalida);
        CuentaPortal c = cuentas.findById(usuarioId).orElseThrow(PortalCuentasService::sesionInvalida);
        if (!Boolean.TRUE.equals(u.getActivo()) || c.getEstado() != EstadoCuentaPortal.ACTIVA
                || ControlIntentos.bloqueada(c.getBloqueadaHasta(), LocalDateTime.now(reloj))) {
            throw sesionInvalida();
        }
        return emitir(u, inicio);
    }

    private Sesion emitir(Usuario u, Instant inicio) {
        List<String> permisos = roles.findByCodigo(ROL_CIUDADANO)
                .filter(r -> Boolean.TRUE.equals(r.getActivo()))
                .map(r -> roles.permisosDe(r.getId()))
                .orElse(List.of());
        List<String> rolesSesion = List.of(ROL_CIUDADANO);
        JwtService.TokenEmitido t = jwt.emitir(TipoActor.CIUDADANO, u.getId(), u.getCodigo(), u.getNombreCompleto(),
                rolesSesion, permisos, null, false, inicio);
        return new Sesion(t.token(), t.expiraEn(), TipoActor.CIUDADANO, u.getNombreCompleto(), rolesSesion, permisos, null,
                false);
    }

    private Usuario buscar(TipoIdentificacion tipo, String numero) {
        String n = Normalizador.documento(numero);
        if (tipo == null || n == null || n.isEmpty()) {
            return null;
        }
        return usuarios.findByTipoIdentificacionAndNumeroIdentificacionHash(tipo, cifrador.hash(tipo, n)).orElse(null);
    }

    private void registrarFallo(Usuario u, CuentaPortal c, LocalDateTime ahora) {
        int minutos = parametros.entero(Parametros.PORTAL_MINUTOS_BLOQUEO);
        ControlIntentos.Resultado r = ControlIntentos.fallo(c.getIntentosFallidos(),
                parametros.entero(Parametros.PORTAL_MAX_INTENTOS), minutos, ahora);
        c.setIntentosFallidos(r.intentos());
        c.setBloqueadaHasta(r.bloqueadaHasta());
        cuentas.saveAndFlush(c);
        auditarFallo(u.getId(), "CREDENCIALES");
        if (r.bloqueoNuevo()) {
            auditoria.como(Actor.ANONIMO, OperacionAuditoria.BLOQUEO_CUENTA, ResultadoAuditoria.EXITO, ENTIDAD, u.getId(),
                    Map.of("minutos", minutos));
            notificaciones.correo(EventoNotificacion.CUENTA_BLOQUEADA, u.getCorreo(), u.getId(),
                    "EPQ: cuenta del portal bloqueada temporalmente",
                    "Hola " + u.getNombreCompleto() + ". Su cuenta del portal de EPQ quedó bloqueada " + minutos
                            + " minutos por intentos fallidos de ingreso. Si no fue usted, use la opción de recuperar contraseña.",
                    ENTIDAD, u.getId());
        }
    }

    private void auditarFallo(Long usuarioId, String motivo) {
        auditoria.como(Actor.ANONIMO, OperacionAuditoria.INICIO_SESION_FALLIDO, ResultadoAuditoria.FALLO, ENTIDAD, usuarioId,
                Map.of("motivo", motivo));
    }

    static NegocioException sesionInvalida() {
        return new NegocioException("SESION_INVALIDA", "La sesión ya no es válida. Ingrese de nuevo", HttpStatus.UNAUTHORIZED);
    }
}
