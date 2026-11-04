package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseContext;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerDailyTotalsRepository;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseContextService;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseSellerService;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseContextServiceImpl implements PurchaseContextService {

    private final CurrentAgencyProvider currentAgencyProvider;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final ProductCodeResolver productCodeResolver;
    private final AnnouncementService announcementService;
    private final ControlRecordService controlRecordService;
    private final PurchaseSellerService purchaseSellerService;
    private final GrowerDailyTotalsRepository dailyTotals;

    public PurchaseContextServiceImpl(
            CurrentAgencyProvider currentAgencyProvider,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            PurchaseSellerService purchaseSellerService,
            GrowerDailyTotalsRepository dailyTotals) {
        this.currentAgencyProvider = currentAgencyProvider;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.productCodeResolver = productCodeResolver;
        this.announcementService = announcementService;
        this.controlRecordService = controlRecordService;
        this.purchaseSellerService = purchaseSellerService;
        this.dailyTotals = dailyTotals;
    }

    @Override
    public PurchaseContext forPreview(Long fundId, String specialType, String idNumber, String address, String cellphone) {
        PurchaseSeller seller = purchaseSellerService.resolve(idNumber, address, cellphone);
        return context(fundId, specialType, seller);
    }

    /** MANDATORY: el bloqueo del tercero solo sirve dentro de la transaccion del guardado. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PurchaseContext forSave(Long fundId, String specialType, String idNumber, String address, String cellphone) {
        PurchaseSeller seller = purchaseSellerService.resolveAndComplete(idNumber, address, cellphone);
        dailyTotals.lockGrower(seller.idNumber());
        return context(fundId, specialType, seller);
    }

    private PurchaseContext context(Long fundId, String specialType, PurchaseSeller seller) {
        Agency agency = agencyRepository.findById(currentAgencyProvider.currentAgencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        Fund fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
        return new PurchaseContext(
                agency,
                fund,
                productCodeResolver.resolve(specialType, fund.getId()),
                announcementService.findLatest(agency.getId(), fund.getId(), specialType),
                controlRecordService.getActive(agency.getId()),
                seller,
                dailyTotals.sum(seller.idNumber(), LocalDate.now()));
    }
}
