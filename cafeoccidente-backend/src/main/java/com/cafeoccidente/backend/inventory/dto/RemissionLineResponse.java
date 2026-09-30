package com.cafeoccidente.backend.inventory.dto;

import java.math.BigDecimal;

public record RemissionLineResponse(
        Long id,
        Long inventoryMovementId,
        BigDecimal quantity,
        BigDecimal unitValue,
        BigDecimal outputValue,
        /** Clase de cafe, factor y fondo de la entrada origen: para la remision impresa. */
        String specialType,
        BigDecimal healthyPercentage,
        String fundCode,
        /** Sacos / Kilos Brutos de la salida; null en lineas anteriores a V31. */
        Integer sacos,
        BigDecimal grossKg,
        /** % salida (frpond de Sld3); null en lineas anteriores a V32. */
        BigDecimal exitPercentage) {
}
