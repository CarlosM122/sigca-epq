package co.gov.epq.sigca.tarifas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import co.gov.epq.sigca.common.validation.SinHtml;
import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.usuarios.EstratoSegunUso;
import co.gov.epq.sigca.usuarios.UsoPredio;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Contratos de la API de tarifas CRA/CREG y factores de subsidio o contribución (P-08, RN-08, RN-37). */
public final class TarifaDtos {

    private TarifaDtos() {
    }

    /** Acueducto y alcantarillado exigen los rangos básico, complementario y suntuario; el gas no los admite. */
    @RangosTarifa
    public record TarifaDatos(
            @NotNull(message = "El tipo de servicio es obligatorio") TipoServicio tipoServicio,
            @NotNull(message = "El municipio es obligatorio") Long municipioId,
            @NotNull(message = "El cargo fijo es obligatorio") @DecimalMin(value = "0", message = "No puede ser negativo")
            @Digits(integer = 12, fraction = 2, message = "Máximo 12 enteros y 2 decimales") BigDecimal cargoFijo,
            @NotNull(message = "El valor del m³ básico es obligatorio") @DecimalMin(value = "0", message = "No puede ser negativo")
            @Digits(integer = 10, fraction = 4, message = "Máximo 10 enteros y 4 decimales") BigDecimal valorM3Basico,
            @DecimalMin(value = "0", message = "No puede ser negativo")
            @Digits(integer = 10, fraction = 4, message = "Máximo 10 enteros y 4 decimales") BigDecimal valorM3Complementario,
            @DecimalMin(value = "0", message = "No puede ser negativo")
            @Digits(integer = 10, fraction = 4, message = "Máximo 10 enteros y 4 decimales") BigDecimal valorM3Suntuario,
            @DecimalMin(value = "0", inclusive = false, message = "Debe ser mayor que cero")
            @Digits(integer = 4, fraction = 2, message = "Máximo 4 enteros y 2 decimales") BigDecimal consumoBasicoM3,
            @DecimalMin(value = "0", inclusive = false, message = "Debe ser mayor que cero")
            @Digits(integer = 4, fraction = 2, message = "Máximo 4 enteros y 2 decimales") BigDecimal consumoSuntuarioDesdeM3,
            @NotNull(message = "La fecha de inicio de vigencia es obligatoria") LocalDate vigenteDesde,
            @NotBlank(message = "La resolución es obligatoria")
            @Pattern(regexp = "(?s)^\\s*\\S.{3,78}\\S\\s*$", message = "La resolución debe tener entre 5 y 80 caracteres")
            @SinHtml String resolucion) {
    }

    /** Subsidio (negativo) o contribución (positivo), con los topes legales de CK_FACTOR_TOPES. */
    @EstratoSegunUso
    @TopesFactor
    public record FactorDatos(
            @NotNull(message = "El municipio es obligatorio") Long municipioId,
            @NotNull(message = "El tipo de servicio es obligatorio") TipoServicio tipoServicio,
            @NotNull(message = "El uso es obligatorio") UsoPredio uso,
            Integer estrato,
            @NotNull(message = "El porcentaje es obligatorio") @DecimalMin(value = "-100", message = "Mínimo -100")
            @DecimalMax(value = "100", message = "Máximo 100")
            @Digits(integer = 3, fraction = 2, message = "Máximo 2 decimales") BigDecimal porcentaje,
            @NotNull(message = "La fecha de inicio de vigencia es obligatoria") LocalDate vigenteDesde,
            @NotBlank(message = "El acuerdo municipal es obligatorio")
            @Pattern(regexp = "(?s)^\\s*\\S.{3,78}\\S\\s*$", message = "El acuerdo debe tener entre 5 y 80 caracteres")
            @SinHtml String acuerdoMunicipal) implements EstratoSegunUso.ConEstrato {
    }

    /** Cierre de vigencia: la tarifa o el factor dejan de aplicar después de esta fecha. */
    public record Cierre(@NotNull(message = "La fecha de fin de vigencia es obligatoria") LocalDate vigenteHasta, Long version) {
    }

    public record TarifaVista(Long id, TipoServicio tipoServicio, Long municipioId, String municipio, BigDecimal cargoFijo,
            BigDecimal valorM3Basico, BigDecimal valorM3Complementario, BigDecimal valorM3Suntuario,
            BigDecimal consumoBasicoM3, BigDecimal consumoSuntuarioDesdeM3, LocalDate vigenteDesde, LocalDate vigenteHasta,
            String resolucion, boolean vigenteHoy, String creadoPor, LocalDateTime creadoEn, Long version) {
    }

    public record FactorVista(Long id, Long municipioId, String municipio, TipoServicio tipoServicio, UsoPredio uso,
            Integer estrato, BigDecimal porcentaje, LocalDate vigenteDesde, LocalDate vigenteHasta, String acuerdoMunicipal,
            boolean vigenteHoy, String creadoPor, LocalDateTime creadoEn, Long version) {
    }

    /** Resultado de "nueva vigencia": la anterior cerrada el día previo (si había) y la nueva. */
    public record NuevaVigencia<T>(T cerrada, T nueva) {
    }
}
