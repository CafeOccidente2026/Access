package com.cafeoccidente.backend.purchases.greencoffee.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.greencoffee.dto.AnnouncementInfoResponse;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.shared.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.greencoffee.entity.GreenCoffeePurchase;
import com.cafeoccidente.backend.purchases.greencoffee.mapper.GreenCoffeePurchaseMapper;
import com.cafeoccidente.backend.purchases.greencoffee.repository.GreenCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.greencoffee.repository.MonthlyGrowerTotals;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseCalculation;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseCalculator;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
import java.math.BigDecimal;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchasePaymentService;
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GreenCoffeePurchaseServiceImpl implements GreenCoffeePurchaseService {

    /** Especial fijo (Cuadro combinado61, RowSource="CV" en VERDES.txt - sin AfterUpdate). */
    private static final String SPECIAL_TYPE = "CV";
    /** Fondo fijo (DefaultValue="RP" en VERDES.txt - sin combo real). */
    private static final String FUND_CODE = "RP";

    private final GreenCoffeePurchaseRepository greenCoffeePurchaseRepository;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final ProductCodeResolver productCodeResolver;
    private final AnnouncementService announcementService;
    private final ControlRecordService controlRecordService;
    private final GreenCoffeePurchaseCalculator calculator;
    private final GreenCoffeePurchaseMapper mapper;
    private final SecurityUtils securityUtils;
    private final CurrentAgencyProvider currentAgencyProvider;
    private final GrowerService growerService;
    private final InvoiceConsecutiveService invoiceConsecutiveService;
    private final InventoryMovementService inventoryMovementService;
    private final PurchasePaymentService purchasePaymentService;

    public GreenCoffeePurchaseServiceImpl(
            GreenCoffeePurchaseRepository greenCoffeePurchaseRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            GreenCoffeePurchaseCalculator calculator,
            GreenCoffeePurchaseMapper mapper,
            SecurityUtils securityUtils, CurrentAgencyProvider currentAgencyProvider,
            GrowerService growerService,
            InvoiceConsecutiveService invoiceConsecutiveService,
            InventoryMovementService inventoryMovementService,
            PurchasePaymentService purchasePaymentService) {
        this.greenCoffeePurchaseRepository = greenCoffeePurchaseRepository;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.productCodeResolver = productCodeResolver;
        this.announcementService = announcementService;
        this.controlRecordService = controlRecordService;
        this.calculator = calculator;
        this.mapper = mapper;
        this.securityUtils = securityUtils;
        this.currentAgencyProvider = currentAgencyProvider;
        this.growerService = growerService;
        this.invoiceConsecutiveService = invoiceConsecutiveService;
        this.inventoryMovementService = inventoryMovementService;
        this.purchasePaymentService = purchasePaymentService;
    }

    @Override
    @Transactional
    public GreenCoffeePurchaseResponse create(GreenCoffeePurchaseRequest request) {
        request = request.withWithholdingExempt(growerService.isAssociation(request.idNumber()));
        Agency agency = agencyRepository.findById(currentAgencyProvider.currentAgencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
        ProductCode productCode = productCodeResolver.resolve(SPECIAL_TYPE, fund.getId());
        AnnouncementResponse announcement = announcementService.findLatest(agency.getId(), fund.getId(), SPECIAL_TYPE);
        GreenCoffeePurchaseCalculation calculation = runCalculation(request, announcement);

        Long currentUserId = securityUtils.getCurrentUserId();

        GreenCoffeePurchase purchase = new GreenCoffeePurchase();
        purchase.setPurchaseDate(LocalDate.now());
        // HU-15: el numero lo toma el servidor dentro de esta transaccion (Q7: compartido seco/verde/pasilla).
        purchase.setInvoiceNumber(invoiceConsecutiveService.take(agency.getId(), controlRecordService.getActive(agency.getId())));
        purchase.setAgency(agency);
        purchase.setFund(fund);
        purchase.setSpecialType(SPECIAL_TYPE);
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
        purchase.setGreenKg(calculation.greenKg());
        purchase.setNetKg(calculation.netKg());
        purchase.setHealthyUnitPrice(request.healthyUnitPrice());
        purchase.setDefectiveUnitPrice(announcement.defectiveUnitPrice());
        purchase.setBonus(request.bonus());
        purchase.setCosts(request.costs());
        purchase.setPenalty(request.penalty());
        purchase.setCompKgPrice(request.compKgPrice());
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
        PurchasePaymentService.ResolvedPayment payment =
                purchasePaymentService.validate(request.payment(), calculation.netToPay());
        purchase.setPaymentMethod(payment.paymentMethod());
        purchase.setCheckNumber(payment.checkNumber());
        purchase.setCreatedByUserId(currentUserId);
        purchase.setCreatedAt(Instant.now());

        GreenCoffeePurchase savedPurchase = greenCoffeePurchaseRepository.save(purchase);
        purchasePaymentService.record("GREEN", savedPurchase.getId(), request.payment());

        inventoryMovementService.recordFromPurchaseSafely(
                InventoryMovement.PurchaseModule.GREEN_COFFEE, savedPurchase.getId(),
                agency.getId(), productCode.getId(), purchase.getSpecialType(), purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(), purchase.getBagsCount(), purchase.getGrossKg(), purchase.getNetKg(),
                null, purchase.getInventoryValue());

        return mapper.toResponse(savedPurchase);
    }

    @Override
    public GreenCoffeePurchaseResponse findById(Long id) {
        return greenCoffeePurchaseRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Compra de cafe verde no encontrada"));
    }

    @Override
    public GreenCoffeePurchaseCalculation preview(GreenCoffeePurchaseRequest request) {
        request = request.withWithholdingExempt(growerService.isAssociation(request.idNumber()));
        AnnouncementResponse announcement =
                announcementService.findLatest(currentAgencyProvider.currentAgencyId(), request.fundId(), SPECIAL_TYPE);
        return runCalculation(request, announcement);
    }

    private GreenCoffeePurchaseCalculation runCalculation(
            GreenCoffeePurchaseRequest request, AnnouncementResponse announcement) {
        ControlRecord controlRecord = controlRecordService.getActive(currentAgencyProvider.currentAgencyId());

        MonthlyGrowerTotals dailyTotals = greenCoffeePurchaseRepository.sumDailyTotalsByIdNumber(
                request.idNumber(), LocalDate.now());

        GreenCoffeePurchaseCalculation calculation = calculator.calculate(
                request,
                controlRecord,
                announcement.basePriceLoad(),
                dailyTotals.grossValue(),
                dailyTotals.withholding());
        // Item C (cupo) - ver GrowerService.checkQuota. SPECIAL_TYPE fijo ("CV") nunca matchea
        // SPECIAL_TO_PROGRAMA hoy, pero queda enganchado para cuando se migren mas programas.
        growerService.checkQuota(request.idNumber(), SPECIAL_TYPE, calculation.netKg());
        return calculation;
    }

    @Override
    public NextInvoiceNumberResponse nextInvoiceNumber() {
        Long agencyId = currentAgencyProvider.currentAgencyId();
        return invoiceConsecutiveService.next(agencyId, controlRecordService.getActive(agencyId));
    }

    @Override
    public AnnouncementInfoResponse announcementInfo() {
        Long agencyId = currentAgencyProvider.currentAgencyId();
        Fund fund = fundRepository.findByCode(FUND_CODE)
                .orElseThrow(() -> new ResourceNotFoundException("Fondo RP no encontrado"));
        ProductCode productCode = productCodeResolver.resolve(SPECIAL_TYPE, fund.getId());
        AnnouncementResponse announcement = announcementService.findLatest(agencyId, fund.getId(), SPECIAL_TYPE);
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        // Precio Base Carga PC = Pr_Base_CPS crudo del anuncio maestro - (Costos * BaseCarga)
        // (Form_VERDES.bas: Pr_Base_PC = Texto91 - (Costos * Texto176)). Desde el anuncio compartido
        // entre agencias (ver docs/diseno-anuncios-compartidos.md), "Costos" ya NO viene congelado:
        // announcement.costs() lo calcula AnnouncementServiceImpl.findLatest() con el ControlRecord
        // VIVO de esta agencia (decision de negocio para el sistema nuevo, distinta del VBA legado).
        BigDecimal basePriceLoad = announcement.basePriceLoad()
                .subtract(announcement.costs().multiply(BigDecimal.valueOf(controlRecord.getBaseLoad())))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        return new AnnouncementInfoResponse(
                fund.getId(),
                productCode.getCode(),
                announcement.announcementNumber(),
                announcement.announcementDate(),
                basePriceLoad,
                announcement.defectiveUnitPrice(),
                announcement.healthyUnitPrice(),
                announcement.bonus(),
                announcement.costs());
    }
}
