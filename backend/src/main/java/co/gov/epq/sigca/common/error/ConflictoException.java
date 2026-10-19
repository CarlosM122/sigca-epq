package co.gov.epq.sigca.common.error;

import org.springframework.http.HttpStatus;

public class ConflictoException extends NegocioException {

    public ConflictoException(String codigo, String mensaje) {
        super(codigo, mensaje, HttpStatus.CONFLICT);
    }
}
