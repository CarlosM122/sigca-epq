package co.gov.epq.sigca.common.error;

import org.springframework.http.HttpStatus;

/**
 * Excepción base para violaciones de reglas de negocio. Cada subclase define su código y estado HTTP.
 */
public class NegocioException extends RuntimeException {

    private final String codigo;
    private final HttpStatus status;

    public NegocioException(String codigo, String mensaje, HttpStatus status) {
        super(mensaje);
        this.codigo = codigo;
        this.status = status;
    }

    public NegocioException(String codigo, String mensaje) {
        this(codigo, mensaje, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    public String getCodigo() {
        return codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
