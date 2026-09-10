package com.cafeoccidente.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Violacion de una regla de negocio (ej: cedula duplicada, caficultor fallecido). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
