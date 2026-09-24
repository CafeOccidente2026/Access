package com.cafeoccidente.backend.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
 * Prueba la garantia "no bloqueante" pedida por el usuario 2026-09-23: si recordFromPurchaseSafely
 * falla por el motivo que sea (aca: un product_code_id que no existe), el llamador (cualquiera de
 * los 5 *PurchaseServiceImpl.create()) nunca debe ver una excepcion ni un intento de rollback -
 * la compra ya esta guardada antes de que esto corra.
 */
class InventoryMovementServiceImplTest {

    private final InventoryMovementRepository inventoryMovementRepository = mock(InventoryMovementRepository.class);
    private final AgencyRepository agencyRepository = mock(AgencyRepository.class);
    private final ProductCodeRepository productCodeRepository = mock(ProductCodeRepository.class);
    private final InventoryMovementService service =
            new InventoryMovementServiceImpl(inventoryMovementRepository, agencyRepository, productCodeRepository);

    @Test
    void neverThrowsWhenTheProductCodeDoesNotExist() {
        when(agencyRepository.findById(1L)).thenReturn(Optional.empty());
        when(productCodeRepository.findById(999999L)).thenReturn(Optional.empty());

        // La asercion real es que esta llamada NO lanza nada - ni ResourceNotFoundException ni
        // ninguna otra. Un *PurchaseServiceImpl real llama esto sin try/catch, confiado en que
        // nunca se propaga un error hasta el (ver Javadoc de InventoryMovementService).
        assertThatCode(() -> service.recordFromPurchaseSafely(
                        InventoryMovement.PurchaseModule.DRY_COFFEE, 1L, 1L, 999999L, "RN", 27867,
                        LocalDate.now(), 10, new BigDecimal("1250"), new BigDecimal("1200"),
                        new BigDecimal("90"), new BigDecimal("10000000")))
                .doesNotThrowAnyException();

        // Como fallo antes de construir el movimiento, nunca se intento guardar nada.
        verifyNoInteractions(inventoryMovementRepository);
    }
}
