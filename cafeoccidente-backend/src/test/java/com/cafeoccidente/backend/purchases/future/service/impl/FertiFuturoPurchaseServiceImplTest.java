package com.cafeoccidente.backend.purchases.future.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseRequest;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import com.cafeoccidente.backend.purchases.future.repository.FertiFuturoMonthlyTotals;
import com.cafeoccidente.backend.purchases.future.repository.FertiFuturoPurchaseRepository;
import com.cafeoccidente.backend.purchases.future.repository.FuturePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.future.service.FertiFuturoPurchaseCalculation;
import com.cafeoccidente.backend.purchases.future.service.FertiFuturoPurchaseCalculator;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseInvoiceNumberService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * preview() (agregado 2026-09-24 para dar paridad con los otros 4 modulos de compra, a pedido del
 * usuario) reusa la misma validacion de saldo de compromiso que create() (Texto29_AfterUpdate) pero
 * nunca persiste nada.
 */
class FertiFuturoPurchaseServiceImplTest {

    private final FertiFuturoPurchaseRepository fertiFuturoPurchaseRepository = mock(FertiFuturoPurchaseRepository.class);
    private final FuturePurchaseRepository futurePurchaseRepository = mock(FuturePurchaseRepository.class);
    private final AgencyRepository agencyRepository = mock(AgencyRepository.class);
    private final FundRepository fundRepository = mock(FundRepository.class);
    private final ProductCodeResolver productCodeResolver = mock(ProductCodeResolver.class);
    private final AnnouncementService announcementService = mock(AnnouncementService.class);
    private final ControlRecordService controlRecordService = mock(ControlRecordService.class);
    private final PurchaseInvoiceNumberService purchaseInvoiceNumberService = mock(PurchaseInvoiceNumberService.class);
    private final SecurityUtils securityUtils = mock(SecurityUtils.class);
    private final InventoryMovementService inventoryMovementService = mock(InventoryMovementService.class);

    private final FertiFuturoPurchaseServiceImpl service = new FertiFuturoPurchaseServiceImpl(
            fertiFuturoPurchaseRepository, futurePurchaseRepository, agencyRepository, fundRepository,
            productCodeResolver, announcementService, controlRecordService, new FertiFuturoPurchaseCalculator(),
            purchaseInvoiceNumberService, securityUtils, inventoryMovementService);

    private Agency agency() {
        Agency agency = new Agency();
        agency.setId(1L);
        agency.setName("Buesaco");
        return agency;
    }

    private Fund fund() {
        Fund fund = new Fund();
        fund.setId(1L);
        fund.setCode("RP");
        return fund;
    }

    private AnnouncementResponse announcement() {
        return new AnnouncementResponse(
                1L, "SDBU-1", LocalDate.now(), new BigDecimal("1113500.00"), BigDecimal.ZERO,
                new BigDecimal("8908.00"), new BigDecimal("40"), new BigDecimal("692.00"), 1L, 1L, "RN",
                new BigDecimal("500"));
    }

    private FertiFuturoPurchaseRequest request(Long futurePurchaseId) {
        return new FertiFuturoPurchaseRequest(
                1L, 1L, 27867, "RN", "123456", "Juan", "Perez", "S", "Vereda", futurePurchaseId,
                10, new BigDecimal("1200"), new BigDecimal("1250"), new BigDecimal("220"),
                new BigDecimal("20"), new BigDecimal("50"), false,
                BigDecimal.ZERO, BigDecimal.ZERO, "EFECTIVO", null);
    }

    private void mockAnnouncementLookup() {
        when(agencyRepository.findById(1L)).thenReturn(Optional.of(agency()));
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund()));
        when(announcementService.findLatest(1L, 1L, "RN")).thenReturn(announcement());
        when(fertiFuturoPurchaseRepository.sumMonthlyTotalsByIdNumber(any(), any(), any()))
                .thenReturn(new FertiFuturoMonthlyTotals(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void previewNeverPersistsAnything() {
        mockAnnouncementLookup();

        FertiFuturoPurchaseCalculation result = service.preview(request(null));

        assertThat(result.netToPay()).isGreaterThan(BigDecimal.ZERO);
        verify(fertiFuturoPurchaseRepository, never()).save(any());
        verify(inventoryMovementService, never()).recordFromPurchaseSafely(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void previewRejectsDeliveryAboveTheCommitmentRemainingBalance() {
        mockAnnouncementLookup();
        FuturePurchase futurePurchase = new FuturePurchase();
        futurePurchase.setId(5L);
        futurePurchase.setRemainingKg(new BigDecimal("500"));
        when(futurePurchaseRepository.findById(5L)).thenReturn(Optional.of(futurePurchase));

        // netKg del request = 1200, muy por encima del saldo (500).
        assertThatThrownBy(() -> service.preview(request(5L))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createPersistsAndDecrementsTheLinkedCommitmentBalance() {
        mockAnnouncementLookup();
        when(productCodeResolver.resolve("RN", 1L)).thenReturn(new ProductCode());
        when(controlRecordService.getActive(1L)).thenReturn(new ControlRecord());
        when(fertiFuturoPurchaseRepository.save(any())).thenAnswer(inv -> {
            var p = inv.getArgument(0, com.cafeoccidente.backend.purchases.future.entity.FertiFuturoPurchase.class);
            p.setId(1L);
            return p;
        });
        FuturePurchase futurePurchase = new FuturePurchase();
        futurePurchase.setId(5L);
        futurePurchase.setRemainingKg(new BigDecimal("2000"));
        when(futurePurchaseRepository.findById(5L)).thenReturn(Optional.of(futurePurchase));

        service.create(request(5L));

        assertThat(futurePurchase.getRemainingKg()).isEqualByComparingTo("800");
        verify(futurePurchaseRepository).save(futurePurchase);
    }
}
