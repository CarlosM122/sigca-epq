package com.epq.sigca.usuario.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lo que envía el formulario de registro (POST /api/usuarios).
 * Nombres/apellidos (persona natural) y razón social (persona jurídica) son opcionales aquí:
 * cuál es obligatorio depende del tipo de documento y lo valida el servicio.
 */
public record RegistroUsuarioRequest(

        @NotBlank(message = "Seleccione el tipo de documento")
        String tipoDocumento,

        @NotBlank(message = "Ingrese el número de documento")
        @Size(max = 20, message = "El documento admite máximo 20 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "El documento solo admite letras, números y guion")
        String numeroDocumento,

        @Size(max = 100, message = "Los nombres admiten máximo 100 caracteres")
        String nombres,

        @Size(max = 100, message = "Los apellidos admiten máximo 100 caracteres")
        String apellidos,

        @Size(max = 200, message = "La razón social admite máximo 200 caracteres")
        String razonSocial,

        @NotBlank(message = "Ingrese el correo electrónico")
        @Size(max = 254, message = "El correo admite máximo 254 caracteres")
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Correo electrónico inválido")
        String email,

        @NotBlank(message = "Ingrese el teléfono")
        @Pattern(regexp = "^[0-9]{7,15}$", message = "El teléfono debe tener entre 7 y 15 dígitos")
        String telefono,

        @NotBlank(message = "Ingrese la contraseña")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
                message = "La contraseña debe tener entre 8 y 72 caracteres, con letras y números")
        String password,

        @NotBlank(message = "Confirme la contraseña")
        String confirmarPassword,

        @AssertTrue(message = "Debe aceptar el tratamiento de datos personales")
        boolean aceptaTratamientoDatos) {

   
    @Override
    public String toString() {
        return "RegistroUsuarioRequest[tipoDocumento=" + tipoDocumento
                + ", numeroDocumento=" + numeroDocumento + ", email=" + email + "]";
    }
}
