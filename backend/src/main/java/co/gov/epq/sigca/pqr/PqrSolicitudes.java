package co.gov.epq.sigca.pqr;

import java.math.BigDecimal;

import co.gov.epq.sigca.common.validation.TelefonoColombia;
import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpos de entrada de la API de PQR (validados con Bean Validation antes de llegar a la BD). */
public final class PqrSolicitudes {

    public static final String RADICADO = "^\\d{4}(0[1-9]|1[0-2])-\\d{4}$";
    public static final String MSG_RADICADO = "El radicado tiene el formato AAAAMM-NNNN";
    /** Igual a CK_PQR_NOMBRE: letras, espacios, punto, apóstrofo y guion. */
    public static final String NOMBRE = "^\\p{L}[\\p{L} .'-]{2,149}$";
    public static final String CORREO = "^(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$";

    private PqrSolicitudes() {
    }

    /**
     * Radicación por cualquiera de los tres canales. La parte "datos" del multipart; los soportes viajan en "adjuntos".
     */
    @RadicacionValida
    public record Radicar(
            @NotNull(message = "Seleccione el tipo de solicitud") TipoPqr tipo,
            @Pattern(regexp = "^CTR-\\d{8}$", message = "El contrato tiene el formato CTR-00000000") String numeroContrato,
            @Pattern(regexp = RADICADO, message = MSG_RADICADO) String radicadoOriginal,
            Boolean solicitaApelacion,
            @Pattern(regexp = "^FAC-\\d{6}-\\d{7}$", message = "La factura tiene el formato FAC-AAAAMM-0000000") String numeroFactura,
            TipoIdentificacion tipoIdentificacion,
            @Size(max = 15, message = "Máximo 15 caracteres") String numeroIdentificacion,
            @NotBlank(message = "Indique su nombre")
            @Pattern(regexp = NOMBRE, message = "Nombre inválido: entre 3 y 150 letras, sin números ni símbolos") String nombreContacto,
            @NotBlank(message = "Indique su correo")
            @Size(max = 254) @Pattern(regexp = CORREO, message = "Correo electrónico inválido") String correoContacto,
            @NotBlank(message = "Indique su teléfono") @TelefonoColombia String telefonoContacto,
            Long municipioId,
            @NotBlank(message = "Describa su solicitud")
            @TextoRecortado(min = 20, max = 4000) String descripcion,
            Boolean autorizaTratamientoDatos) {
    }

    public record ConsultaPublica(
            @NotBlank @Pattern(regexp = RADICADO, message = MSG_RADICADO) String radicado,
            @NotNull(message = "Seleccione el tipo de identificación") TipoIdentificacion tipoIdentificacion,
            @NotBlank(message = "Indique el número de identificación") @Size(max = 15) String numeroIdentificacion) {
    }

    public record Actuacion(@NotBlank @TextoRecortado(min = 5, max = 2000) String descripcion) {
    }

    /** Respuesta de EPQ (RN-06). En un recurso, la decisión es obligatoria. */
    public record Respuesta(@NotBlank @TextoRecortado(min = 20, max = 4000) String respuesta,
            DecisionRecurso decisionRecurso) {
    }

    /** Recurso con apelación cuya reposición se confirma: la respuesta y el traslado a la SSPD (P-15). */
    public record Traslado(@NotBlank @TextoRecortado(min = 20, max = 4000) String respuesta) {
    }

    public record DecisionSspd(@NotNull(message = "Seleccione la decisión de la Superintendencia") DecisionRecurso decisionSspd,
            @TextoRecortado(min = 5, max = 2000) String observacion) {
    }

    public record Reasignacion(@NotNull(message = "Seleccione el nuevo responsable") Long gestorId,
            @NotBlank @TextoRecortado(min = 5, max = 300) String motivo) {
    }

    /** Valor reconocido al reclamante sobre la línea en reclamo: cero o negativo (CK_LINEA_AJUSTE). */
    public record AjusteLinea(
            @NotNull(message = "Indique el valor del ajuste")
            @DecimalMax(value = "0", message = "El ajuste es un valor negativo (lo que se descuenta) o cero")
            @Digits(integer = 12, fraction = 2, message = "Máximo 2 decimales") BigDecimal valorAjuste) {
    }

    public record Reconocimiento(@TextoRecortado(min = 5, max = 2000) String observacion) {
    }
}
