package com.cafeoccidente.backend.purchases.future.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseResponse;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import com.cafeoccidente.backend.purchases.future.repository.FuturePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseService;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Compras a Futuro" (Form_COMPRAS A FUTURO.bas): registra el compromiso de entrega, sin cascada
 * de precio ni factura (ver FuturePurchase). "Fondo" fijo en RP: el formulario real no tiene
 * Cuadro combinado37 (Fondo) - el anuncio vigente se busca directo por agencia+Especial.
 */
@Service
public class FuturePurchaseServiceImpl implements FuturePurchaseService {

    private static final String FUND_CODE = "RP";

    private final FuturePurchaseRepository futurePurchaseRepository;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final AnnouncementService announcementService;
    private final GrowerService growerService;
    private final SecurityUtils securityUtils;

    public FuturePurchaseServiceImpl(
            FuturePurchaseRepository futurePurchaseRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            AnnouncementService announcementService,
            GrowerService growerService,
            SecurityUtils securityUtils) {
        this.futurePurchaseRepository = futurePurchaseRepository;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.announcementService = announcementService;
        this.growerService = growerService;
        this.securityUtils = securityUtils;
    }

    @Override
    @Transactional
    public FuturePurchaseResponse create(FuturePurchaseRequest request) {
        Agency agency = agencyRepository.findById(request.agencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        GrowerResponse grower = growerService.findByIdNumber(request.idNumber());

        // Texto34_AfterUpdate: "NO LE PUEDE ANUNCIAR A UN FALLECIDO".
        if ("F".equalsIgnoreCase(grower.growerType())) {
            throw new BusinessRuleException("No se le puede anunciar una entrega futura a un caficultor fallecido");
        }
        // Texto34_AfterUpdate: bloqueo de pertenencia a programa (Id_NessOcci) - ver GrowerService.
        growerService.requireProgramMembership(request.idNumber(), request.specialType());

        Fund fund = fundRepository.findByCode(FUND_CODE)
                .orElseThrow(() -> new ResourceNotFoundException("Fondo RP no encontrado"));
        AnnouncementResponse announcement =
                announcementService.findLatest(agency.getId(), fund.getId(), request.specialType());

        FuturePurchase purchase = new FuturePurchase();
        purchase.setCreatedAt(Instant.now());
        purchase.setAgency(agency);
        purchase.setAnnouncementNumber(announcement.announcementNumber());
        purchase.setAnnouncementDate(announcement.announcementDate());
        purchase.setIdNumber(grower.idNumber());
        purchase.setFirstName(grower.firstName());
        purchase.setLastName(grower.lastName());
        purchase.setGrowerType(grower.growerType());
        purchase.setAddress(grower.address());
        purchase.setSpecialType(request.specialType());
        purchase.setAnnouncedKg(request.announcedKg());
        // Kilos_LostFocus: SaldoKilos = Kilos al momento de anunciar (sin entregas parciales aun).
        purchase.setRemainingKg(request.announcedKg());
        purchase.setDeliveryDate(request.deliveryDate());
        purchase.setFinca(request.finca());
        purchase.setMunicipality(request.municipality());
        purchase.setVereda(request.vereda());
        purchase.setHealthyUnitPrice(announcement.healthyUnitPrice());
        purchase.setDefectiveUnitPrice(announcement.defectiveUnitPrice());
        purchase.setBonus(announcement.bonus());
        purchase.setCosts(announcement.costs());
        purchase.setQualityIncrement(
                announcement.qualityIncrement() == null ? BigDecimal.ZERO : announcement.qualityIncrement());
        // prbcps: Pr_Base_CPS crudo, NO el Pr_Base_PC neto que usan Seco/Verde/Otros - ver entidad.
        purchase.setBasePriceLoad(announcement.basePriceLoad());
        purchase.setCreatedByUserId(securityUtils.getCurrentUserId());

        return toResponse(futurePurchaseRepository.save(purchase));
    }

    @Override
    public FuturePurchaseResponse findById(Long id) {
        FuturePurchase purchase = futurePurchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compromiso de compra a futuro no encontrado"));
        return toResponse(purchase);
    }

    private FuturePurchaseResponse toResponse(FuturePurchase purchase) {
        return new FuturePurchaseResponse(
                purchase.getId(),
                purchase.getAgency().getId(),
                purchase.getAgency().getName(),
                purchase.getAnnouncementNumber(),
                purchase.getAnnouncementDate(),
                purchase.getIdNumber(),
                purchase.getFirstName(),
                purchase.getLastName(),
                purchase.getGrowerType(),
                purchase.getAddress(),
                purchase.getSpecialType(),
                purchase.getAnnouncedKg(),
                purchase.getRemainingKg(),
                purchase.getDeliveryDate(),
                purchase.getFinca(),
                purchase.getMunicipality(),
                purchase.getVereda(),
                purchase.getHealthyUnitPrice(),
                purchase.getDefectiveUnitPrice(),
                purchase.getBonus(),
                purchase.getCosts(),
                purchase.getQualityIncrement(),
                purchase.getBasePriceLoad());
    }
}
