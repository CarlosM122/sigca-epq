package co.gov.epq.sigca.common.error;

import org.springframework.http.HttpStatus;

public class RecursoNoEncontradoException extends NegocioException {

    public RecursoNoEncontradoException(String recurso, Object id) {
        super("RECURSO_NO_ENCONTRADO", recurso + " no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
