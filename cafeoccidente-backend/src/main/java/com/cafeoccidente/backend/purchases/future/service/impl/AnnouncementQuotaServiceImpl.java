package com.cafeoccidente.backend.purchases.future.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaBalanceResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaCandidateResponse;
import com.cafeoccidente.backend.purchases.future.entity.AgencyAnnouncementNumber;
import com.cafeoccidente.backend.purchases.future.entity.AnnouncementQuota;
import com.cafeoccidente.backend.purchases.future.repository.AgencyAnnouncementNumberRepository;
import com.cafeoccidente.backend.purchases.future.repository.AnnouncementQuotaRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.future.service.DeliveryRule;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    // ponytail: recorre toda la historia de la agencia; filtrar en la consulta si se vuelve lenta.
    @Override
    public List<QuotaCandidateResponse> available(Long agencyId) {
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        Set<String> withQuota = quotasByDisplayNumber(agencyId, controlRecord).keySet();
        Map<String, BigDecimal> delivered = deliveredByAnnouncement(agencyId);
        Map<String, BigDecimal> quotaKg = quotaKgByDisplayNumber(agencyId);
        // C6: solo el ultimo anuncio de cada fondo y especial (la historia viene del mas nuevo al mas viejo).
        Set<String> seenTypes = new HashSet<>();
        return announcementService.history().stream()
                .filter(a -> seenTypes.add(a.fundId() + "|" + a.specialType()))
                .filter(a -> !SPECIALS_WITHOUT_QUOTA.contains(a.specialType()))
                .filter(a -> !withQuota.contains(a.announcementNumber()))
                .map(a -> new QuotaCandidateResponse(a, delivered.getOrDefault(key(a), BigDecimal.ZERO),
                        quotaKg.getOrDefault(a.announcementNumber(), BigDecimal.ZERO)))
                .toList();
    }

    @Override
    public List<QuotaBalanceResponse> withBalance(Long agencyId, Long fundId, String specialType) {
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        Map<String, BigDecimal> quotas = quotasByDisplayNumber(agencyId, controlRecord);
        Map<String, BigDecimal> delivered = deliveredByAnnouncement(agencyId);
        Map<String, BigDecimal> quotaKg = quotaKgByDisplayNumber(agencyId);
        return announcementService.history().stream()
                .filter(a -> a.fundId().equals(fundId) && a.specialType().equals(specialType))
                .filter(a -> quotas.containsKey(a.announcementNumber()))
                .map(a -> {
                    BigDecimal quota = quotas.get(a.announcementNumber());
                    BigDecimal spent = quotaKg.getOrDefault(a.announcementNumber(), BigDecimal.ZERO);
                    return new QuotaBalanceResponse(a, quota, delivered.getOrDefault(key(a), BigDecimal.ZERO), spent,
                            quota.subtract(spent));
                })
                .filter(q -> q.balance().signum() > 0)
                .toList();
    }

    /** MANDATORY: el bloqueo solo sirve dentro de la transaccion que guarda la compra. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long agencyId, String displayNumber, BigDecimal netKg, BigDecimal deliveryKg) {
        Integer number = Integer.valueOf(displayNumber.substring(displayNumber.lastIndexOf('-') + 1));
        AnnouncementQuota quota = announcementQuotaRepository.lockByAgencyAndNumber(agencyId, number).orElse(null);
        if (quota == null) {
            if (deliveryKg != null) {
                throw new BusinessRuleException("El anuncio " + number + " no tiene cupo asignado");
            }
            return;
        }
        BigDecimal balance = quota.getAssignedQuota().subtract(
                dryCoffeePurchaseRepository.sumQuotaNetKgByAgencyAndAnnouncementNumber(agencyId, displayNumber));
        DeliveryRule.check(balance, deliveryKg, netKg, "del cupo del anuncio " + number);
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
        BigDecimal spent = quotaKg(numbering, controlRecord);
        if (request.assignedQuota().compareTo(spent) < 0) {
            throw new BusinessRuleException(
                    "El cupo no puede ser menor que los kilos con cupo (" + spent.toPlainString() + ")");
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
        BigDecimal spent = quotaKg(numbering, controlRecord);
        return new AnnouncementQuotaResponse(
                quota.getId(),
                numbering.getAgency().getId(),
                numbering.getAnnouncementNumber(),
                numbering.getAssignedAt(),
                numbering.getMasterAnnouncement().getSpecialType(),
                quota.getAssignedQuota(),
                dryCoffeePurchaseRepository.sumValidNetKgByAgencyAndAnnouncementNumber(numbering.getAgency().getId(),
                        displayNumber(controlRecord, numbering), numbering.getMasterAnnouncement().getSpecialType()),
                spent,
                quota.getAssignedQuota().subtract(spent),
                displayNumber(controlRecord, numbering));
    }

    private Map<String, BigDecimal> quotasByDisplayNumber(Long agencyId, ControlRecord controlRecord) {
        return announcementQuotaRepository.findByAgencyAnnouncementNumberAgencyId(agencyId).stream()
                .collect(Collectors.toMap(
                        quota -> displayNumber(controlRecord, quota.getAgencyAnnouncementNumber()),
                        AnnouncementQuota::getAssignedQuota));
    }

    /** Kilos con Cupo por numero de display: solo las compras de Facturar Anuncios con Cupos. */
    private Map<String, BigDecimal> quotaKgByDisplayNumber(Long agencyId) {
        return dryCoffeePurchaseRepository.sumQuotaNetKgByAnnouncementNumber(agencyId).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (BigDecimal) row[1]));
    }

    /** Kilos Entregados por anuncio y especial (key): todas las compras validas, normales y de cupo. */
    private Map<String, BigDecimal> deliveredByAnnouncement(Long agencyId) {
        return dryCoffeePurchaseRepository.sumValidNetKgByAnnouncementNumber(agencyId).stream()
                .collect(Collectors.toMap(row -> row[0] + "|" + row[1], row -> (BigDecimal) row[2]));
    }

    private static String key(AnnouncementResponse announcement) {
        return announcement.announcementNumber() + "|" + announcement.specialType();
    }

    /** Las compras guardan el numero de display del anuncio ("PREFIJO-numero"), no su id. */
    private static String displayNumber(ControlRecord controlRecord, AgencyAnnouncementNumber numbering) {
        return controlRecord.getPrefix() + "-" + numbering.getAnnouncementNumber();
    }

    /** Kilos con Cupo: compras de cupo con ese anuncio (regla del area, 18-nov); las anuladas ya estan en 0. */
    private BigDecimal quotaKg(AgencyAnnouncementNumber numbering, ControlRecord controlRecord) {
        return dryCoffeePurchaseRepository.sumQuotaNetKgByAgencyAndAnnouncementNumber(
                numbering.getAgency().getId(), displayNumber(controlRecord, numbering));
    }
}
