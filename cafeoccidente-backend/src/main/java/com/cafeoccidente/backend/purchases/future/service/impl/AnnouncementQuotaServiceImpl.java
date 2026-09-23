package com.cafeoccidente.backend.purchases.future.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import com.cafeoccidente.backend.purchases.future.entity.AgencyAnnouncementNumber;
import com.cafeoccidente.backend.purchases.future.entity.AnnouncementQuota;
import com.cafeoccidente.backend.purchases.future.repository.AgencyAnnouncementNumberRepository;
import com.cafeoccidente.backend.purchases.future.repository.AnnouncementQuotaRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementQuotaServiceImpl implements AnnouncementQuotaService {

    private final AnnouncementQuotaRepository announcementQuotaRepository;
    private final AgencyAnnouncementNumberRepository agencyAnnouncementNumberRepository;
    private final DryCoffeePurchaseRepository dryCoffeePurchaseRepository;
    private final ControlRecordService controlRecordService;

    public AnnouncementQuotaServiceImpl(
            AnnouncementQuotaRepository announcementQuotaRepository,
            AgencyAnnouncementNumberRepository agencyAnnouncementNumberRepository,
            DryCoffeePurchaseRepository dryCoffeePurchaseRepository,
            ControlRecordService controlRecordService) {
        this.announcementQuotaRepository = announcementQuotaRepository;
        this.agencyAnnouncementNumberRepository = agencyAnnouncementNumberRepository;
        this.dryCoffeePurchaseRepository = dryCoffeePurchaseRepository;
        this.controlRecordService = controlRecordService;
    }

    @Override
    public List<AnnouncementQuotaResponse> list(Long agencyId) {
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        return announcementQuotaRepository.findByAgencyAnnouncementNumberAgencyId(agencyId).stream()
                .map(quota -> toResponse(quota, controlRecord))
                .toList();
    }

    @Override
    public AnnouncementQuotaResponse get(Long agencyId, Integer announcementNumber) {
        AgencyAnnouncementNumber numbering = findNumbering(agencyId, announcementNumber);
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        AnnouncementQuota quota = announcementQuotaRepository.findByAgencyAnnouncementNumberId(numbering.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Este anuncio no tiene cupo asignado todavia"));
        return toResponse(quota, controlRecord);
    }

    @Override
    @Transactional
    public AnnouncementQuotaResponse assign(
            Long agencyId, Integer announcementNumber, AnnouncementQuotaRequest request) {
        AgencyAnnouncementNumber numbering = findNumbering(agencyId, announcementNumber);
        AnnouncementQuota quota = announcementQuotaRepository.findByAgencyAnnouncementNumberId(numbering.getId())
                .orElseGet(AnnouncementQuota::new);
        quota.setAgencyAnnouncementNumber(numbering);
        quota.setAssignedQuota(request.assignedQuota());
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        return toResponse(announcementQuotaRepository.save(quota), controlRecord);
    }

    private AgencyAnnouncementNumber findNumbering(Long agencyId, Integer announcementNumber) {
        return agencyAnnouncementNumberRepository.findByAgencyIdAndAnnouncementNumber(agencyId, announcementNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No existe ese anuncio para esta agencia"));
    }

    private AnnouncementQuotaResponse toResponse(AnnouncementQuota quota, ControlRecord controlRecord) {
        AgencyAnnouncementNumber numbering = quota.getAgencyAnnouncementNumber();
        String displayNumber = controlRecord.getPrefix() + "-" + numbering.getAnnouncementNumber();
        BigDecimal delivered = dryCoffeePurchaseRepository.sumNetKgByAgencyAndAnnouncementNumber(
                numbering.getAgency().getId(), displayNumber);
        return new AnnouncementQuotaResponse(
                quota.getId(),
                numbering.getAgency().getId(),
                numbering.getAnnouncementNumber(),
                numbering.getAssignedAt(),
                numbering.getMasterAnnouncement().getSpecialType(),
                quota.getAssignedQuota(),
                delivered,
                quota.getAssignedQuota().subtract(delivered));
    }
}
