package com.cafeoccidente.backend.history.service;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Foto de un DTO (record) como texto, para el historial: los decimales sin ceros de mas (94 y 94.00 son
 * lo mismo) y sin los campos excluidos (ids internos; jamas contrasenas).
 */
public final class Snapshots {

    private Snapshots() {
    }

    public static Map<String, String> of(Record dto, String... excluded) {
        Set<String> skip = Set.of(excluded);
        Map<String, String> values = new LinkedHashMap<>();
        for (RecordComponent component : dto.getClass().getRecordComponents()) {
            if (skip.contains(component.getName())) {
                continue;
            }
            try {
                values.put(component.getName(), text(component.getAccessor().invoke(dto)));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("No se pudo leer " + component.getName(), e);
            }
        }
        return values;
    }

    public static String text(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal number ? number.stripTrailingZeros().toPlainString() : value.toString();
    }
}
