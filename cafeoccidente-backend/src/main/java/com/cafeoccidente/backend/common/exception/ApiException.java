package com.cafeoccidente.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Excepcion base de negocio, se traduce a una respuesta HTTP en GlobalExceptionHandler. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
