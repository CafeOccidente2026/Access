package com.cafeoccidente.backend.purchases.future.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.entity.Announcement;
import com.cafeoccidente.backend.purchases.future.repository.AnnouncementRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import org.springframework.stereotype.Service;

@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public AnnouncementServiceImpl(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    @Override
    public AnnouncementResponse findLatest(Long agencyId, Long fundId) {
        Announcement announcement = announcementRepository
                .findFirstByAgencyIdAndFundIdAndActiveTrueOrderByAnnouncementDateDesc(agencyId, fundId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay un anuncio de precio vigente para esa agencia/fondo"));
        return toResponse(announcement);
    }

    private AnnouncementResponse toResponse(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getAnnouncementNumber(),
                announcement.getAnnouncementDate(),
                announcement.getBasePriceLoad(),
                announcement.getAgency().getId(),
                announcement.getFund().getId());
    }
}
