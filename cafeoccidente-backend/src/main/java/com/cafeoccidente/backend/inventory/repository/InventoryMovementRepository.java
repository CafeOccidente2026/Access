package com.cafeoccidente.backend.inventory.repository;

import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
}
