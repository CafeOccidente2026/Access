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
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFromPurchase(
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
    }

    @Override
    public List<InventoryMovementResponse> listByAgency(Long agencyId) {
        Map<Long, Object[]> sellers = sellerAndFund(agencyId);
        return inventoryMovementRepository.findByAgencyIdOrderByPurchaseDateDesc(agencyId).stream()
                .map(movement -> toResponse(movement, sellers))
                .toList();
    }

    @Override
    public InventoryMovementResponse findById(Long id) {
        InventoryMovement movement = inventoryMovementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento de inventario no encontrado"));
        return toResponse(movement, sellerAndFund(movement.getAgency().getId()));
    }

    private Map<Long, Object[]> sellerAndFund(Long agencyId) {
        return inventoryMovementRepository.findSellerAndFund(agencyId).stream()
                .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> row));
    }

    private static InventoryMovementResponse toResponse(InventoryMovement movement, Map<Long, Object[]> sellers) {
        Object[] seller = sellers.getOrDefault(movement.getId(), new Object[3]);
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
                movement.getInventoryValue(),
                (String) seller[1],
                (String) seller[2]);
    }
}
