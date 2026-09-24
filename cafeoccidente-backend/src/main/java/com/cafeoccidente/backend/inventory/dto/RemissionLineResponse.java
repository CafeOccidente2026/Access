package com.cafeoccidente.backend.inventory.dto;

import java.math.BigDecimal;

public record RemissionLineResponse(
        Long id, Long inventoryMovementId, BigDecimal quantity, BigDecimal unitValue, BigDecimal outputValue) {
}
