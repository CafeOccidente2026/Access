package com.cafeoccidente.backend.purchases.husk.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.husk.dto.AnnouncementInfoResponse;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseResponse;
import com.cafeoccidente.backend.purchases.husk.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.husk.entity.HuskPurchase;
import com.cafeoccidente.backend.purchases.husk.mapper.HuskPurchaseMapper;
import com.cafeoccidente.backend.purchases.husk.repository.HuskPurchaseRepository;
import com.cafeoccidente.backend.purchases.husk.repository.MonthlyGrowerTotals;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseCalculation;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseCalculator;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseInvoiceNumberService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HuskPurchaseServiceImpl implements HuskPurchaseService {

    /** Especial fijo en la practica (Cuadro combinado61, RowSource="PASILLA" en PASILLA.txt). */
    private static final String SPECIAL_TYPE = "PASILLA";
    /** Fondo fijo en la practica (Cuadro combinado37, RowSource="RP" en PASILLA.txt). */
    private static final String FUND_CODE = "RP";

    private final HuskPurchaseRepository huskPurchaseRepository;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final ProductCodeResolver productCodeResolver;
    private final AnnouncementService announcementService;
    private final ControlRecordService controlRecordService;
    private final HuskPurchaseCalculator calculator;
    private final HuskPurchaseMapper mapper;
    private final SecurityUtils securityUtils;
    private final GrowerService growerService;
    private final PurchaseInvoiceNumberService purchaseInvoiceNumberService;
    private final InventoryMovementService inventoryMovementService;

    public HuskPurchaseServiceImpl(
            HuskPurchaseRepository huskPurchaseRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            HuskPurchaseCalculator calculator,
            HuskPurchaseMapper mapper,
            SecurityUtils securityUtils,
            GrowerService growerService,
            PurchaseInvoiceNumberService purchaseInvoiceNumberService,
            InventoryMovementService inventoryMovementService) {
        this.huskPurchaseRepository = huskPurchaseRepository;
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
    public HuskPurchaseResponse create(HuskPurchaseRequest request) {
        Agency agency = agencyRepository.findById(request.agencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
        ProductCode productCode = productCodeResolver.resolve(SPECIAL_TYPE, fund.getId());
        AnnouncementResponse announcement = announcementService.findLatest(agency.getId(), fund.getId(), SPECIAL_TYPE);
        HuskPurchaseCalculation calculation = runCalculation(request, announcement);

        Long currentUserId = securityUtils.getCurrentUserId();

        HuskPurchase purchase = new HuskPurchase();
        purchase.setPurchaseDate(LocalDate.now());
        purchase.setInvoiceNumber(request.invoiceNumber());
        purchase.setAgency(agency);
        purchase.setFund(fund);
        purchase.setSpecialType(SPECIAL_TYPE);
        purchase.setProductCode(productCode);
        purchase.setAnnouncementNumber(announcement.announcementNumber());
        purchase.setAnnouncementDate(announcement.announcementDate());
        purchase.setBasePriceDryLoad(announcement.basePriceLoad());
        purchase.setIdNumber(request.idNumber());
        purchase.setFirstName(request.firstName());
        purchase.setLastName(request.lastName());
        purchase.setGrowerType(request.growerType());
        purchase.setAddress(request.address());
        purchase.setCellphone(request.cellphone());
        purchase.setPointPrice(request.pointPrice());
        purchase.setCosts(request.costs());
        purchase.setAlmondWeight(request.almondWeight());
        purchase.setAlmondPercentage(calculation.almondPercentage());
        purchase.setBagsCount(request.bagsCount());
        purchase.setGrossKg(request.grossKg());
        purchase.setTareKg(request.tareKg());
        purchase.setNetKg(calculation.netKg());
        purchase.setUnitPrice(calculation.unitPrice());
        purchase.setGrossValue(calculation.grossValue());
        purchase.setInventoryValue(calculation.inventoryValue());
        purchase.setAssociateContribution(calculation.associateContribution());
        purchase.setCooperativeDiscount(calculation.cooperativeDiscount());
        purchase.setWithholdingExempt(request.withholdingExempt());
        purchase.setWithholding(calculation.withholding());
        purchase.setShrinkageDiscount(request.shrinkageDiscount());
        purchase.setOtherDiscounts(request.otherDiscounts());
        purchase.setNetToPay(calculation.netToPay());
        purchase.setPaymentMethod(request.paymentMethod());
        purchase.setCheckNumber(request.checkNumber());
        purchase.setCreatedByUserId(currentUserId);
        purchase.setCreatedAt(Instant.now());

        HuskPurchase savedPurchase = huskPurchaseRepository.save(purchase);

        inventoryMovementService.recordFromPurchaseSafely(
                InventoryMovement.PurchaseModule.HUSK, savedPurchase.getId(),
                agency.getId(), productCode.getId(), purchase.getSpecialType(), purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(), purchase.getBagsCount(), purchase.getGrossKg(), purchase.getNetKg(),
                purchase.getAlmondPercentage(), purchase.getInventoryValue());

        return mapper.toResponse(savedPurchase);
    }

    @Override
    public HuskPurchaseResponse findById(Long id) {
        return huskPurchaseRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Compra de pasilla no encontrada"));
    }

    @Override
    public HuskPurchaseCalculation preview(HuskPurchaseRequest request) {
        AnnouncementResponse announcement =
                announcementService.findLatest(request.agencyId(), request.fundId(), SPECIAL_TYPE);
        return runCalculation(request, announcement);
    }

    private HuskPurchaseCalculation runCalculation(HuskPurchaseRequest request, AnnouncementResponse announcement) {
        ControlRecord controlRecord = controlRecordService.getActive(securityUtils.getCurrentAgencyId());

        YearMonth currentMonth = YearMonth.now();
        MonthlyGrowerTotals monthlyTotals = huskPurchaseRepository.sumMonthlyTotalsByIdNumber(
                request.idNumber(), currentMonth.atDay(1), currentMonth.atEndOfMonth());

        HuskPurchaseCalculation calculation =
                calculator.calculate(request, controlRecord, monthlyTotals.grossValue(), monthlyTotals.withholding());
        // Item C (cupo) - ver GrowerService.checkQuota. SPECIAL_TYPE fijo ("PASILLA") nunca matchea
        // SPECIAL_TO_PROGRAMA hoy, pero queda enganchado para cuando se migren mas programas.
        growerService.checkQuota(request.idNumber(), SPECIAL_TYPE, calculation.netKg());
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
    public AnnouncementInfoResponse announcementInfo() {
        Long agencyId = securityUtils.getCurrentAgencyId();
        Fund fund = fundRepository.findByCode(FUND_CODE)
                .orElseThrow(() -> new ResourceNotFoundException("Fondo RP no encontrado"));
        ProductCode productCode = productCodeResolver.resolve(SPECIAL_TYPE, fund.getId());
        AnnouncementResponse announcement = announcementService.findLatest(agencyId, fund.getId(), SPECIAL_TYPE);
        return new AnnouncementInfoResponse(
                fund.getId(),
                productCode.getCode(),
                announcement.announcementNumber(),
                announcement.announcementDate(),
                announcement.basePriceLoad(),
                announcement.healthyUnitPrice(),
                announcement.costs());
    }
}
