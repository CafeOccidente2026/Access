package com.cafeoccidente.backend.purchases.shared.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseContext;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseSellerService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PurchaseContextServiceImplTest {

    private final CurrentAgencyProvider currentAgencyProvider = mock(CurrentAgencyProvider.class);
    private final AgencyRepository agencies = mock(AgencyRepository.class);
    private final FundRepository funds = mock(FundRepository.class);
    private final ProductCodeResolver productCodes = mock(ProductCodeResolver.class);
    private final AnnouncementService announcements = mock(AnnouncementService.class);
    private final ControlRecordService controlRecords = mock(ControlRecordService.class);
    private final PurchaseSellerService sellers = mock(PurchaseSellerService.class);
    private final PurchaseContextServiceImpl service = new PurchaseContextServiceImpl(
            currentAgencyProvider, agencies, funds, productCodes, announcements, controlRecords, sellers);

    private final Agency agency = new Agency();
    private final Fund fund = new Fund();
    private final ProductCode productCode = new ProductCode();
    private final AnnouncementResponse announcement =
            new AnnouncementResponse(9L, "9", null, null, null, null, null, null, 4L, 2L, "RN", null);
    private final ControlRecord controlRecord = new ControlRecord();
    private final PurchaseSeller seller = new PurchaseSeller("1", "A", "B", "S", "D", "C", false);

    @BeforeEach
    void pointWithEverything() {
        agency.setId(4L);
        fund.setId(2L);
        when(currentAgencyProvider.currentAgencyId()).thenReturn(4L);
        when(agencies.findById(4L)).thenReturn(Optional.of(agency));
        when(funds.findById(2L)).thenReturn(Optional.of(fund));
        when(productCodes.resolve("RN", 2L)).thenReturn(productCode);
        when(announcements.findLatest(4L, 2L, "RN")).thenReturn(announcement);
        when(controlRecords.getActive(4L)).thenReturn(controlRecord);
    }

    @Test
    void thePreviewResolvesEverythingFromTheTokenAgencyWithoutWriting() {
        when(sellers.resolve("1", "dir", "cel")).thenReturn(seller);

        PurchaseContext context = service.forPreview(2L, "RN", "1", "dir", "cel");

        assertThat(context).isEqualTo(new PurchaseContext(agency, fund, productCode, announcement, controlRecord, seller));
        verify(sellers, never()).resolveAndComplete(any(), any(), any());
    }

    @Test
    void theSaveAlsoCompletesTheSeller() {
        when(sellers.resolveAndComplete("1", "dir", "cel")).thenReturn(seller);

        PurchaseContext context = service.forSave(2L, "RN", "1", "dir", "cel");

        assertThat(context.seller()).isSameAs(seller);
        assertThat(context.agency()).isSameAs(agency);
        verify(sellers, never()).resolve(any(), any(), any());
    }
}
