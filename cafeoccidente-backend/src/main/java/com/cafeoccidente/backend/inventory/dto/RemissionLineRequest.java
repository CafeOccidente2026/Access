package com.cafeoccidente.backend.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RemissionLineRequest(@NotNull Long inventoryMovementId, @NotNull @Positive BigDecimal quantity) {
}
