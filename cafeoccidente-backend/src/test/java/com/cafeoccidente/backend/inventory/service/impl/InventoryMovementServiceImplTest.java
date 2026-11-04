package com.cafeoccidente.backend.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.repository.InventoryMovementRepository;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.ProductCodeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 6e: el inventario va dentro de la transaccion de la compra. Si no puede registrarse (aca: un
 * product_code_id que no existe), el error sube para que la compra entera se revierta, en vez de
 * quedar una compra sin su entrada de inventario (antes se tragaba el error, decision 2026-09-23).
 */
class InventoryMovementServiceImplTest {

    private final InventoryMovementRepository inventoryMovementRepository = mock(InventoryMovementRepository.class);
    private final AgencyRepository agencyRepository = mock(AgencyRepository.class);
    private final ProductCodeRepository productCodeRepository = mock(ProductCodeRepository.class);
    private final InventoryMovementService service =
            new InventoryMovementServiceImpl(inventoryMovementRepository, agencyRepository, productCodeRepository);

    @Test
    void aFailureIsPropagatedSoThePurchaseRollsBack() {
        when(agencyRepository.findById(1L)).thenReturn(Optional.empty());
        when(productCodeRepository.findById(999999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordFromPurchase(
                        InventoryMovement.PurchaseModule.DRY_COFFEE, 1L, 1L, 999999L, "RN", 27867,
                        LocalDate.now(), 10, new BigDecimal("1250"), new BigDecimal("1200"),
                        new BigDecimal("90"), new BigDecimal("10000000")))
                .isInstanceOf(ResourceNotFoundException.class);

        // Como fallo antes de construir el movimiento, nunca se intento guardar nada.
        verifyNoInteractions(inventoryMovementRepository);
    }

}
