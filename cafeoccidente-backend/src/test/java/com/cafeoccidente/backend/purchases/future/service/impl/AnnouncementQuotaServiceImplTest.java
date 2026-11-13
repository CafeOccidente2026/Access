package com.cafeoccidente.backend.purchases.future.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaCandidateResponse;
import com.cafeoccidente.backend.purchases.future.entity.AgencyAnnouncementNumber;
import com.cafeoccidente.backend.purchases.future.entity.Announcement;
import com.cafeoccidente.backend.purchases.future.entity.AnnouncementQuota;
import com.cafeoccidente.backend.purchases.future.repository.AgencyAnnouncementNumberRepository;
import com.cafeoccidente.backend.purchases.future.repository.AnnouncementQuotaRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** HU-29 (RF-21.1): Entregados y Saldo se recalculan con las compras, nunca se guardan. */
class AnnouncementQuotaServiceImplTest {

    private final AnnouncementQuotaRepository announcementQuotaRepository = mock(AnnouncementQuotaRepository.class);
    private final AgencyAnnouncementNumberRepository agencyAnnouncementNumberRepository =
            mock(AgencyAnnouncementNumberRepository.class);
    private final DryCoffeePurchaseRepository dryCoffeePurchaseRepository = mock(DryCoffeePurchaseRepository.class);
    private final ControlRecordService controlRecordService = mock(ControlRecordService.class);
    private final AnnouncementService announcementService = mock(AnnouncementService.class);

    private final AnnouncementQuotaServiceImpl service = new AnnouncementQuotaServiceImpl(
            announcementQuotaRepository, agencyAnnouncementNumberRepository, dryCoffeePurchaseRepository,
            controlRecordService, announcementService);

    private ControlRecord controlRecord() {
        ControlRecord cr = new ControlRecord();
        cr.setPrefix("SDBU");
        return cr;
    }

    private AgencyAnnouncementNumber numbering(int number, String specialType) {
        Agency agency = new Agency();
        agency.setId(1L);
        Announcement master = new Announcement();
        master.setSpecialType(specialType);
        AgencyAnnouncementNumber numbering = new AgencyAnnouncementNumber();
        numbering.setId((long) number);
        numbering.setAgency(agency);
        numbering.setMasterAnnouncement(master);
        numbering.setAnnouncementNumber(number);
        numbering.setAssignedAt(LocalDate.now());
        return numbering;
    }

    private AnnouncementResponse announcement(String displayNumber, String specialType) {
        return new AnnouncementResponse(1L, displayNumber, LocalDate.now(), new BigDecimal("1200000"),
                BigDecimal.ZERO, new BigDecimal("8908"), new BigDecimal("40"), new BigDecimal("692"), 1L, 1L,
                specialType, BigDecimal.ZERO);
    }

    private AnnouncementQuota quota(AgencyAnnouncementNumber numbering, String kg) {
        AnnouncementQuota quota = new AnnouncementQuota();
        quota.setId(9L);
        quota.setAgencyAnnouncementNumber(numbering);
        quota.setAssignedQuota(new BigDecimal(kg));
        return quota;
    }

    private void givenAnnouncement(AgencyAnnouncementNumber numbering, Optional<AnnouncementQuota> quota, String delivered) {
        when(agencyAnnouncementNumberRepository.findByAgencyIdAndAnnouncementNumber(1L, numbering.getAnnouncementNumber()))
                .thenReturn(Optional.of(numbering));
        when(announcementQuotaRepository.findByAgencyAnnouncementNumberId(numbering.getId())).thenReturn(quota);
        when(controlRecordService.getActive(1L)).thenReturn(controlRecord());
        // Busca por el numero de MUESTRA (prefijo + numero), no por el id interno.
        when(dryCoffeePurchaseRepository.sumNetKgByAgencyAndAnnouncementNumber(
                1L, "SDBU-" + numbering.getAnnouncementNumber())).thenReturn(new BigDecimal(delivered));
        when(announcementQuotaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void ca1ListsOnlyAnnouncementsWithoutQuotaAndNeitherGreenNorHusk() {
        when(controlRecordService.getActive(1L)).thenReturn(controlRecord());
        when(announcementService.history()).thenReturn(List.of(
                announcement("SDBU-40", "RN"),
                announcement("SDBU-39", "NESPRESSO - FTUSA"),
                announcement("SDBU-38", "CV"),
                announcement("SDBU-37", "PASILLA")));
        when(announcementQuotaRepository.findByAgencyAnnouncementNumberAgencyId(1L))
                .thenReturn(List.of(quota(numbering(39, "NESPRESSO - FTUSA"), "500")));
        when(dryCoffeePurchaseRepository.sumNetKgByAnnouncementNumber(1L))
                .thenReturn(List.<Object[]>of(new Object[] {"SDBU-39", new BigDecimal("80")}));

        List<QuotaCandidateResponse> candidates = service.available(1L);

        assertThat(candidates).extracting(c -> c.announcement().announcementNumber()).containsExactly("SDBU-40");
    }

    @Test
    void ca2KilosEntregadosIsTheSumOfThePurchasesWithThatAnnouncement() {
        when(controlRecordService.getActive(1L)).thenReturn(controlRecord());
        when(announcementService.history()).thenReturn(List.of(
                announcement("SDBU-40", "RN"), announcement("SDBU-41", "REGIONAL NARIÑO 4C")));
        when(announcementQuotaRepository.findByAgencyAnnouncementNumberAgencyId(1L)).thenReturn(List.of());
        when(dryCoffeePurchaseRepository.sumNetKgByAnnouncementNumber(1L))
                .thenReturn(List.<Object[]>of(new Object[] {"SDBU-40", new BigDecimal("150.50")}));

        List<QuotaCandidateResponse> candidates = service.available(1L);

        assertThat(candidates.get(0).deliveredKg()).isEqualByComparingTo("150.50");
        assertThat(candidates.get(1).deliveredKg()).isEqualByComparingTo("0");
    }

    /** C6 (prueba del 11-nov, punto 12): solo el ultimo anuncio de cada tipo; los viejos ya no se mezclan. */
    @Test
    void c6ListsOnlyTheLatestAnnouncementOfEachTypeWithoutQuota() {
        when(controlRecordService.getActive(1L)).thenReturn(controlRecord());
        when(announcementService.history()).thenReturn(List.of(
                announcement("SDBU-50", "RN"),
                announcement("SDBU-49", "NESPRESSO - FTUSA"),
                announcement("SDBU-48", "RN"),
                announcement("SDBU-47", "NESPRESSO - FTUSA")));
        // El ultimo NESPRESSO ya tiene cupo: de ese tipo no sale ninguno (tampoco el viejo).
        when(announcementQuotaRepository.findByAgencyAnnouncementNumberAgencyId(1L))
                .thenReturn(List.of(quota(numbering(49, "NESPRESSO - FTUSA"), "500")));
        when(dryCoffeePurchaseRepository.sumNetKgByAnnouncementNumber(1L)).thenReturn(List.of());

        assertThat(service.available(1L)).extracting(c -> c.announcement().announcementNumber()).containsExactly("SDBU-50");
    }

    @Test
    void ca3SaldoIsQuotaMinusDelivered() {
        givenAnnouncement(numbering(32, "RN"), Optional.empty(), "200.00");

        AnnouncementQuotaResponse response = service.assign(1L, 32, new AnnouncementQuotaRequest(new BigDecimal("576.70")));

        assertThat(response.assignedQuota()).isEqualByComparingTo("576.70");
        assertThat(response.deliveredKg()).isEqualByComparingTo("200.00");
        assertThat(response.balance()).isEqualByComparingTo("376.70");
    }

    @Test
    void getRecalculatesSaldoFromThePurchases() {
        AgencyAnnouncementNumber numbering = numbering(32, "RN");
        givenAnnouncement(numbering, Optional.of(quota(numbering, "576.70")), "200.00");

        AnnouncementQuotaResponse response = service.get(1L, 32);

        assertThat(response.deliveredKg()).isEqualByComparingTo("200.00");
        assertThat(response.balance()).isEqualByComparingTo("376.70");
    }

    @Test
    void quotaCanNotBeChangedOnceSaved() {
        AgencyAnnouncementNumber numbering = numbering(32, "RN");
        givenAnnouncement(numbering, Optional.of(quota(numbering, "400")), "0");

        AnnouncementQuotaRequest request = new AnnouncementQuotaRequest(new BigDecimal("650.00"));
        assertThatThrownBy(() -> service.assign(1L, 32, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene cupo");
        verify(announcementQuotaRepository, never()).save(any());
    }

    @Test
    void quotaBelowTheKilosAlreadyDeliveredIsRejected() {
        givenAnnouncement(numbering(32, "RN"), Optional.empty(), "200.00");

        AnnouncementQuotaRequest request = new AnnouncementQuotaRequest(new BigDecimal("199.99"));
        assertThatThrownBy(() -> service.assign(1L, 32, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("menor que los kilos ya entregados");
        verify(announcementQuotaRepository, never()).save(any());
    }

    @Test
    void quotaEqualToTheKilosDeliveredLeavesSaldoInZero() {
        givenAnnouncement(numbering(32, "RN"), Optional.empty(), "200.00");

        AnnouncementQuotaResponse response = service.assign(1L, 32, new AnnouncementQuotaRequest(new BigDecimal("200.00")));

        assertThat(response.balance()).isEqualByComparingTo("0");
    }

    @Test
    void greenOrHuskAnnouncementsDoNotTakeQuota() {
        givenAnnouncement(numbering(33, "CV"), Optional.empty(), "0");
        givenAnnouncement(numbering(34, "PASILLA"), Optional.empty(), "0");
        AnnouncementQuotaRequest request = new AnnouncementQuotaRequest(new BigDecimal("100"));

        assertThatThrownBy(() -> service.assign(1L, 33, request)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.assign(1L, 34, request)).isInstanceOf(BusinessRuleException.class);
        verify(announcementQuotaRepository, never()).save(any());
    }
}
