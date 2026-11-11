package com.cafeoccidente.backend.common.exception;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Nombre en pantalla de los campos de los request (prueba del 11-nov, punto 4): una validacion dice "Falta o esta
 * mal: Kilos Brutos" en vez de "grossKg". Un campo que no este aca sale con su nombre tal cual.
 */
final class FieldLabels {

    /** Etiquetas en src/main/resources/field-labels.json: textos fuera del codigo. */
    private static final Map<String, String> LABELS = load();

    private FieldLabels() {
    }

    private static Map<String, String> load() {
        try (InputStream json = FieldLabels.class.getResourceAsStream("/field-labels.json")) {
            return Map.copyOf(JsonMapper.builder().build().readValue(json, new TypeReference<Map<String, String>>() { }));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer field-labels.json", e);
        }
    }

    /** "payment.cashAmount" -> "Efectivo": cuenta el ultimo tramo del campo anidado. */
    static String of(String field) {
        String last = field.substring(field.lastIndexOf('.') + 1);
        int index = last.indexOf('[');
        String name = index < 0 ? last : last.substring(0, index);
        return LABELS.getOrDefault(name, name);
    }
}
