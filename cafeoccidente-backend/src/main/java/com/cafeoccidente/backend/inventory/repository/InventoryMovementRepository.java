package com.cafeoccidente.backend.inventory.repository;

import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    Optional<InventoryMovement> findByPurchaseModuleAndPurchaseId(
            InventoryMovement.PurchaseModule purchaseModule, Long purchaseId);

    List<InventoryMovement> findByAgencyIdOrderByPurchaseDateDesc(Long agencyId);

    List<InventoryMovement> findByAgencyIdAndPurchaseDateBetween(Long agencyId, LocalDate from, LocalDate to);

    /** Entradas del Cod_Prod (Sld1/Sld2): SumaDeKilos_Netos, SumaDeVr_Inventario, SumaDeKXFC. */
    @Query("SELECT new com.cafeoccidente.backend.inventory.repository.CodeTotals("
            + "SUM(m.netKg), SUM(m.inventoryValue), SUM(m.netKg * COALESCE(m.healthyPercentage, 0))) "
            + "FROM InventoryMovement m WHERE m.agency.id = :agencyId AND m.productCode.id = :productCodeId "
            + "AND m.purchaseDate BETWEEN :from AND :to")
    CodeTotals sumEntries(@Param("agencyId") Long agencyId, @Param("productCodeId") Long productCodeId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
