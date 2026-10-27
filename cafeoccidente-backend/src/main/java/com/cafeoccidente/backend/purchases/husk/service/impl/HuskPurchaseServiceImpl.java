package com.cafeoccidente.backend.purchases.husk.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
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
import com.cafeoccidente.backend.purchases.shared.dto.NextInvoiceNumberResponse;
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
import com.cafeoccidente.backend.purchases.shared.service.PurchasePaymentService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseSellerService;
import java.time.Instant;
import java.time.LocalDate;
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
    private final CurrentAgencyProvider currentAgencyProvider;
    private final GrowerService growerService;
    private final PurchaseSellerService purchaseSellerService;
    private final InvoiceConsecutiveService invoiceConsecutiveService;
    private final InventoryMovementService inventoryMovementService;
    private final PurchasePaymentService purchasePaymentService;

    public HuskPurchaseServiceImpl(
            HuskPurchaseRepository huskPurchaseRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            HuskPurchaseCalculator calculator,
            HuskPurchaseMapper mapper,
            SecurityUtils securityUtils, CurrentAgencyProvider currentAgencyProvider,
            GrowerService growerService,
            PurchaseSellerService purchaseSellerService,
            InvoiceConsecutiveService invoiceConsecutiveService,
            InventoryMovementService inventoryMovementService,
            PurchasePaymentService purchasePaymentService) {
        this.huskPurchaseRepository = huskPurchaseRepository;
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
        this.purchaseSellerService = purchaseSellerService;
        this.invoiceConsecutiveService = invoiceConsecutiveService;
        this.inventoryMovementService = inventoryMovementService;
        this.purchasePaymentService = purchasePaymentService;
    }

    @Override
    @Transactional
    public HuskPurchaseResponse create(HuskPurchaseRequest request) {
        // Q8: el vendedor sale de TERCEROS; lo digitado solo completa direccion o celular vacios.
        PurchaseSeller seller = purchaseSellerService.resolveAndComplete(
                request.idNumber(), request.address(), request.cellphone());
        Agency agency = agencyRepository.findById(currentAgencyProvider.currentAgencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        Fund fund = fundRepository.findById(request.fundId())
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
        ProductCode productCode = productCodeResolver.resolve(SPECIAL_TYPE, fund.getId());
        AnnouncementResponse announcement = announcementService.findLatest(agency.getId(), fund.getId(), SPECIAL_TYPE);
        HuskPurchaseCalculation calculation = runCalculation(request, announcement, seller);

        Long currentUserId = securityUtils.getCurrentUserId();

        HuskPurchase purchase = new HuskPurchase();
        purchase.setPurchaseDate(LocalDate.now());
        // HU-15: el numero lo toma el servidor dentro de esta transaccion (Q7: compartido seco/verde/pasilla).
        purchase.setInvoiceNumber(invoiceConsecutiveService.take(agency.getId(), controlRecordService.getActive(agency.getId())));
        purchase.setAgency(agency);
        purchase.setFund(fund);
        purchase.setSpecialType(SPECIAL_TYPE);
        purchase.setProductCode(productCode);
        purchase.setAnnouncementNumber(announcement.announcementNumber());
        purchase.setAnnouncementDate(announcement.announcementDate());
        purchase.setBasePriceDryLoad(announcement.basePriceLoad());
        purchase.setIdNumber(request.idNumber());
        purchase.setFirstName(seller.firstName());
        purchase.setLastName(seller.lastName());
        purchase.setGrowerType(seller.growerType());
        purchase.setAddress(seller.address());
        purchase.setCellphone(seller.cellphone());
        purchase.setPointPrice(announcement.healthyUnitPrice());
        purchase.setCosts(announcement.costs());
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
        purchase.setWithholdingExempt(seller.association());
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

        HuskPurchase savedPurchase = huskPurchaseRepository.save(purchase);
        purchasePaymentService.record("HUSK", savedPurchase.getId(), request.payment());

        inventoryMovementService.recordFromPurchaseSafely(
                InventoryMovement.PurchaseModule.HUSK, savedPurchase.getId(),
                agency.getId(), productCode.getId(), purchase.getSpecialType(), purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(), purchase.getBagsCount(), purchase.getGrossKg(), purchase.getNetKg(),
                purchase.getAlmondPercentage(), purchase.getInventoryValue());

        return mapper.toResponse(savedPurchase, controlRecordService.getActive(agency.getId()));
    }

    @Override
    public HuskPurchaseResponse findById(Long id) {
        return huskPurchaseRepository.findById(id)
                .map(p -> mapper.toResponse(p, controlRecordService.getActive(p.getAgency().getId())))
                .orElseThrow(() -> new ResourceNotFoundException("Compra de pasilla no encontrada"));
    }

    @Override
    public HuskPurchaseCalculation preview(HuskPurchaseRequest request) {
        PurchaseSeller seller = purchaseSellerService.resolve(request.idNumber(), request.address(), request.cellphone());
        AnnouncementResponse announcement =
                announcementService.findLatest(currentAgencyProvider.currentAgencyId(), request.fundId(), SPECIAL_TYPE);
        return runCalculation(request, announcement, seller);
    }

    private HuskPurchaseCalculation runCalculation(
            HuskPurchaseRequest request, AnnouncementResponse announcement, PurchaseSeller seller) {
        ControlRecord controlRecord = controlRecordService.getActive(currentAgencyProvider.currentAgencyId());

        MonthlyGrowerTotals dailyTotals = huskPurchaseRepository.sumDailyTotalsByIdNumber(
                request.idNumber(), LocalDate.now());

        HuskPurchaseCalculation calculation =
                calculator.calculate(
                request, controlRecord, announcement, seller, dailyTotals.grossValue(), dailyTotals.withholding());
        // Item C (cupo) - ver GrowerService.checkQuota. SPECIAL_TYPE fijo ("PASILLA") nunca matchea
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
