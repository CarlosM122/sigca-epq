package com.epq.sigca.usuario.service;

import org.springframework.stereotype.Component;

/**
 * Simulación para el prototipo. Acepta cualquier documento excepto los formados solo por
 * ceros (p. ej. 0000000000), para poder demostrar el caso de validación fallida.
 * Para producción, reemplazar por una clase que llame al servicio real e implemente la
 * interfaz ValidadorIdentidad; el resto del código no cambia.
 */
@Component
public class ValidadorIdentidadSimulado implements ValidadorIdentidad {

    @Override
    public boolean validar(String tipoDocumento, String numeroDocumento) {
        return !numeroDocumento.matches("0+");
    }
}
