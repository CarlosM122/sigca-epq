package com.epq.sigca.usuario.service;

/** Punto de integración con la validación de identidad (RNEC o un proveedor autorizado). */
public interface ValidadorIdentidad {

   
    boolean validar(String tipoDocumento, String numeroDocumento);
}
