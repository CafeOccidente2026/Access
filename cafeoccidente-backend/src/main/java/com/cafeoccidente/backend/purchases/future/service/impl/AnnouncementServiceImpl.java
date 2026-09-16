package com.cafeoccidente.backend.purchases.future.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.entity.Announcement;
import com.cafeoccidente.backend.purchases.future.repository.AnnouncementRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final ControlRecordService controlRecordService;
    private final SecurityUtils securityUtils;

    public AnnouncementServiceImpl(
            AnnouncementRepository announcementRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            ControlRecordService controlRecordService,
            SecurityUtils securityUtils) {
        this.announcementRepository = announcementRepository;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.controlRecordService = controlRecordService;
        this.securityUtils = securityUtils;
    }

    @Override
    public AnnouncementResponse findLatest(Long agencyId, Long fundId, String specialType) {
        Announcement announcement = announcementRepository
                .findFirstByAgencyIdAndFundIdAndSpecialTypeAndActiveTrueOrderByAnnouncementDateDescIdDesc(
                        agencyId, fundId, specialType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay un anuncio de precio vigente para esa agencia/fondo/especial"));
        return toResponse(announcement);
    }

    @Override
    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request) {
        Long agencyId = securityUtils.getCurrentAgencyId();
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        BigDecimal baseLoad = BigDecimal.valueOf(controlRecord.getBaseLoad());

        Integer maxNumber = announcementRepository.findMaxAnnouncementNumber(agencyId);
        int nextNumber = maxNumber == null ? 1 : maxNumber + 1;

        // Formulas reales (Form_ANUNCIOS CORRF.bas):
        // Pr_Sustentación = Pr_Base_CPS / BaseCarga - Costos ; Bonificacion = SobrePr_CPS / BaseCarga
        BigDecimal healthyUnitPrice = request.basePriceLoad()
                .divide(baseLoad, 2, RoundingMode.HALF_UP)
                .subtract(controlRecord.getCosts());
        BigDecimal bonus = request.specialSurcharge().divide(baseLoad, 2, RoundingMode.HALF_UP);

        Announcement announcement = new Announcement();
        announcement.setAnnouncementNumber(String.valueOf(nextNumber));
        announcement.setAnnouncementDate(LocalDate.now());
        announcement.setBasePriceLoad(request.basePriceLoad());
        announcement.setDefectiveUnitPrice(request.defectiveUnitPrice());
        announcement.setHealthyUnitPrice(healthyUnitPrice);
        announcement.setBonus(bonus);
        announcement.setCosts(controlRecord.getCosts());
        announcement.setAgency(agency);
        announcement.setFund(fund);
        announcement.setSpecialType(request.specialType());
        announcement.setActive(true);

        return toResponse(announcementRepository.save(announcement));
    }

    @Override
    public List<AnnouncementResponse> history() {
        Long agencyId = securityUtils.getCurrentAgencyId();
        return announcementRepository.findByAgencyIdOrderByAnnouncementDateDescIdDesc(agencyId).stream()
                .map(this::toResponse)
                .toList();
    }

    private AnnouncementResponse toResponse(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getAnnouncementNumber(),
                announcement.getAnnouncementDate(),
                announcement.getBasePriceLoad(),
                announcement.getDefectiveUnitPrice(),
                announcement.getHealthyUnitPrice(),
                announcement.getBonus(),
                announcement.getCosts(),
                announcement.getAgency().getId(),
                announcement.getFund().getId(),
                announcement.getSpecialType());
    }
}
