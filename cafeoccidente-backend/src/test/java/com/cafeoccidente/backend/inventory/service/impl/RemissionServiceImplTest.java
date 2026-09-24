package com.cafeoccidente.backend.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.inventory.dto.RemissionLineRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionResponse;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.repository.InventoryMovementRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionLineRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionRepository;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Form_EXITS.bas, Cantidad_AfterUpdate: "La salida no puede ser superior al saldo". */
class RemissionServiceImplTest {

    private final RemissionRepository remissionRepository = mock(RemissionRepository.class);
    private final RemissionLineRepository remissionLineRepository = mock(RemissionLineRepository.class);
    private final InventoryMovementRepository inventoryMovementRepository = mock(InventoryMovementRepository.class);
    private final AgencyRepository agencyRepository = mock(AgencyRepository.class);
    private final GrowerRepository growerRepository = mock(GrowerRepository.class);
    private final SecurityUtils securityUtils = mock(SecurityUtils.class);

    private final RemissionServiceImpl service = new RemissionServiceImpl(
            remissionRepository, remissionLineRepository, inventoryMovementRepository,
            agencyRepository, growerRepository, securityUtils);

    private Agency agency() {
        Agency agency = new Agency();
        agency.setId(1L);
        agency.setName("Buesaco");
        return agency;
    }

    private InventoryMovement movement(BigDecimal netKg, BigDecimal remainingKg, BigDecimal inventoryValue) {
        InventoryMovement movement = new InventoryMovement();
        movement.setId(10L);
        movement.setPurchaseModule(InventoryMovement.PurchaseModule.DRY_COFFEE);
        movement.setPurchaseId(100L);
        movement.setInvoiceNumber(27867);
        movement.setNetKg(netKg);
        movement.setRemainingKg(remainingKg);
        movement.setInventoryValue(inventoryValue);
        return movement;
    }

    private void mockCommonSaves() {
        when(agencyRepository.findById(1L)).thenReturn(Optional.of(agency()));
        when(remissionRepository.findMaxRemissionNumber(1L)).thenReturn(null);
        when(remissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void quantityAboveRemainingKgIsRejected() {
        mockCommonSaves();
        InventoryMovement movement = movement(new BigDecimal("1200.00"), new BigDecimal("100.00"), new BigDecimal("1506000.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(movement));

        RemissionRequest request = new RemissionRequest(
                1L, LocalDate.now(), "Bodega central", null,
                java.util.List.of(new RemissionLineRequest(10L, new BigDecimal("150.00"))));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleException.class);

        // Como fallo la validacion de saldo, el movimiento nunca se debe haber tocado.
        verify(inventoryMovementRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void quantityWithinRemainingKgComputesUnitValueFromInventoryValueOverNetKg() {
        mockCommonSaves();
        InventoryMovement movement = movement(new BigDecimal("1200.00"), new BigDecimal("1200.00"), new BigDecimal("1506000.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(movement));

        RemissionRequest request = new RemissionRequest(
                1L, LocalDate.now(), "Bodega central", null,
                java.util.List.of(new RemissionLineRequest(10L, new BigDecimal("500.00"))));

        RemissionResponse response = service.create(request);

        // unitValue = 1506000 / 1200 = 1255.00 ; outputValue = 1255 * 500 = 627500.00
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).unitValue()).isEqualByComparingTo("1255.00");
        assertThat(response.lines().get(0).outputValue()).isEqualByComparingTo("627500.00");
    }

    @Test
    void dispatchingDecrementsTheMovementRemainingKg() {
        mockCommonSaves();
        InventoryMovement movement = movement(new BigDecimal("1200.00"), new BigDecimal("1200.00"), new BigDecimal("1506000.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(movement));

        RemissionRequest request = new RemissionRequest(
                1L, LocalDate.now(), "Bodega central", null,
                java.util.List.of(new RemissionLineRequest(10L, new BigDecimal("500.00"))));

        service.create(request);

        assertThat(movement.getRemainingKg()).isEqualByComparingTo("700.00");
        verify(inventoryMovementRepository).save(movement);
    }

    @Test
    void exactlyTheFullRemainingBalanceIsAllowed() {
        mockCommonSaves();
        InventoryMovement movement = movement(new BigDecimal("1200.00"), new BigDecimal("300.00"), new BigDecimal("1506000.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(movement));

        RemissionRequest request = new RemissionRequest(
                1L, LocalDate.now(), "Bodega central", null,
                java.util.List.of(new RemissionLineRequest(10L, new BigDecimal("300.00"))));

        service.create(request);

        assertThat(movement.getRemainingKg()).isEqualByComparingTo("0.00");
    }
}
