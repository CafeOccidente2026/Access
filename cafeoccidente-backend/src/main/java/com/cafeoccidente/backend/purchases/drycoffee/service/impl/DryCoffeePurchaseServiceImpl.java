package com.cafeoccidente.backend.purchases.drycoffee.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.shared.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.QualityPercentagesResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.SpecialInfoResponse;
import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import com.cafeoccidente.backend.purchases.drycoffee.mapper.DryCoffeePurchaseMapper;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculation;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculator;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.future.service.DeliveryRule;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseService;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchasePaymentService;
import com.cafeoccidente.backend.purchases.shared.dto.DailyGrowerTotals;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseContext;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
import com.cafeoccidente.backend.purchases.shared.service.PenaltyRule;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseContextService;
import com.cafeoccidente.backend.purchases.shared.service.SpecialtyThresholdRule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DryCoffeePurchaseServiceImpl implements DryCoffeePurchaseService {

    private final DryCoffeePurchaseRepository dryCoffeePurchaseRepository;
    private final ProductCodeResolver productCodeResolver;
    private final AnnouncementService announcementService;
    private final ControlRecordService controlRecordService;
    private final DryCoffeePurchaseCalculator calculator;
    private final DryCoffeePurchaseMapper mapper;
    private final SecurityUtils securityUtils;
    private final CurrentAgencyProvider currentAgencyProvider;
    private final GrowerService growerService;
    private final PurchaseContextService purchaseContextService;
    private final InvoiceConsecutiveService invoiceConsecutiveService;
    private final InventoryMovementService inventoryMovementService;
    private final PurchasePaymentService purchasePaymentService;
    private final AnnouncementQuotaService announcementQuotaService;
    private final FuturePurchaseService futurePurchaseService;
    private final Clock clock;

    public DryCoffeePurchaseServiceImpl(
            DryCoffeePurchaseRepository dryCoffeePurchaseRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            DryCoffeePurchaseCalculator calculator,
            DryCoffeePurchaseMapper mapper,
            SecurityUtils securityUtils, CurrentAgencyProvider currentAgencyProvider,
            GrowerService growerService,
            PurchaseContextService purchaseContextService,
            InvoiceConsecutiveService invoiceConsecutiveService,
            InventoryMovementService inventoryMovementService,
            PurchasePaymentService purchasePaymentService,
            AnnouncementQuotaService announcementQuotaService,
            FuturePurchaseService futurePurchaseService,
            Clock clock) {
        this.clock = clock;
        this.announcementQuotaService = announcementQuotaService;
        this.futurePurchaseService = futurePurchaseService;
        this.dryCoffeePurchaseRepository = dryCoffeePurchaseRepository;
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
    public DryCoffeePurchaseResponse create(DryCoffeePurchaseRequest request) {
        // Agencia del token, fondo, producto, anuncio, Registro de Control y vendedor (Q8).
        requireDelivery(request);
        // RF-21.5: la compra anunciada queda bloqueada hasta que termine esta transaccion.
        FuturePurchase future = request.futurePurchaseId() == null
                ? null
                : futurePurchaseService.forBilling(request.futurePurchaseId(), request.idNumber(), true);
        PurchaseContext context = purchaseContextService.forSave(request.fundId(), request.specialType(),
                chosenAnnouncement(request, future), request.idNumber(), request.address(), request.cellphone());
        AnnouncementResponse announcement = context.announcement();
        PurchaseSeller seller = context.seller();
        DryCoffeePurchaseCalculation calculation = runCalculation(request, context);
        // HU-29 CA-4 y RF-21.2: el saldo del cupo se lee y se gasta con la fila del cupo bloqueada.
        announcementQuotaService.reserve(context.agency().getId(), announcement.announcementNumber(),
                calculation.netKg(), request.quotaAnnouncementNumber() == null ? null : request.deliveryKg());
        if (future != null) {
            futurePurchaseService.deliver(future, request.deliveryKg(), calculation.netKg());
        }

        Long currentUserId = securityUtils.getCurrentUserId();

        DryCoffeePurchase purchase = new DryCoffeePurchase();
        purchase.setPurchaseDate(LocalDate.now(clock));
        // HU-15: el numero lo toma el servidor dentro de esta transaccion (Q7: compartido seco/verde/pasilla).
        purchase.setInvoiceNumber(invoiceConsecutiveService.take(context.agency().getId(), context.controlRecord()));
        purchase.setAgency(context.agency());
        purchase.setFund(context.fund());
        purchase.setSpecialType(request.specialType());
        purchase.setProductCode(context.productCode());
        purchase.setAnnouncementNumber(announcement.announcementNumber());
        purchase.setAnnouncementDate(announcement.announcementDate());
        purchase.setBasePriceLoad(calculation.basePriceLoad());
        purchase.setIdNumber(request.idNumber());
        purchase.setFirstName(seller.firstName());
        purchase.setLastName(seller.lastName());
        purchase.setGrowerType(seller.growerType());
        purchase.setAddress(seller.address());
        purchase.setCellphone(seller.cellphone());
        purchase.setBagsCount(request.bagsCount());
        purchase.setGrossKg(request.grossKg());
        purchase.setTareKg(request.tareKg());
        purchase.setNetKg(calculation.netKg());
        purchase.setTotalStoredWeight(request.totalStoredWeight());
        purchase.setWastePercentage(calculation.wastePercentage());
        purchase.setDefectiveStoredWeight(request.defectiveStoredWeight());
        purchase.setDefectivePercentage(calculation.defectivePercentage());
        purchase.setHealthyStoredWeight(calculation.healthyStoredWeight());
        purchase.setHealthyPercentage(calculation.healthyPercentage());
        purchase.setHealthyUnitPrice(announcement.healthyUnitPrice());
        purchase.setDefectiveUnitPrice(announcement.defectiveUnitPrice());
        purchase.setBonus(announcement.bonus());
        purchase.setPenalty(PenaltyRule.normalize(request.penalty()));
        purchase.setCosts(announcement.costs());
        purchase.setUnitPrice(calculation.unitPrice());
        purchase.setGrossValue(calculation.grossValue());
        purchase.setInventoryValue(calculation.inventoryValue());
        purchase.setAssociateContribution(calculation.associateContribution());
        purchase.setCooperativeDiscount(calculation.cooperativeDiscount());
        purchase.setWithholdingExempt(seller.association());
        purchase.setWithholding(calculation.withholding());
        purchase.setFreightDiscount(request.freightDiscount());
        purchase.setOtherDiscounts(request.otherDiscounts());
        purchase.setNetToPay(calculation.netToPay());
        PurchasePaymentService.ResolvedPayment payment =
                purchasePaymentService.validate(request.payment(), calculation.netToPay());
        purchase.setPaymentMethod(payment.paymentMethod());
        purchase.setCheckNumber(payment.checkNumber());
        purchase.setCreatedByUserId(currentUserId);
        purchase.setCreatedAt(Instant.now());
        purchase.setFuturePurchaseId(future == null ? null : future.getId());

        DryCoffeePurchase savedPurchase = dryCoffeePurchaseRepository.save(purchase);
        purchasePaymentService.record(
                "DRY", savedPurchase.getId(), savedPurchase.getInvoiceNumber(), request.payment(), context);

        inventoryMovementService.recordFromPurchase(
                InventoryMovement.PurchaseModule.DRY_COFFEE, savedPurchase.getId(),
                context.agency().getId(), context.productCode().getId(), purchase.getSpecialType(),
                purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(), purchase.getBagsCount(), purchase.getGrossKg(), purchase.getNetKg(),
                purchase.getHealthyPercentage(), purchase.getInventoryValue());

        return mapper.toResponse(savedPurchase, context.controlRecord());
    }

    @Override
    public DryCoffeePurchaseResponse findById(Long id) {
        // Solo de la agencia de trabajo (RF-05.1): la de otra agencia es "no encontrada", como Consultar Compras.
        DryCoffeePurchase purchase = dryCoffeePurchaseRepository.findById(id)
                .filter(p -> p.getAgency().getId().equals(currentAgencyProvider.currentAgencyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Compra de cafe seco no encontrada"));
        ControlRecord controlRecord = controlRecordService.getActive(purchase.getAgency().getId());
        return mapper.toResponse(purchase, controlRecord);
    }

    @Override
    public DryCoffeePurchaseCalculation preview(DryCoffeePurchaseRequest request) {
        requireDelivery(request);
        FuturePurchase future = request.futurePurchaseId() == null
                ? null
                : futurePurchaseService.forBilling(request.futurePurchaseId(), request.idNumber(), false);
        DryCoffeePurchaseCalculation calculation = runCalculation(request, purchaseContextService.forPreview(
                request.fundId(), request.specialType(), chosenAnnouncement(request, future), request.idNumber(),
                request.address(), request.cellphone()));
        // Avisa antes de Imprimir; al guardar se vuelve a revisar con el saldo bloqueado.
        if (future != null) {
            DeliveryRule.check(future.getRemainingKg(), request.deliveryKg(), calculation.netKg(), "de la compra anunciada");
        }
        if (request.deliveryKg() != null && calculation.netKg().compareTo(request.deliveryKg()) > 0) {
            throw new BusinessRuleException("Los kilos netos (" + calculation.netKg().toPlainString()
                    + ") superan Esta Entrega (" + request.deliveryKg().toPlainString() + ")");
        }
        return calculation;
    }

    /** RN-21.2.5 y RN-21.5.3: con anuncio con cupo o compra anunciada, Esta Entrega es obligatoria. */
    private static void requireDelivery(DryCoffeePurchaseRequest request) {
        if (request.quotaAnnouncementNumber() != null && request.futurePurchaseId() != null) {
            throw new BusinessRuleException("Una compra no puede ser con cupo y anunciada a la vez");
        }
        boolean needsDelivery = request.quotaAnnouncementNumber() != null || request.futurePurchaseId() != null;
        if (needsDelivery && request.deliveryKg() == null) {
            throw new BusinessRuleException("Digite Esta Entrega");
        }
    }

    /** El anuncio elegido: el de la compra anunciada (con su especial), el anuncio con cupo o null (el vigente). */
    private AnnouncementResponse chosenAnnouncement(DryCoffeePurchaseRequest request, FuturePurchase future) {
        if (future != null) {
            if (!future.getSpecialType().equals(request.specialType())) {
                throw new BusinessRuleException("El especial no es el de la compra anunciada (" + future.getSpecialType() + ")");
            }
            return futurePurchaseService.billingAnnouncement(future, request.fundId());
        }
        if (request.quotaAnnouncementNumber() == null) {
            return null;
        }
        return announcementService.findForPurchase(currentAgencyProvider.currentAgencyId(), request.fundId(),
                request.specialType(), request.quotaAnnouncementNumber());
    }

    /** Corre la cascada con el ControlRecord ya resuelto por el caller (evita pedirlo dos veces). */
    private DryCoffeePurchaseCalculation runCalculation(DryCoffeePurchaseRequest request, PurchaseContext context) {
        // Q8: en los cafes de programa la cedula tiene que estar en su programa NESS.
        growerService.requireProgramToInvoice(request.idNumber(), request.specialType());
        // Q10: lo que el fiel ya vendio hoy en seco + verde + pasilla, en todas las agencias.
        DailyGrowerTotals dailyTotals = context.dailyTotals();

        DryCoffeePurchaseCalculation calculation = calculator.calculate(
                request,
                context.controlRecord(),
                context.announcement(),
                context.seller(),
                dailyTotals.grossValue(),
                dailyTotals.withholding());
        // Q15: en los NESPRESSO el Factor no puede superar el umbral del Registro de Control.
        SpecialtyThresholdRule.check(context.productCode().isAppliesSpecialtyThreshold(),
                calculation.healthyPercentage(), context.controlRecord().getSpecialtyThreshold());
        // Item C (cupo, Form_COMPRAS.bas lineas 412/465/518/571/624) - ver GrowerService.checkQuota.
        growerService.checkQuota(request.idNumber(), request.specialType(), calculation.netKg());
        return calculation;
    }

    @Override
    public NextInvoiceNumberResponse nextInvoiceNumber() {
        Long agencyId = currentAgencyProvider.currentAgencyId();
        return invoiceConsecutiveService.next(agencyId, controlRecordService.getActive(agencyId));
    }

    @Override
    public SpecialInfoResponse specialInfo(
            Long agencyId, Long fundId, String specialType, Integer announcementNumber, Long futurePurchaseId) {
        ProductCode productCode = productCodeResolver.resolve(specialType, fundId);
        AnnouncementResponse announcement = futurePurchaseId != null
                ? futurePurchaseService.billingAnnouncement(futurePurchaseService.forBilling(futurePurchaseId, null, false), fundId)
                : announcementService.findForPurchase(agencyId, fundId, specialType, announcementNumber);
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
            BigDecimal totalStoredWeight, BigDecimal defectiveStoredWeight, Long fundId, String specialType) {
        ControlRecord controlRecord = controlRecordService.getActive(currentAgencyProvider.currentAgencyId());
        // Q2: con los dos pesos digitados, Peso Alm Sana y Factor salen del servidor.
        BigDecimal healthyStoredWeight = totalStoredWeight == null || defectiveStoredWeight == null
                ? null
                : calculator.healthyStoredWeight(totalStoredWeight, defectiveStoredWeight);
        BigDecimal factor = healthyStoredWeight == null ? null : calculator.healthyPercentage(healthyStoredWeight, controlRecord);
        if (factor != null && fundId != null && specialType != null) {
            // Q15: el aviso sale apenas se conoce el Factor, no despues de digitar sacos y kilos.
            SpecialtyThresholdRule.check(productCodeResolver.resolve(specialType, fundId).isAppliesSpecialtyThreshold(),
                    factor, controlRecord.getSpecialtyThreshold());
        }
        return new QualityPercentagesResponse(
                totalStoredWeight == null ? null : calculator.wastePercentage(totalStoredWeight, controlRecord),
                defectiveStoredWeight == null
                        ? null
                        : calculator.defectivePercentage(defectiveStoredWeight, controlRecord),
                healthyStoredWeight,
                factor);
    }
}
