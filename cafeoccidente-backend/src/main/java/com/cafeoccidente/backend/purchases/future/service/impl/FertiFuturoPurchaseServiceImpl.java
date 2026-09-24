package com.cafeoccidente.backend.purchases.future.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseResponse;
import com.cafeoccidente.backend.purchases.future.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.future.entity.FertiFuturoPurchase;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import com.cafeoccidente.backend.purchases.future.repository.FertiFuturoMonthlyTotals;
import com.cafeoccidente.backend.purchases.future.repository.FertiFuturoPurchaseRepository;
import com.cafeoccidente.backend.purchases.future.repository.FuturePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.future.service.FertiFuturoPurchaseCalculation;
import com.cafeoccidente.backend.purchases.future.service.FertiFuturoPurchaseCalculator;
import com.cafeoccidente.backend.purchases.future.service.FertiFuturoPurchaseService;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseInvoiceNumberService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FERTIFUTURO (Form_FERTIFUTURO.bas): liquida lo entregado, con cascada de precio completa y
 * factura propia (comparte el pool de numeracion por agencia, ver PurchaseInvoiceNumberService).
 * Si viene ligado a un compromiso de Compras a Futuro, descuenta la entrega del saldo pendiente
 * (Form_FUTURE FERTIFUTURO.bas, Texto29_AfterUpdate) - sin el sub-sistema de "obligacion", fuera
 * de esta fase.
 */
@Service
public class FertiFuturoPurchaseServiceImpl implements FertiFuturoPurchaseService {

    private final FertiFuturoPurchaseRepository fertiFuturoPurchaseRepository;
    private final FuturePurchaseRepository futurePurchaseRepository;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final ProductCodeResolver productCodeResolver;
    private final AnnouncementService announcementService;
    private final ControlRecordService controlRecordService;
    private final FertiFuturoPurchaseCalculator calculator;
    private final PurchaseInvoiceNumberService purchaseInvoiceNumberService;
    private final SecurityUtils securityUtils;
    private final InventoryMovementService inventoryMovementService;

    public FertiFuturoPurchaseServiceImpl(
            FertiFuturoPurchaseRepository fertiFuturoPurchaseRepository,
            FuturePurchaseRepository futurePurchaseRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            FertiFuturoPurchaseCalculator calculator,
            PurchaseInvoiceNumberService purchaseInvoiceNumberService,
            SecurityUtils securityUtils,
            InventoryMovementService inventoryMovementService) {
        this.fertiFuturoPurchaseRepository = fertiFuturoPurchaseRepository;
        this.futurePurchaseRepository = futurePurchaseRepository;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.productCodeResolver = productCodeResolver;
        this.announcementService = announcementService;
        this.controlRecordService = controlRecordService;
        this.calculator = calculator;
        this.purchaseInvoiceNumberService = purchaseInvoiceNumberService;
        this.securityUtils = securityUtils;
        this.inventoryMovementService = inventoryMovementService;
    }

    @Override
    @Transactional
    public FertiFuturoPurchaseResponse create(FertiFuturoPurchaseRequest request) {
        Agency agency = agencyRepository.findById(request.agencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
        ProductCode productCode = productCodeResolver.resolve(request.specialType(), fund.getId());
        AnnouncementResponse announcement =
                announcementService.findLatest(agency.getId(), fund.getId(), request.specialType());
        BigDecimal qualityIncrementRate =
                announcement.qualityIncrement() == null ? BigDecimal.ZERO : announcement.qualityIncrement();

        FuturePurchase futurePurchase = requireFuturePurchaseWithBalance(request);

        FertiFuturoPurchaseCalculation calculation = runCalculation(request, announcement, qualityIncrementRate);

        FertiFuturoPurchase purchase = new FertiFuturoPurchase();
        purchase.setPurchaseDate(LocalDate.now());
        purchase.setInvoiceNumber(request.invoiceNumber());
        purchase.setAgency(agency);
        purchase.setFund(fund);
        purchase.setSpecialType(request.specialType());
        purchase.setProductCode(productCode);
        purchase.setAnnouncementNumber(announcement.announcementNumber());
        purchase.setAnnouncementDate(announcement.announcementDate());
        purchase.setFuturePurchase(futurePurchase);
        purchase.setIdNumber(request.idNumber());
        purchase.setFirstName(request.firstName());
        purchase.setLastName(request.lastName());
        purchase.setGrowerType(request.growerType());
        purchase.setAddress(request.address());
        purchase.setSacos(request.sacos());
        purchase.setNetKg(request.netKg());
        purchase.setGrossKg(request.grossKg());
        purchase.setTareKg(calculation.tareKg());
        purchase.setHealthyStoredWeight(request.healthyStoredWeight());
        purchase.setHealthyPercentage(calculation.healthyPercentage());
        purchase.setDefectiveStoredWeight(request.defectiveStoredWeight());
        purchase.setDefectivePercentage(calculation.defectivePercentage());
        purchase.setHealthyUnitPrice(announcement.healthyUnitPrice());
        purchase.setDefectiveUnitPrice(announcement.defectiveUnitPrice());
        purchase.setBonus(announcement.bonus());
        purchase.setPenalty(request.penalty());
        purchase.setCosts(announcement.costs());
        purchase.setQualityIncrementRate(qualityIncrementRate);
        purchase.setQualityIncrementAmount(calculation.qualityIncrementAmount());
        purchase.setUnitPrice(calculation.unitPrice());
        purchase.setGrossValue(calculation.grossValue());
        purchase.setInventoryValue(calculation.inventoryValue());
        purchase.setAssociateContribution(calculation.associateContribution());
        purchase.setCooperativeDiscount(calculation.cooperativeDiscount());
        purchase.setWithholdingExempt(request.withholdingExempt());
        purchase.setWithholding(calculation.withholding());
        purchase.setFreightDiscount(request.freightDiscount());
        purchase.setOtherDiscounts(request.otherDiscounts());
        purchase.setNetToPay(calculation.netToPay());
        purchase.setPaymentMethod(request.paymentMethod());
        purchase.setCheckNumber(request.checkNumber());
        purchase.setCreatedByUserId(securityUtils.getCurrentUserId());
        purchase.setCreatedAt(Instant.now());

        FertiFuturoPurchase saved = fertiFuturoPurchaseRepository.save(purchase);

        if (futurePurchase != null) {
            futurePurchase.setRemainingKg(futurePurchase.getRemainingKg().subtract(request.netKg()));
            futurePurchaseRepository.save(futurePurchase);
        }

        inventoryMovementService.recordFromPurchaseSafely(
                InventoryMovement.PurchaseModule.FERTI_FUTURO, saved.getId(),
                agency.getId(), productCode.getId(), purchase.getSpecialType(), purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(), purchase.getSacos(), purchase.getGrossKg(), purchase.getNetKg(),
                purchase.getHealthyPercentage(), purchase.getInventoryValue());

        ControlRecord controlRecord = controlRecordService.getActive(agency.getId());
        return toResponse(saved, controlRecord);
    }

    @Override
    public FertiFuturoPurchaseCalculation preview(FertiFuturoPurchaseRequest request) {
        Agency agency = agencyRepository.findById(request.agencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
        AnnouncementResponse announcement =
                announcementService.findLatest(agency.getId(), fund.getId(), request.specialType());
        BigDecimal qualityIncrementRate =
                announcement.qualityIncrement() == null ? BigDecimal.ZERO : announcement.qualityIncrement();

        requireFuturePurchaseWithBalance(request);

        return runCalculation(request, announcement, qualityIncrementRate);
    }

    /** Texto29_AfterUpdate: si viene ligado a un compromiso, valida el saldo antes de calcular. */
    private FuturePurchase requireFuturePurchaseWithBalance(FertiFuturoPurchaseRequest request) {
        if (request.futurePurchaseId() == null) {
            return null;
        }
        FuturePurchase futurePurchase = futurePurchaseRepository.findById(request.futurePurchaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Compromiso de compra a futuro no encontrado"));
        if (request.netKg().compareTo(futurePurchase.getRemainingKg()) > 0) {
            throw new BusinessRuleException(
                    "La entrega no debe ser superior al saldo pendiente del compromiso (saldo: "
                            + futurePurchase.getRemainingKg() + " kg)");
        }
        return futurePurchase;
    }

    private FertiFuturoPurchaseCalculation runCalculation(
            FertiFuturoPurchaseRequest request, AnnouncementResponse announcement, BigDecimal qualityIncrementRate) {
        YearMonth currentMonth = YearMonth.now();
        FertiFuturoMonthlyTotals monthlyTotals = fertiFuturoPurchaseRepository.sumMonthlyTotalsByIdNumber(
                request.idNumber(), currentMonth.atDay(1), currentMonth.atEndOfMonth());

        return calculator.calculate(
                request,
                announcement.healthyUnitPrice(),
                announcement.defectiveUnitPrice(),
                announcement.bonus(),
                announcement.costs(),
                qualityIncrementRate,
                monthlyTotals.grossValue(),
                monthlyTotals.withholding());
    }

    @Override
    public FertiFuturoPurchaseResponse findById(Long id) {
        FertiFuturoPurchase purchase = fertiFuturoPurchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Liquidacion de Fertifuturo no encontrada"));
        ControlRecord controlRecord = controlRecordService.getActive(purchase.getAgency().getId());
        return toResponse(purchase, controlRecord);
    }

    @Override
    public NextInvoiceNumberResponse nextInvoiceNumber() {
        Long agencyId = securityUtils.getCurrentAgencyId();
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        Integer maxUsed = purchaseInvoiceNumberService.findMaxUsed(agencyId);
        int next = maxUsed == null ? controlRecord.getResolutionFrom() : maxUsed + 1;
        if (next > controlRecord.getResolutionTo()) {
            throw new BusinessRuleException(
                    "Se agotó el rango de facturas autorizado por la resolución DIAN vigente");
        }

        String warning = null;
        LocalDate expiration = controlRecord.getResolutionDate().plusMonths(controlRecord.getValidity());
        if (LocalDate.now().isAfter(expiration)) {
            warning = "La resolución de facturación está vencida";
        } else if (controlRecord.getResolutionTo() - next < 100) {
            warning = "La resolución de facturación está a punto de agotarse";
        }

        return new NextInvoiceNumberResponse(next, controlRecord.getPrefix(), warning);
    }

    private FertiFuturoPurchaseResponse toResponse(FertiFuturoPurchase purchase, ControlRecord controlRecord) {
        return new FertiFuturoPurchaseResponse(
                purchase.getId(),
                purchase.getPurchaseDate(),
                purchase.getInvoiceNumber(),
                purchase.getAgency().getId(),
                purchase.getAgency().getName(),
                purchase.getFund().getId(),
                purchase.getFund().getCode(),
                purchase.getSpecialType(),
                purchase.getProductCode().getCode(),
                purchase.getAnnouncementNumber(),
                purchase.getAnnouncementDate(),
                purchase.getFuturePurchase() == null ? null : purchase.getFuturePurchase().getId(),
                purchase.getIdNumber(),
                purchase.getFirstName(),
                purchase.getLastName(),
                purchase.getGrowerType(),
                purchase.getAddress(),
                purchase.getSacos(),
                purchase.getNetKg(),
                purchase.getGrossKg(),
                purchase.getTareKg(),
                purchase.getHealthyStoredWeight(),
                purchase.getHealthyPercentage(),
                purchase.getDefectiveStoredWeight(),
                purchase.getDefectivePercentage(),
                purchase.getHealthyUnitPrice(),
                purchase.getDefectiveUnitPrice(),
                purchase.getBonus(),
                purchase.getPenalty(),
                purchase.getCosts(),
                purchase.getQualityIncrementRate(),
                purchase.getQualityIncrementAmount(),
                purchase.getUnitPrice(),
                purchase.getGrossValue(),
                purchase.getInventoryValue(),
                purchase.getAssociateContribution(),
                purchase.getCooperativeDiscount(),
                purchase.isWithholdingExempt(),
                purchase.getWithholding(),
                purchase.getFreightDiscount(),
                purchase.getOtherDiscounts(),
                purchase.getNetToPay(),
                purchase.getPaymentMethod(),
                purchase.getCheckNumber(),
                purchase.getCreatedByUserId(),
                controlRecord.getPurchasePoint(),
                controlRecord.getPrefix(),
                controlRecord.getDianResolution(),
                controlRecord.getResolutionDate(),
                controlRecord.getResolutionFrom(),
                controlRecord.getResolutionTo(),
                controlRecord.getValidity());
    }
}
