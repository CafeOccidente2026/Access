package com.cafeoccidente.backend.purchases.othercoffee.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.othercoffee.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.othercoffee.dto.QualityPercentagesResponse;
import com.cafeoccidente.backend.purchases.othercoffee.dto.SpecialInfoResponse;
import com.cafeoccidente.backend.purchases.othercoffee.entity.OtherCoffeePurchase;
import com.cafeoccidente.backend.purchases.othercoffee.mapper.OtherCoffeePurchaseMapper;
import com.cafeoccidente.backend.purchases.othercoffee.repository.MonthlyGrowerTotals;
import com.cafeoccidente.backend.purchases.othercoffee.repository.OtherCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.othercoffee.service.OtherCoffeePurchaseCalculation;
import com.cafeoccidente.backend.purchases.othercoffee.service.OtherCoffeePurchaseCalculator;
import com.cafeoccidente.backend.purchases.othercoffee.service.OtherCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseInvoiceNumberService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtherCoffeePurchaseServiceImpl implements OtherCoffeePurchaseService {

    private final OtherCoffeePurchaseRepository otherCoffeePurchaseRepository;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final ProductCodeResolver productCodeResolver;
    private final AnnouncementService announcementService;
    private final ControlRecordService controlRecordService;
    private final OtherCoffeePurchaseCalculator calculator;
    private final OtherCoffeePurchaseMapper mapper;
    private final SecurityUtils securityUtils;
    private final GrowerService growerService;
    private final PurchaseInvoiceNumberService purchaseInvoiceNumberService;
    private final InventoryMovementService inventoryMovementService;

    public OtherCoffeePurchaseServiceImpl(
            OtherCoffeePurchaseRepository otherCoffeePurchaseRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            OtherCoffeePurchaseCalculator calculator,
            OtherCoffeePurchaseMapper mapper,
            SecurityUtils securityUtils,
            GrowerService growerService,
            PurchaseInvoiceNumberService purchaseInvoiceNumberService,
            InventoryMovementService inventoryMovementService) {
        this.otherCoffeePurchaseRepository = otherCoffeePurchaseRepository;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.productCodeResolver = productCodeResolver;
        this.announcementService = announcementService;
        this.controlRecordService = controlRecordService;
        this.calculator = calculator;
        this.mapper = mapper;
        this.securityUtils = securityUtils;
        this.growerService = growerService;
        this.purchaseInvoiceNumberService = purchaseInvoiceNumberService;
        this.inventoryMovementService = inventoryMovementService;
    }

    @Override
    @Transactional
    public OtherCoffeePurchaseResponse create(OtherCoffeePurchaseRequest request) {
        Agency agency = agencyRepository.findById(request.agencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
        ProductCode productCode = productCodeResolver.resolve(request.specialType(), fund.getId());
        AnnouncementResponse announcement =
                announcementService.findLatest(agency.getId(), fund.getId(), request.specialType());
        ControlRecord controlRecord = controlRecordService.getActive(agency.getId());
        OtherCoffeePurchaseCalculation calculation = runCalculation(request, announcement, controlRecord);

        Long currentUserId = securityUtils.getCurrentUserId();

        OtherCoffeePurchase purchase = new OtherCoffeePurchase();
        purchase.setPurchaseDate(LocalDate.now());
        purchase.setInvoiceNumber(request.invoiceNumber());
        purchase.setAgency(agency);
        purchase.setFund(fund);
        purchase.setSpecialType(request.specialType());
        purchase.setProductCode(productCode);
        purchase.setAnnouncementNumber(announcement.announcementNumber());
        purchase.setAnnouncementDate(announcement.announcementDate());
        purchase.setBasePriceLoad(calculation.basePriceLoad());
        purchase.setIdNumber(request.idNumber());
        purchase.setFirstName(request.firstName());
        purchase.setLastName(request.lastName());
        purchase.setGrowerType(request.growerType());
        purchase.setAddress(request.address());
        purchase.setCellphone(request.cellphone());
        purchase.setBagsCount(request.bagsCount());
        purchase.setGrossKg(request.grossKg());
        purchase.setTareKg(request.tareKg());
        purchase.setNetKg(calculation.netKg());
        purchase.setTotalStoredWeight(request.totalStoredWeight());
        purchase.setWastePercentage(calculation.wastePercentage());
        purchase.setDefectiveStoredWeight(request.defectiveStoredWeight());
        purchase.setDefectivePercentage(calculation.defectivePercentage());
        purchase.setHealthyStoredWeight(request.healthyStoredWeight());
        purchase.setHealthyPercentage(calculation.healthyPercentage());
        purchase.setHealthyUnitPrice(request.healthyUnitPrice());
        // Pr_AlmDefec se guarda igual que en Cafe Seco (viene del anuncio) aunque el calculador de
        // este modulo no lo use en Vr_Kilo - ver OtherCoffeePurchaseCalculator.
        purchase.setDefectiveUnitPrice(announcement.defectiveUnitPrice());
        purchase.setBonus(request.bonus());
        purchase.setPenalty(request.penalty());
        purchase.setCosts(request.costs());
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
        purchase.setCreatedByUserId(currentUserId);
        purchase.setCreatedAt(Instant.now());

        OtherCoffeePurchase savedPurchase = otherCoffeePurchaseRepository.save(purchase);

        inventoryMovementService.recordFromPurchaseSafely(
                InventoryMovement.PurchaseModule.OTHER_COFFEE, savedPurchase.getId(),
                agency.getId(), productCode.getId(), purchase.getSpecialType(), purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(), purchase.getBagsCount(), purchase.getGrossKg(), purchase.getNetKg(),
                purchase.getHealthyPercentage(), purchase.getInventoryValue());

        return mapper.toResponse(savedPurchase, controlRecord);
    }

    @Override
    public OtherCoffeePurchaseResponse findById(Long id) {
        OtherCoffeePurchase purchase = otherCoffeePurchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compra de cafes otros no encontrada"));
        ControlRecord controlRecord = controlRecordService.getActive(purchase.getAgency().getId());
        return mapper.toResponse(purchase, controlRecord);
    }

    @Override
    public OtherCoffeePurchaseCalculation preview(OtherCoffeePurchaseRequest request) {
        AnnouncementResponse announcement = announcementService.findLatest(
                request.agencyId(), request.fundId(), request.specialType());
        ControlRecord controlRecord = controlRecordService.getActive(securityUtils.getCurrentAgencyId());
        return runCalculation(request, announcement, controlRecord);
    }

    private OtherCoffeePurchaseCalculation runCalculation(
            OtherCoffeePurchaseRequest request, AnnouncementResponse announcement, ControlRecord controlRecord) {
        YearMonth currentMonth = YearMonth.now();
        MonthlyGrowerTotals monthlyTotals = otherCoffeePurchaseRepository.sumMonthlyTotalsByIdNumber(
                request.idNumber(), currentMonth.atDay(1), currentMonth.atEndOfMonth());

        OtherCoffeePurchaseCalculation calculation = calculator.calculate(
                request,
                controlRecord,
                announcement.basePriceLoad(),
                monthlyTotals.grossValue(),
                monthlyTotals.withholding());
        // Item C (cupo) - mismos Especiales/fuente que Cafe Seco, ver GrowerService.checkQuota.
        growerService.checkQuota(request.idNumber(), request.specialType(), calculation.netKg());
        return calculation;
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

    @Override
    public SpecialInfoResponse specialInfo(Long agencyId, Long fundId, String specialType) {
        ProductCode productCode = productCodeResolver.resolve(specialType, fundId);
        AnnouncementResponse announcement = announcementService.findLatest(agencyId, fundId, specialType);
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        BigDecimal basePriceLoad = announcement.basePriceLoad()
                .subtract(announcement.costs().multiply(BigDecimal.valueOf(controlRecord.getBaseLoad())))
                .setScale(2, RoundingMode.HALF_UP);
        return new SpecialInfoResponse(
                productCode.getCode(),
                announcement.announcementNumber(),
                announcement.announcementDate(),
                basePriceLoad,
                announcement.defectiveUnitPrice(),
                announcement.healthyUnitPrice(),
                announcement.bonus(),
                announcement.costs());
    }

    @Override
    public QualityPercentagesResponse qualityPercentages(
            BigDecimal totalStoredWeight, BigDecimal defectiveStoredWeight, BigDecimal healthyStoredWeight) {
        ControlRecord controlRecord = controlRecordService.getActive(securityUtils.getCurrentAgencyId());
        return new QualityPercentagesResponse(
                totalStoredWeight == null ? null : calculator.wastePercentage(totalStoredWeight, controlRecord),
                defectiveStoredWeight == null
                        ? null
                        : calculator.defectivePercentage(defectiveStoredWeight, controlRecord),
                healthyStoredWeight == null
                        ? null
                        : calculator.healthyPercentage(healthyStoredWeight, controlRecord));
    }
}
