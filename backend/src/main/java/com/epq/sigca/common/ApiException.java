package com.epq.sigca.common;

import org.springframework.http.HttpStatus;

/** Error de negocio o de validación que se responde al cliente con el estado HTTP indicado. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
