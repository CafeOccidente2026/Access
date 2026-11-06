package com.cafeoccidente.backend.purchases.future.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaCandidateResponse;
import com.cafeoccidente.backend.purchases.future.entity.AgencyAnnouncementNumber;
import com.cafeoccidente.backend.purchases.future.entity.AnnouncementQuota;
import com.cafeoccidente.backend.purchases.future.repository.AgencyAnnouncementNumberRepository;
import com.cafeoccidente.backend.purchases.future.repository.AnnouncementQuotaRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementQuotaServiceImpl implements AnnouncementQuotaService {

    /** RN-21.1.4: pasilla y cafe verde no llevan cupo (se reconocen por el Especial). */
    private static final Set<String> SPECIALS_WITHOUT_QUOTA = Set.of("CV", "PASILLA");

    private final AnnouncementQuotaRepository announcementQuotaRepository;
    private final AgencyAnnouncementNumberRepository agencyAnnouncementNumberRepository;
    private final DryCoffeePurchaseRepository dryCoffeePurchaseRepository;
    private final ControlRecordService controlRecordService;
    private final AnnouncementService announcementService;

    public AnnouncementQuotaServiceImpl(
            AnnouncementQuotaRepository announcementQuotaRepository,
            AgencyAnnouncementNumberRepository agencyAnnouncementNumberRepository,
            DryCoffeePurchaseRepository dryCoffeePurchaseRepository,
            ControlRecordService controlRecordService,
            AnnouncementService announcementService) {
        this.announcementQuotaRepository = announcementQuotaRepository;
        this.agencyAnnouncementNumberRepository = agencyAnnouncementNumberRepository;
        this.dryCoffeePurchaseRepository = dryCoffeePurchaseRepository;
        this.controlRecordService = controlRecordService;
        this.announcementService = announcementService;
    }

    @Override
    public List<AnnouncementQuotaResponse> list(Long agencyId) {
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        return announcementQuotaRepository.findByAgencyAnnouncementNumberAgencyId(agencyId).stream()
                .map(quota -> toResponse(quota, controlRecord))
                .toList();
    }

    // ponytail: la lista crece con cada anuncio de la agencia; filtrar en la consulta si se vuelve lenta.
    @Override
    public List<QuotaCandidateResponse> available(Long agencyId) {
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        Set<String> withQuota = announcementQuotaRepository.findByAgencyAnnouncementNumberAgencyId(agencyId).stream()
                .map(quota -> displayNumber(controlRecord, quota.getAgencyAnnouncementNumber()))
                .collect(Collectors.toSet());
        Map<String, BigDecimal> delivered = dryCoffeePurchaseRepository.sumNetKgByAnnouncementNumber(agencyId).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (BigDecimal) row[1]));
        return announcementService.history().stream()
                .filter(a -> !SPECIALS_WITHOUT_QUOTA.contains(a.specialType()))
                .filter(a -> !withQuota.contains(a.announcementNumber()))
                .map(a -> new QuotaCandidateResponse(a, delivered.getOrDefault(a.announcementNumber(), BigDecimal.ZERO)))
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
        if (SPECIALS_WITHOUT_QUOTA.contains(numbering.getMasterAnnouncement().getSpecialType())) {
            throw new BusinessRuleException("Los anuncios de pasilla y de cafe verde no llevan cupo");
        }
        if (announcementQuotaRepository.findByAgencyAnnouncementNumberId(numbering.getId()).isPresent()) {
            throw new BusinessRuleException("Este anuncio ya tiene cupo; no se puede cambiar");
        }
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        BigDecimal delivered = deliveredKg(numbering, controlRecord);
        if (request.assignedQuota().compareTo(delivered) < 0) {
            throw new BusinessRuleException(
                    "El cupo no puede ser menor que los kilos ya entregados (" + delivered.toPlainString() + ")");
        }
        AnnouncementQuota quota = new AnnouncementQuota();
        quota.setAgencyAnnouncementNumber(numbering);
        quota.setAssignedQuota(request.assignedQuota());
        return toResponse(announcementQuotaRepository.save(quota), controlRecord);
    }

    private AgencyAnnouncementNumber findNumbering(Long agencyId, Integer announcementNumber) {
        return agencyAnnouncementNumberRepository.findByAgencyIdAndAnnouncementNumber(agencyId, announcementNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No existe ese anuncio para esta agencia"));
    }

    private AnnouncementQuotaResponse toResponse(AnnouncementQuota quota, ControlRecord controlRecord) {
        AgencyAnnouncementNumber numbering = quota.getAgencyAnnouncementNumber();
        BigDecimal delivered = deliveredKg(numbering, controlRecord);
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

    /** Las compras guardan el numero de display del anuncio ("PREFIJO-numero"), no su id. */
    private static String displayNumber(ControlRecord controlRecord, AgencyAnnouncementNumber numbering) {
        return controlRecord.getPrefix() + "-" + numbering.getAnnouncementNumber();
    }

    /** RN-21.1.6: kilos netos de las compras con ese anuncio; las anuladas ya estan en 0. */
    private BigDecimal deliveredKg(AgencyAnnouncementNumber numbering, ControlRecord controlRecord) {
        return dryCoffeePurchaseRepository.sumNetKgByAgencyAndAnnouncementNumber(
                numbering.getAgency().getId(), displayNumber(controlRecord, numbering));
    }
}
