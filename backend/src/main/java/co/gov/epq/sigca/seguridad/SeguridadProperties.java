package co.gov.epq.sigca.seguridad;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Parámetros técnicos de seguridad. Los de negocio (intentos, minutos de bloqueo, vigencia de enlaces)
 * están en PARAMETRO_SISTEMA. El secreto JWT se exige al arrancar (mínimo 32 caracteres = 256 bits).
 *
 * @param duracionAcceso     vigencia de cada token de acceso (P-20: 15 minutos)
 * @param duracionRenovacion tiempo máximo de una sesión renovando el token sin volver a autenticarse
 */
@Validated
@ConfigurationProperties(prefix = "sigca.seguridad")
public record SeguridadProperties(
        @NotNull @Size(min = 32, message = "sigca.seguridad.jwt-secret debe tener al menos 32 caracteres") String jwtSecret,
        @NotNull Duration duracionAcceso,
        @NotNull Duration duracionRenovacion,
        @NotEmpty List<String> corsOrigenes,
        @NotBlank String emisorDobleFactor) {
}
