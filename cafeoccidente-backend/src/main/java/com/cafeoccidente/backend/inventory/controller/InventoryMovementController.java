package com.cafeoccidente.backend.inventory.controller;

import com.cafeoccidente.backend.inventory.dto.InventoryMovementResponse;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Solo lectura: las filas se crean automaticamente desde cada *PurchaseServiceImpl.create(). */
@RestController
@RequestMapping("/api/inventory-movements")
public class InventoryMovementController {

    private final InventoryMovementService inventoryMovementService;

    public InventoryMovementController(InventoryMovementService inventoryMovementService) {
        this.inventoryMovementService = inventoryMovementService;
    }

    @GetMapping
    public List<InventoryMovementResponse> listByAgency(@RequestParam Long agencyId) {
        return inventoryMovementService.listByAgency(agencyId);
    }

    @GetMapping("/{id}")
    public InventoryMovementResponse findById(@PathVariable Long id) {
        return inventoryMovementService.findById(id);
    }
}
