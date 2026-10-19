package co.gov.epq.sigca.common.cifrado;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Claves para proteger el número de identificación (RNF-04). Ambas en Base64, 32 bytes cada una,
 * entregadas por variable de entorno o gestor de secretos; nunca en el repositorio.
 */
@Validated
@ConfigurationProperties("sigca.cifrado")
public record CifradoProperties(@NotBlank String claveAes, @NotBlank String claveHmac) {
}
