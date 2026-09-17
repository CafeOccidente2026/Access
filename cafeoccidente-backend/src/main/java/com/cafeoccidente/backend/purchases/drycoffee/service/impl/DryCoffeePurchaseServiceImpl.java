package com.cafeoccidente.backend.purchases.drycoffee.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
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
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
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

    public DryCoffeePurchaseServiceImpl(
            DryCoffeePurchaseRepository dryCoffeePurchaseRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            DryCoffeePurchaseCalculator calculator,
            DryCoffeePurchaseMapper mapper,
            SecurityUtils securityUtils) {
        this.dryCoffeePurchaseRepository = dryCoffeePurchaseRepository;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.productCodeResolver = productCodeResolver;
        this.announcementService = announcementService;
        this.controlRecordService = controlRecordService;
        this.calculator = calculator;
        this.mapper = mapper;
        this.securityUtils = securityUtils;
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
        DryCoffeePurchaseCalculation calculation = runCalculation(request, announcement);

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

        return mapper.toResponse(dryCoffeePurchaseRepository.save(purchase));
    }

    @Override
    public DryCoffeePurchaseResponse findById(Long id) {
        return dryCoffeePurchaseRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Compra de cafe seco no encontrada"));
    }

    @Override
    public DryCoffeePurchaseCalculation preview(DryCoffeePurchaseRequest request) {
        AnnouncementResponse announcement = announcementService.findLatest(
                request.agencyId(), request.fundId(), request.specialType());
        return runCalculation(request, announcement);
    }

    /** Fetches the current user's agency ControlRecord + acumulado mensual and runs the cascade. */
    private DryCoffeePurchaseCalculation runCalculation(
            DryCoffeePurchaseRequest request, AnnouncementResponse announcement) {
        ControlRecord controlRecord = controlRecordService.getActive(securityUtils.getCurrentAgencyId());

        YearMonth currentMonth = YearMonth.now();
        MonthlyGrowerTotals monthlyTotals = dryCoffeePurchaseRepository.sumMonthlyTotalsByIdNumber(
                request.idNumber(), currentMonth.atDay(1), currentMonth.atEndOfMonth());

        return calculator.calculate(
                request,
                controlRecord,
                announcement.basePriceLoad(),
                announcement.defectiveUnitPrice(),
                monthlyTotals.grossValue(),
                monthlyTotals.withholding());
    }

    @Override
    public NextInvoiceNumberResponse nextInvoiceNumber() {
        Long agencyId = securityUtils.getCurrentAgencyId();
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        Integer maxUsed = dryCoffeePurchaseRepository.findMaxInvoiceNumber(agencyId);
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
        // Precio Base Carga PC = Pr_Base_CPS crudo del anuncio - (Costos * BaseCarga) (Form_COMPRAS.bas:
        // Cuadro_combinado61_AfterUpdate linea 361: Pr_Base_PC = vrcps - (Costos * Texto176)). "Costos"
        // es el valor CONGELADO que la macro de anuncio escribe en el textbox -> announcement.costs()
        // (misma fuente que request.costs() usa el calculador una vez que existe la compra real).
        // BaseCarga (Texto176) si es del RegControl vivo -> controlRecord.getBaseLoad().
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
