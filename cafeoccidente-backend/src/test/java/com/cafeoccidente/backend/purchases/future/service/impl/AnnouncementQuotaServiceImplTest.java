package com.cafeoccidente.backend.purchases.future.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import com.cafeoccidente.backend.purchases.future.entity.AgencyAnnouncementNumber;
import com.cafeoccidente.backend.purchases.future.entity.Announcement;
import com.cafeoccidente.backend.purchases.future.entity.AnnouncementQuota;
import com.cafeoccidente.backend.purchases.future.repository.AgencyAnnouncementNumberRepository;
import com.cafeoccidente.backend.purchases.future.repository.AnnouncementQuotaRepository;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** "Verificar Entregados": Entregados/Saldo se recalculan en cada consulta, nunca se guardan. */
class AnnouncementQuotaServiceImplTest {

    private final AnnouncementQuotaRepository announcementQuotaRepository = mock(AnnouncementQuotaRepository.class);
    private final AgencyAnnouncementNumberRepository agencyAnnouncementNumberRepository =
            mock(AgencyAnnouncementNumberRepository.class);
    private final DryCoffeePurchaseRepository dryCoffeePurchaseRepository = mock(DryCoffeePurchaseRepository.class);
    private final ControlRecordService controlRecordService = mock(ControlRecordService.class);

    private final AnnouncementQuotaServiceImpl service = new AnnouncementQuotaServiceImpl(
            announcementQuotaRepository, agencyAnnouncementNumberRepository, dryCoffeePurchaseRepository,
            controlRecordService);

    private ControlRecord controlRecord() {
        ControlRecord cr = new ControlRecord();
        cr.setPrefix("SDBU");
        return cr;
    }

    private AgencyAnnouncementNumber numbering() {
        Agency agency = new Agency();
        agency.setId(1L);
        Announcement master = new Announcement();
        master.setSpecialType("RN");
        AgencyAnnouncementNumber numbering = new AgencyAnnouncementNumber();
        numbering.setId(5L);
        numbering.setAgency(agency);
        numbering.setMasterAnnouncement(master);
        numbering.setAnnouncementNumber(32);
        numbering.setAssignedAt(LocalDate.now());
        return numbering;
    }

    @Test
    void saldoIsAssignedQuotaMinusDelivered() {
        AgencyAnnouncementNumber numbering = numbering();
        when(agencyAnnouncementNumberRepository.findByAgencyIdAndAnnouncementNumber(1L, 32))
                .thenReturn(Optional.of(numbering));
        when(controlRecordService.getActive(1L)).thenReturn(controlRecord());
        AnnouncementQuota quota = new AnnouncementQuota();
        quota.setId(9L);
        quota.setAgencyAnnouncementNumber(numbering);
        quota.setAssignedQuota(new BigDecimal("576.70"));
        when(announcementQuotaRepository.findByAgencyAnnouncementNumberId(5L)).thenReturn(Optional.of(quota));
        // toResponse busca por el numero de MUESTRA (prefijo + numero), no por el id interno.
        when(dryCoffeePurchaseRepository.sumNetKgByAgencyAndAnnouncementNumber(1L, "SDBU-32"))
                .thenReturn(new BigDecimal("200.00"));

        AnnouncementQuotaResponse response = service.get(1L, 32);

        assertThat(response.deliveredKg()).isEqualByComparingTo("200.00");
        assertThat(response.balance()).isEqualByComparingTo("376.70");
    }

    @Test
    void assignCreatesTheQuotaWhenNoneExistsYetForThisAnnouncement() {
        AgencyAnnouncementNumber numbering = numbering();
        when(agencyAnnouncementNumberRepository.findByAgencyIdAndAnnouncementNumber(1L, 32))
                .thenReturn(Optional.of(numbering));
        when(announcementQuotaRepository.findByAgencyAnnouncementNumberId(5L)).thenReturn(Optional.empty());
        when(controlRecordService.getActive(1L)).thenReturn(controlRecord());
        when(dryCoffeePurchaseRepository.sumNetKgByAgencyAndAnnouncementNumber(1L, "SDBU-32"))
                .thenReturn(BigDecimal.ZERO);
        when(announcementQuotaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnnouncementQuotaResponse response = service.assign(1L, 32, new AnnouncementQuotaRequest(new BigDecimal("500.00")));

        assertThat(response.assignedQuota()).isEqualByComparingTo("500.00");
        assertThat(response.balance()).isEqualByComparingTo("500.00");
    }

    @Test
    void assignOverwritesTheExistingQuotaInsteadOfDuplicatingIt() {
        AgencyAnnouncementNumber numbering = numbering();
        AnnouncementQuota existing = new AnnouncementQuota();
        existing.setId(9L);
        existing.setAgencyAnnouncementNumber(numbering);
        existing.setAssignedQuota(new BigDecimal("400.00"));
        when(agencyAnnouncementNumberRepository.findByAgencyIdAndAnnouncementNumber(1L, 32))
                .thenReturn(Optional.of(numbering));
        when(announcementQuotaRepository.findByAgencyAnnouncementNumberId(5L)).thenReturn(Optional.of(existing));
        when(controlRecordService.getActive(1L)).thenReturn(controlRecord());
        when(dryCoffeePurchaseRepository.sumNetKgByAgencyAndAnnouncementNumber(1L, "SDBU-32"))
                .thenReturn(BigDecimal.ZERO);
        when(announcementQuotaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnnouncementQuotaResponse response = service.assign(1L, 32, new AnnouncementQuotaRequest(new BigDecimal("650.00")));

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.assignedQuota()).isEqualByComparingTo("650.00");
    }
}
