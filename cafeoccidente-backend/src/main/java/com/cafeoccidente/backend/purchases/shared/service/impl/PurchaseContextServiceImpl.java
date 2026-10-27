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
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseContextService;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseSellerService;
import org.springframework.stereotype.Service;

@Service
public class PurchaseContextServiceImpl implements PurchaseContextService {

    private final CurrentAgencyProvider currentAgencyProvider;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final ProductCodeResolver productCodeResolver;
    private final AnnouncementService announcementService;
    private final ControlRecordService controlRecordService;
    private final PurchaseSellerService purchaseSellerService;

    public PurchaseContextServiceImpl(
            CurrentAgencyProvider currentAgencyProvider,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            ProductCodeResolver productCodeResolver,
            AnnouncementService announcementService,
            ControlRecordService controlRecordService,
            PurchaseSellerService purchaseSellerService) {
        this.currentAgencyProvider = currentAgencyProvider;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.productCodeResolver = productCodeResolver;
        this.announcementService = announcementService;
        this.controlRecordService = controlRecordService;
        this.purchaseSellerService = purchaseSellerService;
    }

    @Override
    public PurchaseContext forPreview(Long fundId, String specialType, String idNumber, String address, String cellphone) {
        return context(fundId, specialType, purchaseSellerService.resolve(idNumber, address, cellphone));
    }

    @Override
    public PurchaseContext forSave(Long fundId, String specialType, String idNumber, String address, String cellphone) {
        return context(fundId, specialType, purchaseSellerService.resolveAndComplete(idNumber, address, cellphone));
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
                seller);
    }
}
