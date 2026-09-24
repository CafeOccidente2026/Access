package com.cafeoccidente.backend.inventory.service;

import com.cafeoccidente.backend.inventory.dto.InventoryMovementResponse;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface InventoryMovementService {

    /**
     * Registra la entrada de inventario de una compra ya guardada (Access "INVENTARIO"). NUNCA
     * lanza excepción: cualquier error se loguea y se descarta - la compra ya se guardó antes de
     * llamar esto y no debe poder revertirse ni fallar por esto (ver *PurchaseServiceImpl.create()).
     */
    void recordFromPurchaseSafely(
            InventoryMovement.PurchaseModule purchaseModule,
            Long purchaseId,
            Long agencyId,
            Long productCodeId,
            String specialType,
            Integer invoiceNumber,
            LocalDate purchaseDate,
            Integer sacos,
            BigDecimal grossKg,
            BigDecimal netKg,
            BigDecimal healthyPercentage,
            BigDecimal inventoryValue);

    List<InventoryMovementResponse> listByAgency(Long agencyId);

    InventoryMovementResponse findById(Long id);
}
