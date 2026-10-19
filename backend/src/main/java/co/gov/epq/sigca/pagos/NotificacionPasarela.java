package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del webhook de la pasarela. La firma ({@code X-Firma-Pasarela}) se calcula sobre el cuerpo crudo.
 *
 * @param estado APROBADA, RECHAZADA o ERROR
 */
public record NotificacionPasarela(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{8,40}$") String referencia,
        @NotBlank @Pattern(regexp = "^(APROBADA|RECHAZADA|ERROR)$") String estado,
        @Pattern(regexp = "^[A-Za-z0-9-]{4,60}$") String codigoTransaccion,
        @Size(max = 20) String codigoRespuesta,
        @Size(max = 300) String mensaje,
        @NotNull BigDecimal monto) {
}
