package com.cafeoccidente.backend.inventory.repository;

import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    Optional<InventoryMovement> findByPurchaseModuleAndPurchaseId(
            InventoryMovement.PurchaseModule purchaseModule, Long purchaseId);

    List<InventoryMovement> findByAgencyIdOrderByPurchaseDateDesc(Long agencyId);
}
