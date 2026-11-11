package com.cafeoccidente.backend.purchases.husk.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.husk.dto.AnnouncementInfoResponse;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.correction.dto.PurchaseCorrection;
import com.cafeoccidente.backend.purchases.correction.service.PurchaseCorrectionService;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseResponse;
import com.cafeoccidente.backend.purchases.shared.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.husk.entity.HuskPurchase;
import com.cafeoccidente.backend.purchases.husk.mapper.HuskPurchaseMapper;
import com.cafeoccidente.backend.purchases.husk.repository.HuskPurchaseRepository;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseCalculation;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseCalculator;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchasePaymentService;
import com.cafeoccidente.backend.purchases.shared.dto.DailyGrowerTotals;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseContext;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseContextService;
import java.time.Clock;
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
    private final FundRepository fundRepository;
    private final ProductCodeResolver productCodeResolver;
    private final AnnouncementService announcementService;
    private final ControlRecordService controlRecordService;
    private final HuskPurchaseCalculator calculator;
    private final HuskPurchaseMapper mapper;
    private final SecurityUtils securityUtils;
    private final CurrentAgencyProvider currentAgencyProvider;
    private final GrowerService growerService;
    private final PurchaseContextService purchaseContextService;
    private final InvoiceConsecutiveService invoiceConsecutiveService;
    private final InventoryMovementService inventoryMovementService;
    private final PurchasePaymentService purchasePaymentService;
    private final PurchaseCorrectionService purchaseCorrectionService;
    private final Clock clock;

    public HuskPurchaseServiceImpl(
            HuskPurchaseRepository huskPurchaseRepository,
            FundRepository fundRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            HuskPurchaseCalculator calculator,
            HuskPurchaseMapper mapper,
            SecurityUtils securityUtils, CurrentAgencyProvider currentAgencyProvider,
            GrowerService growerService,
            PurchaseContextService purchaseContextService,
            InvoiceConsecutiveService invoiceConsecutiveService,
            InventoryMovementService inventoryMovementService,
            PurchasePaymentService purchasePaymentService,
            PurchaseCorrectionService purchaseCorrectionService,
            Clock clock) {
        this.clock = clock;
        this.purchaseCorrectionService = purchaseCorrectionService;
        this.huskPurchaseRepository = huskPurchaseRepository;
        this.fundRepository = fundRepository;
        this.productCodeResolver = productCodeResolver;
        this.announcementService = announcementService;
        this.controlRecordService = controlRecordService;
        this.calculator = calculator;
        this.mapper = mapper;
        this.securityUtils = securityUtils;
        this.currentAgencyProvider = currentAgencyProvider;
        this.growerService = growerService;
        this.purchaseContextService = purchaseContextService;
        this.invoiceConsecutiveService = invoiceConsecutiveService;
        this.inventoryMovementService = inventoryMovementService;
        this.purchasePaymentService = purchasePaymentService;
    }

    @Override
    @Transactional
    public HuskPurchaseResponse create(HuskPurchaseRequest request) {
        // Agencia del token, fondo, producto, anuncio, Registro de Control y vendedor (Q8).
        PurchaseContext context = purchaseContextService.forSave(
                request.fundId(), SPECIAL_TYPE, request.idNumber(), request.address(), request.cellphone());
        HuskPurchaseCalculation calculation = runCalculation(request, context);

        Long currentUserId = securityUtils.getCurrentUserId();

        HuskPurchase purchase = new HuskPurchase();
        purchase.setPurchaseDate(LocalDate.now(clock));
        // HU-15: el numero lo toma el servidor dentro de esta transaccion (Q7: compartido seco/verde/pasilla).
        purchase.setInvoiceNumber(invoiceConsecutiveService.take(context.agency().getId(), context.controlRecord()));
        fill(purchase, request, context, calculation);
        purchase.setCreatedByUserId(currentUserId);
        purchase.setCreatedAt(Instant.now());

        HuskPurchase savedPurchase = huskPurchaseRepository.save(purchase);
        purchasePaymentService.record(
                "HUSK", savedPurchase.getId(), savedPurchase.getInvoiceNumber(), request.payment(), context);

        recordInventory(savedPurchase, context);

        return mapper.toResponse(savedPurchase, context.controlRecord());
    }

    /**
     * HU-05 (11-nov): corrige pesos, castigo, descuentos, direccion, celular y pago con los precios del anuncio
     * original. Fondo, cedula, fecha, anuncio y numero de factura no cambian.
     */
    @Override
    @Transactional
    public HuskPurchaseResponse correct(Long id, HuskPurchaseRequest request) {
        PurchaseCorrection correction =
                purchaseCorrectionService.start(AnnulmentModule.HUSK, id, request.address(), request.cellphone());
        HuskPurchase purchase = huskPurchaseRepository.findById(id).orElseThrow();
        HuskPurchaseRequest fixed = fixedRequest(purchase, request);
        HuskPurchaseCalculation calculation = runCalculation(fixed, correction.context());
        purchaseCorrectionService.settle(correction, calculation.netKg(), request.payment());
        fill(purchase, fixed, correction.context(), calculation);
        HuskPurchase saved = huskPurchaseRepository.saveAndFlush(purchase);
        recordInventory(saved, correction.context());
        purchaseCorrectionService.close(correction);
        return mapper.toResponse(saved, correction.context().controlRecord());
    }

    /** Neto, retencion y demas de la correccion, sin guardar: con el anuncio original y la compra bloqueada. */
    @Override
    @Transactional
    public HuskPurchaseCalculation previewCorrection(Long id, HuskPurchaseRequest request) {
        PurchaseCorrection correction =
                purchaseCorrectionService.start(AnnulmentModule.HUSK, id, request.address(), request.cellphone());
        HuskPurchase purchase = huskPurchaseRepository.findById(id).orElseThrow();
        return runCalculation(fixedRequest(purchase, request), correction.context());
    }

    /** Lo digitado, con lo que la correccion deja fijo (fondo, cedula y, segun el modulo, especial o Vr Kilo Comp). */
    private static HuskPurchaseRequest fixedRequest(HuskPurchase purchase, HuskPurchaseRequest request) {
        return new HuskPurchaseRequest(purchase.getFund().getId(), purchase.getIdNumber(),
                request.address(), request.cellphone(), request.almondWeight(), request.bagsCount(), request.grossKg(),
                request.tareKg(), request.shrinkageDiscount(), request.otherDiscounts(), request.payment());
    }

    /** Lo que sale del contexto y del calculo, igual al crear y al corregir; el pago se valida contra el neto. */
    private void fill(HuskPurchase purchase, HuskPurchaseRequest request, PurchaseContext context,
            HuskPurchaseCalculation calculation) {
        AnnouncementResponse announcement = context.announcement();
        PurchaseSeller seller = context.seller();
        purchase.setAgency(context.agency());
        purchase.setFund(context.fund());
        purchase.setSpecialType(SPECIAL_TYPE);
        purchase.setProductCode(context.productCode());
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
    }

    private void recordInventory(HuskPurchase purchase, PurchaseContext context) {
        inventoryMovementService.recordFromPurchase(
                InventoryMovement.PurchaseModule.HUSK, purchase.getId(),
                context.agency().getId(), context.productCode().getId(), purchase.getSpecialType(),
                purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(), purchase.getBagsCount(), purchase.getGrossKg(), purchase.getNetKg(),
                purchase.getAlmondPercentage(), purchase.getInventoryValue());
    }

    @Override
    public HuskPurchaseResponse findById(Long id) {
        // Solo de la agencia de trabajo (RF-05.1): la de otra agencia es "no encontrada", como Consultar Compras.
        return huskPurchaseRepository.findById(id)
                .filter(p -> p.getAgency().getId().equals(currentAgencyProvider.currentAgencyId()))
                .map(p -> mapper.toResponse(p, controlRecordService.getActive(p.getAgency().getId())))
                .orElseThrow(() -> new ResourceNotFoundException("Compra de pasilla no encontrada"));
    }

    @Override
    public HuskPurchaseCalculation preview(HuskPurchaseRequest request) {
        return runCalculation(request, purchaseContextService.forPreview(
                request.fundId(), SPECIAL_TYPE, request.idNumber(), request.address(), request.cellphone()));
    }

    private HuskPurchaseCalculation runCalculation(HuskPurchaseRequest request, PurchaseContext context) {
        // Q10: lo que el fiel ya vendio hoy en seco + verde + pasilla, en todas las agencias.
        DailyGrowerTotals dailyTotals = context.dailyTotals();

        HuskPurchaseCalculation calculation =
                calculator.calculate(
                request, context.controlRecord(), context.announcement(), context.seller(),
                dailyTotals.grossValue(), dailyTotals.withholding());
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
