package co.gov.epq.sigca.pagos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pasarela de pagos (P-18). El secreto del webhook llega por variable de entorno (SIGCA_PASARELA_SECRETO).
 *
 * @param modo               {@code simulado} en desarrollo; un adaptador real (Wompi, PayU…) usaría otro valor
 * @param urlCheckout        URL base a la que se redirige al ciudadano; se le agrega la referencia
 * @param secretoWebhook     clave HMAC-SHA256 compartida con la pasarela
 * @param minutosExpiracion  minutos tras los que un intento INICIADO sin respuesta se da por EXPIRADO
 */
@Validated
@ConfigurationProperties("sigca.integraciones.pasarela")
public record PasarelaProperties(
        @DefaultValue("simulado") String modo,
        @NotBlank String urlCheckout,
        @NotBlank @Size(min = 32, message = "El secreto del webhook debe tener al menos 32 caracteres") String secretoWebhook,
        @DefaultValue("30") int minutosExpiracion) {
}
