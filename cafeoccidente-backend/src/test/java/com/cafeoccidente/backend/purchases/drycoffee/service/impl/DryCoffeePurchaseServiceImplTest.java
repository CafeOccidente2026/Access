package com.cafeoccidente.backend.purchases.drycoffee.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.drycoffee.mapper.DryCoffeePurchaseMapper;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculator;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseInvoiceNumberService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DryCoffeePurchaseServiceImplTest {

    private final DryCoffeePurchaseRepository repository = mock(DryCoffeePurchaseRepository.class);
    private final ControlRecordService controlRecordService = mock(ControlRecordService.class);
    private final SecurityUtils securityUtils = mock(SecurityUtils.class);

    private final DryCoffeePurchaseServiceImpl service = new DryCoffeePurchaseServiceImpl(
            repository, mock(AgencyRepository.class), mock(FundRepository.class), mock(ProductCodeResolver.class),
            mock(AnnouncementService.class), controlRecordService, new DryCoffeePurchaseCalculator(),
            new DryCoffeePurchaseMapper(), securityUtils, mock(GrowerService.class),
            mock(PurchaseInvoiceNumberService.class), mock(InventoryMovementService.class));

    @Test
    void findByDateFiltersBySessionAgencyAndReturnsEmptyWhenNoPurchases() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        when(securityUtils.getCurrentAgencyId()).thenReturn(4L);
        when(repository.findByAgencyIdAndPurchaseDateOrderByInvoiceNumberAsc(4L, date)).thenReturn(List.of());

        assertThat(service.findByDate(date)).isEmpty();
        verify(repository).findByAgencyIdAndPurchaseDateOrderByInvoiceNumberAsc(4L, date);
        verify(controlRecordService, never()).getActive(any());
    }
}
