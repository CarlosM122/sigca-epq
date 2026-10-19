package co.gov.epq.sigca.cartera;

import java.math.BigDecimal;
import java.util.List;

import co.gov.epq.sigca.common.validation.SinHtml;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Solicitudes de la API de cartera. */
public final class CarteraSolicitudes {

    private CarteraSolicitudes() {
    }

    public record IniciarCobro(@NotNull(message = "Indique el usuario") Long usuarioId,
            @Size(max = 500, message = "Máximo 500 caracteres") @SinHtml String observacion) {
    }

    public record Observacion(@Size(max = 500, message = "Máximo 500 caracteres") @SinHtml String observacion) {
    }

    /** Datos para simular o crear un acuerdo de pago (P-12). */
    public record AcuerdoDatos(
            @NotNull(message = "Indique el usuario") Long usuarioId,
            @NotEmpty(message = "Seleccione al menos una factura vencida") @Size(max = 60, message = "Máximo 60 facturas")
            List<@NotNull Long> facturaIds,
            @NotNull(message = "Indique la cuota inicial") @Positive(message = "La cuota inicial debe ser positiva")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido") BigDecimal cuotaInicial,
            @NotNull(message = "Indique el número de cuotas") @Min(value = 2, message = "Mínimo 2 cuotas")
            @Max(value = 24, message = "Máximo 24 cuotas") Integer numeroCuotas,
            @NotNull(message = "Indique la tasa de interés") @DecimalMin(value = "0", message = "La tasa no puede ser negativa")
            @DecimalMax(value = "100", message = "Tasa inválida") @Digits(integer = 3, fraction = 4, message = "Tasa inválida")
            BigDecimal tasaInteresAnual) {
    }

    public record Anulacion(@NotBlank(message = "Indique el motivo")
            @Size(min = 10, max = 300, message = "El motivo debe tener entre 10 y 300 caracteres") @SinHtml String motivo) {
    }
}
