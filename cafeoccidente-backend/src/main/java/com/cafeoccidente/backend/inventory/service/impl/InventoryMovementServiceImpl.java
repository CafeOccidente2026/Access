package com.cafeoccidente.backend.inventory.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.inventory.dto.InventoryMovementResponse;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.repository.InventoryMovementRepository;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.ProductCodeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class InventoryMovementServiceImpl implements InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final AgencyRepository agencyRepository;
    private final ProductCodeRepository productCodeRepository;

    public InventoryMovementServiceImpl(
            InventoryMovementRepository inventoryMovementRepository,
            AgencyRepository agencyRepository,
            ProductCodeRepository productCodeRepository) {
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.agencyRepository = agencyRepository;
        this.productCodeRepository = productCodeRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFromPurchaseSafely(
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
            BigDecimal inventoryValue) {
        try {
            Agency agency = agencyRepository.findById(agencyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
            ProductCode productCode = productCodeRepository.findById(productCodeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cod_Prod no encontrado"));

            InventoryMovement movement = new InventoryMovement();
            movement.setPurchaseModule(purchaseModule);
            movement.setPurchaseId(purchaseId);
            movement.setAgency(agency);
            movement.setProductCode(productCode);
            movement.setSpecialType(specialType);
            movement.setInvoiceNumber(invoiceNumber);
            movement.setPurchaseDate(purchaseDate);
            movement.setSacos(sacos);
            movement.setGrossKg(grossKg);
            movement.setNetKg(netKg);
            movement.setRemainingKg(netKg);
            movement.setHealthyPercentage(healthyPercentage);
            movement.setInventoryValue(inventoryValue);
            movement.setCreatedAt(Instant.now());
            inventoryMovementRepository.save(movement);
        } catch (RuntimeException ex) {
            // La compra ya se guardo antes de llegar aca (ver *PurchaseServiceImpl.create()) - el
            // movimiento de inventario es una consecuencia, nunca debe poder tumbar ni revertir la
            // compra ya confirmada. Se loguea para que alguien lo note y, si hace falta, lo cree a
            // mano despues - no hay reintento automatico (ver decision del usuario 2026-09-23).
            log.error(
                    "No se pudo registrar el movimiento de inventario para {} #{} (compra id {}, agencia {})",
                    purchaseModule, invoiceNumber, purchaseId, agencyId, ex);
        }
    }

    @Override
    public List<InventoryMovementResponse> listByAgency(Long agencyId) {
        return inventoryMovementRepository.findByAgencyIdOrderByPurchaseDateDesc(agencyId).stream()
                .map(InventoryMovementServiceImpl::toResponse)
                .toList();
    }

    @Override
    public InventoryMovementResponse findById(Long id) {
        return inventoryMovementRepository.findById(id)
                .map(InventoryMovementServiceImpl::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento de inventario no encontrado"));
    }

    private static InventoryMovementResponse toResponse(InventoryMovement movement) {
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getPurchaseModule(),
                movement.getPurchaseId(),
                movement.getAgency().getId(),
                movement.getAgency().getName(),
                movement.getProductCode().getCode(),
                movement.getSpecialType(),
                movement.getInvoiceNumber(),
                movement.getPurchaseDate(),
                movement.getSacos(),
                movement.getGrossKg(),
                movement.getNetKg(),
                movement.getRemainingKg(),
                movement.getHealthyPercentage(),
                movement.getInventoryValue());
    }
}
