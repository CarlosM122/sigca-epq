package co.gov.epq.sigca.contratos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import co.gov.epq.sigca.common.validation.SinHtml;
import co.gov.epq.sigca.usuarios.Textos;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contratos de la API de contratos, medidores y órdenes de servicio (RF-04, RF-17, RF-18, P-06, P-11). */
public final class ContratoDtos {

    private ContratoDtos() {
    }

    /**
     * RF-04. Para acueducto y gas, si no se indica el medidor se usa el medidor activo del predio para ese servicio;
     * el alcantarillado no lleva medidor y exige un acueducto vigente del mismo usuario en el predio (lo verifica la BD).
     */
    public record ContratoCrear(
            @NotNull(message = "El usuario es obligatorio") Long usuarioId,
            @NotNull(message = "El predio es obligatorio") Long predioId,
            @NotNull(message = "El tipo de servicio es obligatorio") TipoServicio tipoServicio,
            Long medidorId,
            @NotNull(message = "La fecha de vinculación es obligatoria")
            @PastOrPresent(message = "La fecha de vinculación no puede ser futura") LocalDate fechaVinculacion) {
    }

    /** P-11: cancelar exige no tener deuda ni órdenes pendientes (lo verifica la BD). */
    public record Cancelacion(
            @NotBlank(message = "El motivo es obligatorio")
            @Pattern(regexp = Textos.TEXTO_5_300, message = "El motivo debe tener entre 5 y 300 caracteres") @SinHtml String motivo,
            Long version) {
    }

    public record ContratoVista(Long id, String numeroContrato, Long usuarioId, String codigoUsuario, String titular,
            Long predioId, String codigoPredio, String direccion, Long municipioId, String municipio,
            TipoServicio tipoServicio, Long medidorId, String serialMedidor, LocalDate fechaVinculacion,
            EstadoContrato estado, LocalDateTime fechaEstado, String motivoEstado, Long version) {
    }

    /** Instalación de un medidor (7.3): solo acueducto o gas. */
    public record MedidorInstalar(
            @NotBlank(message = "El serial es obligatorio")
            @Pattern(regexp = "^[A-Za-z0-9-]{4,30}$", message = "El serial tiene de 4 a 30 letras, números o guiones") String serial,
            @NotNull(message = "El tipo de servicio es obligatorio") TipoServicio tipoServicio,
            @Size(max = 60, message = "Máximo 60 caracteres") @SinHtml String marca,
            @NotNull(message = "La fecha de instalación es obligatoria")
            @PastOrPresent(message = "La fecha de instalación no puede ser futura") LocalDate fechaInstalacion,
            @NotNull(message = "La lectura inicial es obligatoria")
            @DecimalMin(value = "0", message = "La lectura inicial no puede ser negativa")
            @DecimalMax(value = "999999999.999", message = "Lectura demasiado grande")
            @Digits(integer = 9, fraction = 3, message = "Máximo 3 decimales") BigDecimal lecturaInicial) {
    }

    public record MedidorRetiro(
            @NotNull(message = "La fecha de retiro es obligatoria")
            @PastOrPresent(message = "La fecha de retiro no puede ser futura") LocalDate fechaRetiro,
            Long version) {
    }

    public record MedidorVista(Long id, String serial, Long predioId, TipoServicio tipoServicio, String marca,
            LocalDate fechaInstalacion, BigDecimal lecturaInicial, BigDecimal lecturaActual, Integer ultimoPeriodo,
            boolean lecturaEnRevision, EstadoMedidor estado, LocalDate fechaRetiro, String numeroContrato, Long version) {
    }

    /** Órdenes que crea el asesor: la de MORA la crea cartera y la reconexión por PAGO o ACUERDO la crea la BD. */
    public record OrdenCrear(
            @NotNull(message = "El contrato es obligatorio") Long contratoId,
            @NotNull(message = "El tipo de orden es obligatorio") TipoOrden tipo,
            @NotNull(message = "La causa es obligatoria") CausaOrden causa,
            @NotBlank(message = "La observación es obligatoria")
            @Pattern(regexp = "(?s)^\\s*\\S.{3,498}\\S\\s*$", message = "La observación debe tener entre 5 y 500 caracteres")
            @SinHtml String observacion) {
    }

    public record OrdenEjecutar(@Size(max = 500, message = "Máximo 500 caracteres") @SinHtml String observacion, Long version) {
    }

    public record OrdenAnular(
            @NotBlank(message = "El motivo es obligatorio")
            @Pattern(regexp = Textos.TEXTO_5_300, message = "El motivo debe tener entre 5 y 300 caracteres") @SinHtml String motivo,
            Long version) {
    }

    public record OrdenVista(Long id, String numeroOrden, Long contratoId, String numeroContrato, TipoServicio tipoServicio,
            String titular, String direccion, Long municipioId, String municipio, TipoOrden tipo, CausaOrden causa,
            LocalDateTime fechaOrden, LocalDateTime fechaLimite, BigDecimal valorCargo, EstadoOrden estado,
            LocalDateTime fechaEjecucion, Long ejecutadaPor, String motivoAnulacion, String observacion, String creadoPor,
            boolean vencida, Long version) {
    }
}
