package com.cafeoccidente.backend.purchases.future.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseResponse;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import com.cafeoccidente.backend.purchases.future.repository.FuturePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Form_COMPRAS A FUTURO.bas, Texto34_AfterUpdate: fallecido y pertenencia a programa bloquean. */
class FuturePurchaseServiceImplTest {

    private final FuturePurchaseRepository futurePurchaseRepository = mock(FuturePurchaseRepository.class);
    private final AgencyRepository agencyRepository = mock(AgencyRepository.class);
    private final FundRepository fundRepository = mock(FundRepository.class);
    private final AnnouncementService announcementService = mock(AnnouncementService.class);
    private final GrowerService growerService = mock(GrowerService.class);
    private final SecurityUtils securityUtils = mock(SecurityUtils.class);

    private final FuturePurchaseServiceImpl service = new FuturePurchaseServiceImpl(
            futurePurchaseRepository, agencyRepository, fundRepository, announcementService, growerService,
            securityUtils);

    private Agency agency() {
        Agency agency = new Agency();
        agency.setId(1L);
        agency.setName("Buesaco");
        return agency;
    }

    private Fund fundRp() {
        Fund fund = new Fund();
        fund.setId(1L);
        fund.setCode("RP");
        return fund;
    }

    private GrowerResponse grower(String growerType) {
        return new GrowerResponse(
                1L, "123456", "Juan", null, "Perez", null, "Vereda", "3001234567", growerType,
                true, false, false, null, null);
    }

    private AnnouncementResponse announcement() {
        return new AnnouncementResponse(
                1L, "SDBU-1", LocalDate.now(), new BigDecimal("1113500.00"), new BigDecimal("0"),
                new BigDecimal("8908.00"), new BigDecimal("40"), new BigDecimal("692.00"), 1L, 1L, "RN", null);
    }

    private FuturePurchaseRequest request() {
        return new FuturePurchaseRequest(
                1L, "123456", "RN", new BigDecimal("500.00"), LocalDate.now().plusMonths(1),
                "Finca", "Buesaco", "Vereda");
    }

    private void mockHappyPath() {
        when(agencyRepository.findById(1L)).thenReturn(Optional.of(agency()));
        when(growerService.findByIdNumber("123456")).thenReturn(grower("S"));
        when(fundRepository.findByCode("RP")).thenReturn(Optional.of(fundRp()));
        when(announcementService.findLatest(1L, 1L, "RN")).thenReturn(announcement());
        when(futurePurchaseRepository.save(any())).thenAnswer(inv -> {
            FuturePurchase saved = inv.getArgument(0);
            return saved;
        });
    }

    @Test
    void deceasedGrowerIsRejectedBeforeCheckingProgramMembership() {
        when(agencyRepository.findById(1L)).thenReturn(Optional.of(agency()));
        when(growerService.findByIdNumber("123456")).thenReturn(grower("F"));

        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessRuleException.class);

        verify(growerService, never()).requireProgramMembership(any(), any());
    }

    @Test
    void growerOutsideTheProgramIsRejected() {
        when(agencyRepository.findById(1L)).thenReturn(Optional.of(agency()));
        when(growerService.findByIdNumber("123456")).thenReturn(grower("S"));
        doThrow(new BusinessRuleException("CAFICULTOR NO PERTENECE A NINGUN PROGRAMA"))
                .when(growerService).requireProgramMembership("123456", "RN");

        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessRuleException.class);

        verify(futurePurchaseRepository, never()).save(any());
    }

    @Test
    void remainingKgStartsEqualToTheAnnouncedKg() {
        mockHappyPath();

        FuturePurchaseResponse response = service.create(request());

        assertThat(response.remainingKg()).isEqualByComparingTo(response.announcedKg());
        assertThat(response.remainingKg()).isEqualByComparingTo("500.00");
    }

    @Test
    void nullQualityIncrementFromTheAnnouncementDefaultsToZero() {
        mockHappyPath();

        FuturePurchaseResponse response = service.create(request());

        assertThat(response.qualityIncrement()).isEqualByComparingTo("0");
    }

    @Test
    void basePriceLoadIsTheRawAnnouncementValueNotTheNetPrBaseCps() {
        mockHappyPath();

        FuturePurchaseResponse response = service.create(request());

        // A diferencia de Seco/Verde/Otros, Compras a Futuro guarda Pr_Base_CPS crudo del anuncio,
        // sin restar Costos*BaseCarga.
        assertThat(response.basePriceLoad()).isEqualByComparingTo("1113500.00");
    }
}
