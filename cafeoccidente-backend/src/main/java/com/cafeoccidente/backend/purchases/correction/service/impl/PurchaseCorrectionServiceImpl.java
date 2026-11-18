package com.cafeoccidente.backend.purchases.correction.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import com.cafeoccidente.backend.purchases.correction.dto.CorrectedPurchase;
import com.cafeoccidente.backend.purchases.correction.dto.PurchaseCorrection;
import com.cafeoccidente.backend.purchases.correction.repository.PurchaseCorrectionRepository;
import com.cafeoccidente.backend.purchases.correction.service.PurchaseCorrectionService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.shared.dto.DailyGrowerTotals;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseContext;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerDailyTotalsRepository;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PurchaseCorrectionServiceImpl implements PurchaseCorrectionService {

    /** Compra cerrada en Access (alli Exportado = Si): tampoco se corrige. */
    static final String CORRECTION_LOCKED = "IMPOSIBLE CORREGIR: ES UNA COMPRA CERRADA EN EL SISTEMA ANTERIOR.";
    static final String OVER_FUTURE_PURCHASE = "Los kilos netos superan el saldo de la compra anunciada";

    private final PurchaseAnnulmentService annulmentService;
    private final PurchaseCorrectionRepository repository;
    private final CurrentAgencyProvider currentAgencyProvider;
    private final SecurityUtils securityUtils;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final ProductCodeResolver productCodeResolver;
    private final AnnouncementService announcementService;
    private final AnnouncementQuotaService announcementQuotaService;
    private final ControlRecordService controlRecordService;
    private final GrowerDailyTotalsRepository dailyTotals;
    private final ChangeHistoryService history;

    public PurchaseCorrectionServiceImpl(PurchaseAnnulmentService annulmentService, PurchaseCorrectionRepository repository,
            CurrentAgencyProvider currentAgencyProvider, SecurityUtils securityUtils, AgencyRepository agencyRepository,
            FundRepository fundRepository, ProductCodeResolver productCodeResolver, AnnouncementService announcementService,
            AnnouncementQuotaService announcementQuotaService, ControlRecordService controlRecordService,
            GrowerDailyTotalsRepository dailyTotals, ChangeHistoryService history) {
        this.annulmentService = annulmentService;
        this.repository = repository;
        this.currentAgencyProvider = currentAgencyProvider;
        this.securityUtils = securityUtils;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.productCodeResolver = productCodeResolver;
        this.announcementService = announcementService;
        this.announcementQuotaService = announcementQuotaService;
        this.controlRecordService = controlRecordService;
        this.dailyTotals = dailyTotals;
        this.history = history;
    }

    @Override
    public PurchaseCorrection start(AnnulmentModule module, Long id, String address, String cellphone) {
        Long agencyId = currentAgencyProvider.currentAgencyId();
        annulmentService.lockChangeable(agencyId, module, id, CORRECTION_LOCKED);
        CorrectedPurchase purchase = repository.find(module, id);

        dailyTotals.lockGrower(purchase.idNumber());
        DailyGrowerTotals day = dailyTotals.sum(purchase.idNumber(), purchase.purchaseDate());
        DailyGrowerTotals others = new DailyGrowerTotals(day.grossValue().subtract(purchase.grossValue()),
                day.withholding().subtract(purchase.withholding()));
        PurchaseSeller seller = new PurchaseSeller(purchase.idNumber(), purchase.firstName(), purchase.lastName(),
                purchase.growerType(), orStored(address, purchase.address()), orStored(cellphone, purchase.cellphone()),
                purchase.withholdingExempt());
        PurchaseContext context = new PurchaseContext(
                agencyRepository.findById(agencyId).orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada")),
                fundRepository.findById(purchase.fundId()).orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado")),
                productCodeResolver.resolve(purchase.specialType(), purchase.fundId()),
                originalAnnouncement(agencyId, purchase),
                controlRecordService.getActive(agencyId),
                seller,
                others);
        return new PurchaseCorrection(module, id, purchase, context, repository.trace(module, id));
    }

    @Override
    public void settle(PurchaseCorrection correction, BigDecimal netKg, PurchasePaymentRequest payment) {
        CorrectedPurchase purchase = correction.purchase();
        if (correction.module() == AnnulmentModule.DRY) {
            BigDecimal extraKg = netKg.subtract(purchase.netKg());
            // El cupo se suma de las compras guardadas (esta, aun con los kilos viejos): solo se revisa lo que crece.
            if (extraKg.signum() > 0) {
                announcementQuotaService.reserve(purchase.agencyId(), purchase.announcementNumber(), extraKg, null);
            }
            if (!repository.adjustFuturePurchase(correction.id(), purchase.netKg(), netKg)) {
                throw new BusinessRuleException(OVER_FUTURE_PURCHASE);
            }
        }
        repository.replacePayment(correction.module(), correction.id(), payment);
        repository.replaceReceivable(correction.module(), correction.id(), purchase,
                correction.context().controlRecord().getTrustedId(), payment.cardAmount());
        repository.deleteInventory(correction.module(), correction.id());
    }

    @Override
    public void close(PurchaseCorrection correction) {
        repository.markCorrected(correction.module(), correction.id(), securityUtils.getCurrentUserId());
        history.record(ChangeHistoryService.Entity.COMPRA, correction.purchase().invoiceNumber().longValue(),
                ChangeHistoryService.Action.CORREGIR, correction.purchase().agencyId(), correction.before(),
                repository.trace(correction.module(), correction.id()));
    }

    /** El anuncio con el que se facturo (su numero va despues del ultimo guion), con los precios de ese dia. */
    private AnnouncementResponse originalAnnouncement(Long agencyId, CorrectedPurchase purchase) {
        String number = purchase.announcementNumber();
        return announcementService.findForPurchase(agencyId, purchase.fundId(), purchase.specialType(),
                Integer.valueOf(number.substring(number.lastIndexOf('-') + 1)));
    }

    private static String orStored(String typed, String stored) {
        return typed == null || typed.isBlank() ? stored : typed.trim();
    }
}
