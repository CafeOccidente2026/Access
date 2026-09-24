package com.cafeoccidente.backend.purchases.drycoffee.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.QualityPercentagesResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.SpecialInfoResponse;
import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import com.cafeoccidente.backend.purchases.drycoffee.mapper.DryCoffeePurchaseMapper;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.drycoffee.repository.MonthlyGrowerTotals;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculation;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculator;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseInvoiceNumberService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DryCoffeePurchaseServiceImpl implements DryCoffeePurchaseService {

    private final DryCoffeePurchaseRepository dryCoffeePurchaseRepository;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final ProductCodeResolver productCodeResolver;
    private final AnnouncementService announcementService;
    private final ControlRecordService controlRecordService;
    private final DryCoffeePurchaseCalculator calculator;
    private final DryCoffeePurchaseMapper mapper;
    private final SecurityUtils securityUtils;
    private final GrowerService growerService;
    private final PurchaseInvoiceNumberService purchaseInvoiceNumberService;
    private final InventoryMovementService inventoryMovementService;

    public DryCoffeePurchaseServiceImpl(
            DryCoffeePurchaseRepository dryCoffeePurchaseRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            DryCoffeePurchaseCalculator calculator,
            DryCoffeePurchaseMapper mapper,
            SecurityUtils securityUtils,
            GrowerService growerService,
            PurchaseInvoiceNumberService purchaseInvoiceNumberService,
            InventoryMovementService inventoryMovementService) {
        this.dryCoffeePurchaseRepository = dryCoffeePurchaseRepository;
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
    public DryCoffeePurchaseResponse create(DryCoffeePurchaseRequest request) {
        Agency agency = agencyRepository.findById(request.agencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
        ProductCode productCode = productCodeResolver.resolve(request.specialType(), fund.getId());
        AnnouncementResponse announcement =
                announcementService.findLatest(agency.getId(), fund.getId(), request.specialType());
        ControlRecord controlRecord = controlRecordService.getActive(agency.getId());
        DryCoffeePurchaseCalculation calculation = runCalculation(request, announcement, controlRecord);

        Long currentUserId = securityUtils.getCurrentUserId();

        DryCoffeePurchase purchase = new DryCoffeePurchase();
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

        DryCoffeePurchase savedPurchase = dryCoffeePurchaseRepository.save(purchase);

        inventoryMovementService.recordFromPurchaseSafely(
                InventoryMovement.PurchaseModule.DRY_COFFEE, savedPurchase.getId(),
                agency.getId(), productCode.getId(), purchase.getSpecialType(), purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(), purchase.getBagsCount(), purchase.getGrossKg(), purchase.getNetKg(),
                purchase.getHealthyPercentage(), purchase.getInventoryValue());

        return mapper.toResponse(savedPurchase, controlRecord);
    }

    @Override
    public DryCoffeePurchaseResponse findById(Long id) {
        DryCoffeePurchase purchase = dryCoffeePurchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compra de cafe seco no encontrada"));
        ControlRecord controlRecord = controlRecordService.getActive(purchase.getAgency().getId());
        return mapper.toResponse(purchase, controlRecord);
    }

    @Override
    public DryCoffeePurchaseCalculation preview(DryCoffeePurchaseRequest request) {
        AnnouncementResponse announcement = announcementService.findLatest(
                request.agencyId(), request.fundId(), request.specialType());
        ControlRecord controlRecord = controlRecordService.getActive(securityUtils.getCurrentAgencyId());
        return runCalculation(request, announcement, controlRecord);
    }

    /** Corre la cascada con el ControlRecord ya resuelto por el caller (evita pedirlo dos veces). */
    private DryCoffeePurchaseCalculation runCalculation(
            DryCoffeePurchaseRequest request, AnnouncementResponse announcement, ControlRecord controlRecord) {
        YearMonth currentMonth = YearMonth.now();
        MonthlyGrowerTotals monthlyTotals = dryCoffeePurchaseRepository.sumMonthlyTotalsByIdNumber(
                request.idNumber(), currentMonth.atDay(1), currentMonth.atEndOfMonth());

        DryCoffeePurchaseCalculation calculation = calculator.calculate(
                request,
                controlRecord,
                announcement.basePriceLoad(),
                announcement.defectiveUnitPrice(),
                monthlyTotals.grossValue(),
                monthlyTotals.withholding());
        // Item C (cupo, Form_COMPRAS.bas lineas 412/465/518/571/624) - ver GrowerService.checkQuota.
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
        // Precio Base Carga PC = Pr_Base_CPS crudo del anuncio maestro - (Costos * BaseCarga)
        // (Form_COMPRAS.bas: Pr_Base_PC = vrcps - (Costos * Texto176)). Desde el anuncio compartido
        // entre agencias (ver docs/diseno-anuncios-compartidos.md), "Costos" ya NO viene congelado:
        // announcement.costs() lo calcula AnnouncementServiceImpl.findLatest() con el ControlRecord
        // VIVO de esta agencia (decision de negocio para el sistema nuevo, distinta del VBA legado).
        BigDecimal basePriceLoad = announcement.basePriceLoad()
                .subtract(announcement.costs().multiply(BigDecimal.valueOf(controlRecord.getBaseLoad())))
                .setScale(2, java.math.RoundingMode.HALF_UP);
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
