package com.cafeoccidente.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Excepcion base de negocio, se traduce a una respuesta HTTP en GlobalExceptionHandler. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    /** Para que el frontend reconozca un caso puntual sin depender del texto; null si no aplica. */
    private final String code;

    public ApiException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public ApiException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
