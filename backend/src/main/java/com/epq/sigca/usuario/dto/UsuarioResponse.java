package com.epq.sigca.usuario.dto;

/** Respuesta de un registro exitoso. Nunca incluye la contraseña ni su hash. */
public record UsuarioResponse(
        Long id,
        String tipoDocumento,
        String numeroDocumento,
        String nombre,
        String email,
        String estado) {
}
