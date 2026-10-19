package co.gov.epq.sigca.lecturas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import co.gov.epq.sigca.common.validation.SinHtml;
import co.gov.epq.sigca.contratos.TipoServicio;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contratos de la API de lecturas y crítica (P-07, RN-35). */
public final class LecturaDtos {

    private LecturaDtos() {
    }

    /** Hay lectura del medidor o causa de no lectura, nunca ambas (CK_LECTURA_TOMADA). */
    @LecturaCoherente
    public record LecturaRegistrar(
            @NotBlank(message = "El serial del medidor es obligatorio")
            @Pattern(regexp = "^[A-Za-z0-9-]{4,30}$", message = "Serial inválido") String serial,
            @NotNull(message = "El periodo es obligatorio") Integer periodo,
            @NotNull(message = "La fecha de lectura es obligatoria")
            @PastOrPresent(message = "La fecha de lectura no puede ser futura") LocalDate fechaLectura,
            @DecimalMin(value = "0", message = "La lectura no puede ser negativa")
            @DecimalMax(value = "999999999.999", message = "Lectura demasiado grande")
            @Digits(integer = 9, fraction = 3, message = "Máximo 3 decimales") BigDecimal lecturaActual,
            CausaNoLectura causaNoLectura,
            @Size(max = 300, message = "Máximo 300 caracteres") @SinHtml String observacion) {
    }

    public enum Decision {
        /** La lectura es correcta: se factura el consumo medido. */
        CONFIRMAR,
        /** La lectura se rechaza: se factura por el promedio de los últimos periodos. */
        ESTIMAR
    }

    public record Revision(
            @NotNull(message = "La decisión es obligatoria") Decision decision,
            @Size(max = 300, message = "Máximo 300 caracteres") @SinHtml String observacion) {
    }

    public record LecturaVista(Long id, Long medidorId, String serial, TipoServicio tipoServicio, String numeroContrato,
            String titular, Long predioId, String codigoPredio, String direccion, Long municipioId, String municipio,
            Integer periodo, LocalDate fechaLectura, BigDecimal lecturaAnterior, BigDecimal lecturaActual, BigDecimal consumo,
            EstadoLectura estado, OrigenLectura origen, CausaNoLectura causaNoLectura, BigDecimal promedioReferencia,
            BigDecimal desviacionPct, String observacion, Long registradaPor, Long revisadaPor, LocalDateTime fechaRevision,
            LocalDateTime creadoEn) {
    }

    /** Resultado de una fila del archivo: se procesa cada una por separado. */
    public record FilaResultado(int linea, String serial, Integer periodo, boolean exito, Long lecturaId, EstadoLectura estado,
            BigDecimal consumo, String mensaje) {
    }

    public record ResultadoCarga(String archivo, int procesadas, int registradas, int confirmadas, int enRevision,
            int estimadas, int conError, List<FilaResultado> filas) {
    }
}
