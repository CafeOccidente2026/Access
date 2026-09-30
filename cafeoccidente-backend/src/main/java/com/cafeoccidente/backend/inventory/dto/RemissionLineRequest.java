package com.cafeoccidente.backend.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Una linea de salida: cantidad (kilos netos) mas Sacos y Kilos Brutos del despacho (Form_EXITS). */
public record RemissionLineRequest(
        @NotNull Long inventoryMovementId,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive Integer sacos,
        @NotNull @Positive BigDecimal grossKg) {
}
