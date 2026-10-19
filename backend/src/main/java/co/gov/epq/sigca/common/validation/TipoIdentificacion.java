package co.gov.epq.sigca.common.validation;

/**
 * Tipos de documento de identidad aceptados (requisitos consolidados 7.1).
 */
public enum TipoIdentificacion {
    /** Cédula de ciudadanía: 6 a 10 dígitos, sin ceros a la izquierda. */
    CC("^[1-9]\\d{5,9}$"),
    /** Cédula de extranjería: 6 a 10 caracteres alfanuméricos. */
    CE("^[A-Z0-9]{6,10}$"),
    /** NIT: 9 dígitos + dígito de verificación (formato 900123456-7). */
    NIT("^\\d{9}-\\d$"),
    /** Pasaporte: 5 a 15 caracteres alfanuméricos. */
    PA("^[A-Z0-9]{5,15}$"),
    /** Permiso por Protección Temporal: 6 a 15 dígitos. */
    PPT("^\\d{6,15}$"),
    /** Tarjeta de identidad: 10 u 11 dígitos. */
    TI("^\\d{10,11}$");

    private final String patron;

    TipoIdentificacion(String patron) {
        this.patron = patron;
    }

    public String patron() {
        return patron;
    }
}
