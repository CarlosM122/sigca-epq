package co.gov.epq.sigca.common.validation;

/**
 * Contrato para DTOs que transportan un documento de identidad y pueden validarse con {@link DocumentoValido}.
 */
public interface ConDocumento {

    TipoIdentificacion tipoIdentificacion();

    String numeroIdentificacion();

    default TipoPersona tipoPersona() {
        return null;
    }
}
