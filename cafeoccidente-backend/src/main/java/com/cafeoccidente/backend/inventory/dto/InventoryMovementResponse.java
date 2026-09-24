package com.cafeoccidente.backend.inventory.dto;

import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryMovementResponse(
        Long id,
        InventoryMovement.PurchaseModule purchaseModule,
        Long purchaseId,
        Long agencyId,
        String agencyName,
        String productCode,
        String specialType,
        Integer invoiceNumber,
        LocalDate purchaseDate,
        Integer sacos,
        BigDecimal grossKg,
        BigDecimal netKg,
        BigDecimal remainingKg,
        BigDecimal healthyPercentage,
        BigDecimal inventoryValue) {
}
