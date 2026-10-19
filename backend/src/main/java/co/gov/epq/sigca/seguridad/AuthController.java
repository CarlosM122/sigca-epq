package co.gov.epq.sigca.seguridad;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.validation.ConDocumento;
import co.gov.epq.sigca.common.validation.DocumentoValido;
import co.gov.epq.sigca.common.validation.PasswordSegura;
import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import co.gov.epq.sigca.seguridad.cuentas.TipoToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Autenticación (RF-25, RF-26, P-13, P-20). Login, enrolamiento 2FA, activación y recuperación son públicos
 * (ver {@link SecurityConfig}); cambiar contraseña, renovar y salir exigen un token vigente.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AutenticacionService autenticacion;
    private final PortalCuentasService portal;

    public AuthController(AutenticacionService autenticacion, PortalCuentasService portal) {
        this.autenticacion = autenticacion;
        this.portal = portal;
    }

    @PostMapping("/login")
    public Sesion login(@Valid @RequestBody LoginFuncionario req) {
        return autenticacion.login(req.username(), req.password(), req.codigoDobleFactor());
    }

    @PostMapping("/doble-factor/enrolar")
    public AutenticacionService.Enrolamiento enrolar(@Valid @RequestBody Credenciales req) {
        return autenticacion.enrolar(req.username(), req.password());
    }

    @PostMapping("/login-ciudadano")
    public Sesion loginCiudadano(@Valid @RequestBody LoginCiudadano req) {
        return portal.login(req.tipoIdentificacion(), req.numeroIdentificacion(), req.password());
    }

    /** Siempre 202, exista o no la identificación (no se enumeran usuarios). */
    @PostMapping("/{tipo}/solicitar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void solicitar(@PathVariable String tipo, @Valid @RequestBody SolicitudEnlace req) {
        portal.solicitarEnlace(tipoToken(tipo), req.tipoIdentificacion(), req.numeroIdentificacion());
    }

    @PostMapping("/{tipo}/confirmar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmar(@PathVariable String tipo, @Valid @RequestBody ConfirmacionEnlace req) {
        portal.confirmarEnlace(tipoToken(tipo), req.token(), req.password(), req.confirmacion());
    }

    @PostMapping("/cambiar-password")
    public Sesion cambiarPassword(@Valid @RequestBody CambioPassword req) {
        return autenticacion.cambiarPassword(req.actual(), req.nueva(), req.confirmacion());
    }

    @PostMapping("/renovar")
    public Sesion renovar() {
        return autenticacion.renovar();
    }

    @PostMapping("/salir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void salir() {
        autenticacion.salir();
    }

    private static TipoToken tipoToken(String ruta) {
        return switch (ruta) {
            case "activacion" -> TipoToken.ACTIVACION;
            case "recuperacion" -> TipoToken.RECUPERACION;
            default -> throw new RecursoNoEncontradoException("Ruta", ruta);
        };
    }

    public record LoginFuncionario(
            @NotBlank @Size(max = 60) String username,
            @NotBlank @Size(max = 64) String password,
            @Pattern(regexp = "^\\d{6}$", message = "El código de verificación tiene 6 dígitos") String codigoDobleFactor) {
    }

    public record Credenciales(@NotBlank @Size(max = 60) String username, @NotBlank @Size(max = 64) String password) {
    }

    @DocumentoValido
    public record LoginCiudadano(
            @NotNull TipoIdentificacion tipoIdentificacion,
            @NotBlank @Size(max = 15) String numeroIdentificacion,
            @NotBlank @Size(max = 64) String password) implements ConDocumento {
    }

    @DocumentoValido
    public record SolicitudEnlace(
            @NotNull TipoIdentificacion tipoIdentificacion,
            @NotBlank @Size(max = 15) String numeroIdentificacion) implements ConDocumento {
    }

    public record ConfirmacionEnlace(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @PasswordSegura String password,
            @NotBlank String confirmacion) {
    }

    public record CambioPassword(
            @NotBlank @Size(max = 64) String actual,
            @NotBlank @PasswordSegura String nueva,
            @NotBlank String confirmacion) {
    }
}
