package com.cafeoccidente.backend.purchases.future.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseResponse;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import com.cafeoccidente.backend.purchases.future.repository.FuturePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.future.service.DeliveryRule;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseService;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Ingresar Compras a Futuro" (RF-21.4): registra el compromiso de entrega, sin cascada de precio ni factura
 * (ver FuturePurchase). "Fondo" fijo en RP: el formulario de Access no tiene Fondo. El anuncio lo elige el
 * usuario entre los de hoy del especial (RN-21.4.3); Access tomaba el ultimo, sin mirar la fecha.
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
    private final CurrentAgencyProvider currentAgencyProvider;
    private final Clock clock;

    public FuturePurchaseServiceImpl(
            FuturePurchaseRepository futurePurchaseRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            AnnouncementService announcementService,
            GrowerService growerService,
            SecurityUtils securityUtils, CurrentAgencyProvider currentAgencyProvider, Clock clock) {
        this.futurePurchaseRepository = futurePurchaseRepository;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.announcementService = announcementService;
        this.growerService = growerService;
        this.securityUtils = securityUtils;
        this.currentAgencyProvider = currentAgencyProvider;
        this.clock = clock;
    }

    @Override
    public List<AnnouncementResponse> todayAnnouncements(String specialType) {
        Long fundId = rpFund().getId();
        LocalDate today = LocalDate.now(clock);
        return announcementService.history().stream()
                .filter(a -> a.fundId().equals(fundId) && a.specialType().equals(specialType))
                .filter(a -> today.equals(a.announcementDate()))
                .toList();
    }

    @Override
    @Transactional
    public FuturePurchaseResponse create(FuturePurchaseRequest request) {
        Agency agency = agencyRepository.findById(currentAgencyProvider.currentAgencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        GrowerResponse grower = growerService.findByIdNumber(request.idNumber());

        // Texto34_AfterUpdate: "NO LE PUEDE ANUNCIAR A UN FALLECIDO".
        if ("F".equalsIgnoreCase(grower.growerType())) {
            throw new BusinessRuleException("No se le puede anunciar una entrega futura a un caficultor fallecido");
        }
        // Texto34_AfterUpdate: bloqueo de pertenencia a programa (Id_NessOcci) - ver GrowerService.
        growerService.requireProgramMembership(request.idNumber(), request.specialType());

        AnnouncementResponse announcement = announcementService.findForPurchase(
                agency.getId(), rpFund().getId(), request.specialType(), request.announcementNumber());
        // RN-21.4.3: solo con un anuncio del dia; al dia siguiente la lista ya es otra.
        if (!LocalDate.now(clock).equals(announcement.announcementDate())) {
            throw new BusinessRuleException("El anuncio " + request.announcementNumber() + " no es de hoy");
        }

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

    private Fund rpFund() {
        return fundRepository.findByCode(FUND_CODE)
                .orElseThrow(() -> new ResourceNotFoundException("Fondo RP no encontrado"));
    }

    @Override
    public FuturePurchaseResponse findById(Long id) {
        // Solo de la agencia de trabajo (RF-05.1): la de otra agencia es "no encontrada", como Consultar Compras.
        FuturePurchase purchase = futurePurchaseRepository.findById(id)
                .filter(p -> p.getAgency().getId().equals(currentAgencyProvider.currentAgencyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Compromiso de compra a futuro no encontrado"));
        return toResponse(purchase);
    }

    @Override
    public List<FuturePurchaseResponse> openFor(String idNumber) {
        return futurePurchaseRepository.findByAgencyIdAndIdNumberAndRemainingKgGreaterThanOrderByIdAsc(
                currentAgencyProvider.currentAgencyId(), idNumber, BigDecimal.ZERO).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public FuturePurchase forBilling(Long id, String idNumber, boolean lock) {
        FuturePurchase purchase = (lock ? futurePurchaseRepository.lockById(id) : futurePurchaseRepository.findById(id))
                .filter(p -> p.getAgency().getId().equals(currentAgencyProvider.currentAgencyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Compra anunciada no encontrada"));
        if (idNumber != null && !purchase.getIdNumber().equals(idNumber)) {
            throw new BusinessRuleException("La cédula no es la de la compra anunciada");
        }
        return purchase;
    }

    @Override
    public AnnouncementResponse billingAnnouncement(FuturePurchase purchase, Long fundId) {
        return new AnnouncementResponse(
                null,
                purchase.getAnnouncementNumber(),
                purchase.getAnnouncementDate(),
                purchase.getBasePriceLoad(),
                purchase.getDefectiveUnitPrice(),
                purchase.getHealthyUnitPrice(),
                purchase.getBonus(),
                purchase.getCosts(),
                purchase.getAgency().getId(),
                fundId,
                purchase.getSpecialType(),
                purchase.getQualityIncrement());
    }

    /** MANDATORY: solo dentro de la transaccion que guarda la compra (con la fila ya bloqueada). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deliver(FuturePurchase purchase, BigDecimal deliveryKg, BigDecimal netKg) {
        DeliveryRule.check(purchase.getRemainingKg(), deliveryKg, netKg, "de la compra anunciada");
        purchase.setRemainingKg(purchase.getRemainingKg().subtract(netKg));
        // Escrito ya: el SQL que siga en esta transaccion (p. ej. la anulacion) ve el saldo nuevo.
        futurePurchaseRepository.saveAndFlush(purchase);
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
