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
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

    /** Agencia de trabajo de la sesion (RF-05.1): la 1, la misma que devuelve agencyRepository. */
    private final CurrentAgencyProvider currentAgencyProvider = mock(CurrentAgencyProvider.class, invocation -> 1L);
    /** Hoy en Colombia: 2026-11-07. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("America/Bogota")).toInstant(),
            ZoneId.of("America/Bogota"));
    private final FuturePurchaseServiceImpl service = new FuturePurchaseServiceImpl(
            futurePurchaseRepository, agencyRepository, fundRepository, announcementService, growerService,
            securityUtils, currentAgencyProvider, clock);

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
        return announcement("SDBU-1", TODAY, 1L, "RN");
    }

    private AnnouncementResponse announcement(String number, LocalDate date, Long fundId, String specialType) {
        return new AnnouncementResponse(
                1L, number, date, new BigDecimal("1113500.00"), new BigDecimal("0"),
                new BigDecimal("8908.00"), new BigDecimal("40"), new BigDecimal("692.00"), 1L, fundId, specialType, null);
    }

    private FuturePurchaseRequest request() {
        return new FuturePurchaseRequest(
                "123456", "RN", 1, new BigDecimal("500.00"), TODAY.plusMonths(1),
                "Finca", "Buesaco", "Vereda");
    }

    private void mockHappyPath() {
        when(agencyRepository.findById(1L)).thenReturn(Optional.of(agency()));
        when(growerService.findByIdNumber("123456")).thenReturn(grower("S"));
        when(fundRepository.findByCode("RP")).thenReturn(Optional.of(fundRp()));
        when(announcementService.findForPurchase(1L, 1L, "RN", 1)).thenReturn(announcement());
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
    void growerWithoutLastNameCanBeAnnouncedAndKeepsTheLastNameEmpty() {
        // Asociacion de Access sin "1er Apellido": Access no bloquea, aca tampoco; no se inventa apellido.
        mockHappyPath();
        when(growerService.findByIdNumber("123456")).thenReturn(new GrowerResponse(
                1L, "123456", "FUNDACION SUYUSAMA", null, null, null, "", "", "C", true, false, false, null, null));

        FuturePurchaseResponse response = service.create(request());

        assertThat(response.firstName()).isEqualTo("FUNDACION SUYUSAMA");
        assertThat(response.lastName()).isNull();
    }

    @Test
    void basePriceLoadIsTheRawAnnouncementValueNotTheNetPrBaseCps() {
        mockHappyPath();

        FuturePurchaseResponse response = service.create(request());

        // A diferencia de Seco/Verde/Otros, Compras a Futuro guarda Pr_Base_CPS crudo del anuncio,
        // sin restar Costos*BaseCarga.
        assertThat(response.basePriceLoad()).isEqualByComparingTo("1113500.00");
    }

    @Test
    void ca2ListsOnlyTodaysAnnouncementsOfTheSpecialInFundRp() {
        when(fundRepository.findByCode("RP")).thenReturn(Optional.of(fundRp()));
        when(announcementService.history()).thenReturn(List.of(
                announcement("SDBU-5", TODAY, 1L, "RN"),
                announcement("SDBU-4", TODAY.minusDays(1), 1L, "RN"),
                announcement("SDBU-3", TODAY, 2L, "RN"),
                announcement("SDBU-2", TODAY, 1L, "NESPRESSO - FTUSA")));

        List<AnnouncementResponse> list = service.todayAnnouncements("RN");

        assertThat(list).extracting(AnnouncementResponse::announcementNumber).containsExactly("SDBU-5");
    }

    @Test
    void ca2SavesTheChosenAnnouncementAndItsPrices() {
        mockHappyPath();

        FuturePurchaseResponse response = service.create(request());

        assertThat(response.announcementNumber()).isEqualTo("SDBU-1");
        assertThat(response.announcementDate()).isEqualTo(TODAY);
        assertThat(response.healthyUnitPrice()).isEqualByComparingTo("8908.00");
    }

    @Test
    void anAnnouncementOfAnotherDayIsRejected() {
        mockHappyPath();
        when(announcementService.findForPurchase(1L, 1L, "RN", 1))
                .thenReturn(announcement("SDBU-1", TODAY.minusDays(1), 1L, "RN"));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no es de hoy");
        verify(futurePurchaseRepository, never()).save(any());
    }
}
