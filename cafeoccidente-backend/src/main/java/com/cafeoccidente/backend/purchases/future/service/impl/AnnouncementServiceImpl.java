package com.cafeoccidente.backend.purchases.future.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.repository.ControlRecordRepository;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.HuskAnnouncementRequest;
import com.cafeoccidente.backend.purchases.future.entity.AgencyAnnouncementNumber;
import com.cafeoccidente.backend.purchases.future.entity.Announcement;
import com.cafeoccidente.backend.purchases.future.repository.AgencyAnnouncementNumberRepository;
import com.cafeoccidente.backend.purchases.future.repository.AnnouncementRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anuncios compartidos entre agencias (ver docs/diseno-anuncios-compartidos.md, opcion A). El admin
 * publica UN anuncio maestro (precio crudo: Pr_Base_CPS/Pr_AlmDefec/SobrePr_CPS); create() hace
 * fan-out y le asigna a cada agencia con ControlRecord activo su propio numero (AgencyAnnouncementNumber).
 * findLatest() calcula Costos/Pr Sustentacion/Bonificacion en el momento de la compra con el
 * ControlRecord VIVO de la agencia que pregunta - nunca quedan congelados en el anuncio.
 */
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    /** Fondo/Especial fijos de "Actualizar Anuncio Pasilla" (Cuadro_combinado37/61, PASILLA.txt). */
    private static final String HUSK_FUND_CODE = "RP";

    private static final String HUSK_SPECIAL_TYPE = "PASILLA";

    private final AnnouncementRepository announcementRepository;
    private final AgencyAnnouncementNumberRepository agencyAnnouncementNumberRepository;
    private final FundRepository fundRepository;
    private final ControlRecordRepository controlRecordRepository;
    private final ControlRecordService controlRecordService;
    private final SecurityUtils securityUtils;

    public AnnouncementServiceImpl(
            AnnouncementRepository announcementRepository,
            AgencyAnnouncementNumberRepository agencyAnnouncementNumberRepository,
            FundRepository fundRepository,
            ControlRecordRepository controlRecordRepository,
            ControlRecordService controlRecordService,
            SecurityUtils securityUtils) {
        this.announcementRepository = announcementRepository;
        this.agencyAnnouncementNumberRepository = agencyAnnouncementNumberRepository;
        this.fundRepository = fundRepository;
        this.controlRecordRepository = controlRecordRepository;
        this.controlRecordService = controlRecordService;
        this.securityUtils = securityUtils;
    }

    @Override
    public AnnouncementResponse findLatest(Long agencyId, Long fundId, String specialType) {
        AgencyAnnouncementNumber numbering = agencyAnnouncementNumberRepository
                .findLatest(agencyId, fundId, specialType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay un anuncio de precio vigente para esa agencia/fondo/especial"));
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        return toResponse(numbering, controlRecord);
    }

    @Override
    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request) {
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));

        Announcement master = new Announcement();
        master.setAnnouncementDate(LocalDate.now());
        master.setBasePriceLoad(request.basePriceLoad());
        master.setDefectiveUnitPrice(request.defectiveUnitPrice());
        master.setSpecialSurcharge(request.specialSurcharge());
        master.setQualityIncrement(request.qualityIncrement());
        master.setFund(fund);
        master.setSpecialType(request.specialType());
        master.setActive(true);
        return publish(master, fund);
    }

    /**
     * "Actualizar Anuncio Pasilla": Form_ANUNCIOS PASILLA.bas solo tiene Pr_Base_CPS y Pr_AlmSana
     * ("Pr Punto") como campos reales - a diferencia de CORRF, nunca tuvo SobrePr_CPS ni un
     * Pr_AlmDefec funcional (el control con ese nombre en PASILLA.txt esta apuntando a
     * NumRegistro). defectiveUnitPrice queda en cero porque ninguna compra de pasilla lo lee
     * (ver HuskPurchaseServiceImpl/HuskPurchaseCalculator).
     */
    @Override
    @Transactional
    public AnnouncementResponse createHusk(HuskAnnouncementRequest request) {
        Fund fund = fundRepository.findByCode(HUSK_FUND_CODE)
                .orElseThrow(() -> new ResourceNotFoundException("Fondo RP no encontrado"));

        Announcement master = new Announcement();
        master.setAnnouncementDate(LocalDate.now());
        master.setBasePriceLoad(request.basePriceLoad());
        master.setPointPrice(request.pointPrice());
        master.setDefectiveUnitPrice(BigDecimal.ZERO);
        master.setFund(fund);
        master.setSpecialType(HUSK_SPECIAL_TYPE);
        master.setActive(true);
        return publish(master, fund);
    }

    private AnnouncementResponse publish(Announcement master, Fund fund) {
        announcementRepository.save(master);

        Long callerAgencyId = securityUtils.getCurrentAgencyId();
        AgencyAnnouncementNumber callerNumbering = null;
        ControlRecord callerControlRecord = null;

        // Fan-out: cada agencia con ControlRecord activo recibe su propia fila de numeracion para
        // este mismo anuncio maestro, con su propio consecutivo (igual que ya pasa con factura).
        for (ControlRecord controlRecord : controlRecordRepository.findByActiveTrue()) {
            Integer maxNumber = agencyAnnouncementNumberRepository.findMaxNumber(controlRecord.getAgency().getId());
            int nextNumber = maxNumber == null ? 1 : maxNumber + 1;

            AgencyAnnouncementNumber numbering = new AgencyAnnouncementNumber();
            numbering.setAgency(controlRecord.getAgency());
            numbering.setMasterAnnouncement(master);
            numbering.setAnnouncementNumber(nextNumber);
            numbering.setAssignedAt(master.getAnnouncementDate());
            agencyAnnouncementNumberRepository.save(numbering);

            if (controlRecord.getAgency().getId().equals(callerAgencyId)) {
                callerNumbering = numbering;
                callerControlRecord = controlRecord;
            }
        }

        if (callerNumbering == null) {
            // El admin publico igual (el fan-out de arriba ya corrio para las agencias que si tienen
            // ControlRecord) pero su propia agencia no tiene uno activo, asi que no hay numero propio
            // que mostrarle en la confirmacion todavia.
            return new AnnouncementResponse(
                    master.getId(), null, master.getAnnouncementDate(), master.getBasePriceLoad(),
                    master.getDefectiveUnitPrice(), null, null, null, null, fund.getId(), master.getSpecialType(),
                    master.getQualityIncrement());
        }
        return toResponse(callerNumbering, callerControlRecord);
    }

    @Override
    public List<AnnouncementResponse> history() {
        Long agencyId = securityUtils.getCurrentAgencyId();
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        return agencyAnnouncementNumberRepository.findByAgencyIdOrderByAssignedAtDescIdDesc(agencyId).stream()
                .map(numbering -> toResponse(numbering, controlRecord))
                .toList();
    }

    /**
     * Costos = ControlRecord.costs vivo de la agencia (nunca congelado). Pr Sustentacion/Bonificacion
     * se derivan del precio crudo del maestro con el BaseCarga vivo de esa misma agencia (formulas
     * reales, Form_ANUNCIOS CORRF.bas: Pr_Sustentacion = Pr_Base_CPS / BaseCarga - Costos;
     * Bonificacion = SobrePr_CPS / BaseCarga) - antes se calculaban una sola vez al crear el anuncio
     * con el ControlRecord de quien lo publicaba; ahora se recalculan en cada consulta con el
     * ControlRecord de quien pregunta. Para PASILLA no hay formula que derivar (Form_ANUNCIOS
     * PASILLA.bas nunca tuvo Texto17_AfterUpdate): master.pointPrice ya trae el "Pr Punto" que
     * digito el admin, se usa tal cual.
     */
    private AnnouncementResponse toResponse(AgencyAnnouncementNumber numbering, ControlRecord controlRecord) {
        Announcement master = numbering.getMasterAnnouncement();
        BigDecimal baseLoad = BigDecimal.valueOf(controlRecord.getBaseLoad());
        BigDecimal healthyUnitPrice = master.getPointPrice() != null
                ? master.getPointPrice()
                : master.getBasePriceLoad().divide(baseLoad, 2, RoundingMode.HALF_UP).subtract(controlRecord.getCosts());
        BigDecimal bonus = master.getSpecialSurcharge() == null
                ? BigDecimal.ZERO
                : master.getSpecialSurcharge().divide(baseLoad, 2, RoundingMode.HALF_UP);
        String displayNumber = controlRecord.getPrefix() + "-" + numbering.getAnnouncementNumber();

        return new AnnouncementResponse(
                master.getId(),
                displayNumber,
                numbering.getAssignedAt(),
                master.getBasePriceLoad(),
                master.getDefectiveUnitPrice(),
                healthyUnitPrice,
                bonus,
                controlRecord.getCosts(),
                numbering.getAgency().getId(),
                master.getFund().getId(),
                master.getSpecialType(),
                master.getQualityIncrement());
    }
}
