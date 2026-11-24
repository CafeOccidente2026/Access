package com.cafeoccidente.backend.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Demo del 23-nov: lo que digita el usuario (nombres, direcciones, detalles, empresa de transporte, placa, finca...) se
 * guarda en mayusculas, tambien si llega a mano por la API. Se pone con {@code @Convert} en cada campo; correo,
 * contrasena y usuario no lo llevan. Leer devuelve lo guardado tal cual.
 */
@Converter
public class UpperCaseConverter implements AttributeConverter<String, String> {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    @Override
    public String convertToDatabaseColumn(String value) {
        return value == null ? null : value.toUpperCase(SPANISH);
    }

    @Override
    public String convertToEntityAttribute(String value) {
        return value;
    }
}
