package co.gov.epq.sigca.usuarios;

import java.time.LocalDateTime;
import java.util.List;

import co.gov.epq.sigca.common.validation.ConDocumento;
import co.gov.epq.sigca.common.validation.DocumentoValido;
import co.gov.epq.sigca.common.validation.SinHtml;
import co.gov.epq.sigca.common.validation.TelefonoColombia;
import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import co.gov.epq.sigca.common.validation.TipoPersona;
import co.gov.epq.sigca.contratos.ContratoDtos.ContratoVista;
import co.gov.epq.sigca.usuarios.PredioDtos.PredioVista;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contratos de la API de usuarios (RF-01, RF-02, RF-03, RF-16). Nunca exponen el número de identificación completo. */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    /** SWR-09: registro de un usuario por el asesor. */
    @DocumentoValido
    @NombreSegunPersona
    public record UsuarioCrear(
            @NotNull(message = "El tipo de persona es obligatorio") TipoPersona tipoPersona,
            @NotNull(message = "El tipo de identificación es obligatorio") TipoIdentificacion tipoIdentificacion,
            @NotBlank(message = "El número de identificación es obligatorio") @Size(max = 15) String numeroIdentificacion,
            @NotBlank(message = "El nombre es obligatorio") @Size(min = 3, max = 150) String nombreCompleto,
            @NotBlank(message = "El correo es obligatorio") @Size(max = 254)
            @Pattern(regexp = Textos.CORREO, message = "Correo inválido") String correo,
            @NotBlank(message = "El teléfono es obligatorio") @TelefonoColombia String telefono,
            @NotBlank(message = "La dirección es obligatoria")
            @Pattern(regexp = Textos.TEXTO_5_200, message = "La dirección debe tener entre 5 y 200 caracteres") @SinHtml String direccionNotificacion,
            @NotNull(message = "El municipio es obligatorio") Long municipioId,
            @NotNull(message = "Debe autorizar el tratamiento de datos personales")
            @AssertTrue(message = "Debe autorizar el tratamiento de datos personales (Ley 1581 de 2012)") Boolean autorizaTratamientoDatos)
            implements ConDocumento, NombreSegunPersona.ConNombre {
    }

    /** RF-02: el tipo de persona y la identificación no cambian. */
    public record UsuarioModificar(
            @NotBlank(message = "El nombre es obligatorio") @Size(min = 3, max = 150) String nombreCompleto,
            @NotBlank(message = "El correo es obligatorio") @Size(max = 254)
            @Pattern(regexp = Textos.CORREO, message = "Correo inválido") String correo,
            @NotBlank(message = "El teléfono es obligatorio") @TelefonoColombia String telefono,
            @NotBlank(message = "La dirección es obligatoria")
            @Pattern(regexp = Textos.TEXTO_5_200, message = "La dirección debe tener entre 5 y 200 caracteres") @SinHtml String direccionNotificacion,
            @NotNull(message = "El municipio es obligatorio") Long municipioId,
            Long version) {
    }

    /** Actualización de solo los datos de contacto (ACTUALIZAR_CONTACTO). */
    public record ContactoActualizar(
            @NotBlank(message = "El correo es obligatorio") @Size(max = 254)
            @Pattern(regexp = Textos.CORREO, message = "Correo inválido") String correo,
            @NotBlank(message = "El teléfono es obligatorio") @TelefonoColombia String telefono,
            @NotBlank(message = "La dirección es obligatoria")
            @Pattern(regexp = Textos.TEXTO_5_200, message = "La dirección debe tener entre 5 y 200 caracteres") @SinHtml String direccionNotificacion,
            Long version) {
    }

    /** RF-16 / P-24: eliminación lógica con motivo. */
    public record Inactivacion(
            @NotBlank(message = "El motivo es obligatorio")
            @Pattern(regexp = Textos.TEXTO_5_300, message = "El motivo debe tener entre 5 y 300 caracteres") @SinHtml String motivo,
            Long version) {
    }

    public record UsuarioVista(Long id, String codigo, TipoPersona tipoPersona, TipoIdentificacion tipoIdentificacion,
            String identificacionMascara, String nombreCompleto, String correo, String telefono,
            String direccionNotificacion, Long municipioId, String municipio,
            EstadoVerificacionRnec estadoVerificacionRnec, LocalDateTime fechaVerificacionRnec, boolean activo,
            LocalDateTime fechaInactivacion, String motivoInactivacion, String cuentaPortal, LocalDateTime creadoEn,
            Long version, String advertencia) {
    }

    /** Detalle: el usuario con los predios donde tiene contratos y sus contratos. */
    public record UsuarioDetalle(UsuarioVista usuario, List<PredioVista> predios, List<ContratoVista> contratos) {
    }
}
