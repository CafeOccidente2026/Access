package com.cafeoccidente.backend.purchases.drycoffee.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import com.cafeoccidente.backend.purchases.drycoffee.mapper.DryCoffeePurchaseMapper;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
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
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
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
    private final UserRepository userRepository;
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
            UserRepository userRepository,
            SecurityUtils securityUtils) {
        this.dryCoffeePurchaseRepository = dryCoffeePurchaseRepository;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.productCodeResolver = productCodeResolver;
        this.announcementService = announcementService;
        this.controlRecordService = controlRecordService;
        this.calculator = calculator;
        this.mapper = mapper;
        this.userRepository = userRepository;
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
        AnnouncementResponse announcement = announcementService.findLatest(agency.getId(), fund.getId());
        ControlRecord controlRecord = controlRecordService.getActive();

        DryCoffeePurchaseCalculation calculation = calculator.calculate(request, controlRecord);

        Long currentUserId = securityUtils.getCurrentUserId();
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));

        DryCoffeePurchase purchase = new DryCoffeePurchase();
        purchase.setPurchaseDate(LocalDate.now());
        purchase.setAgency(agency);
        purchase.setFund(fund);
        purchase.setSpecialType(request.specialType());
        purchase.setProductCode(productCode);
        purchase.setAnnouncementNumber(announcement.announcementNumber());
        purchase.setAnnouncementDate(announcement.announcementDate());
        purchase.setBasePriceLoad(announcement.basePriceLoad());
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
        purchase.setDefectiveUnitPrice(request.defectiveUnitPrice());
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
        purchase.setMunicipality(currentUser.getMunicipality());
        purchase.setCreatedAt(Instant.now());

        return mapper.toResponse(dryCoffeePurchaseRepository.save(purchase));
    }

    @Override
    public DryCoffeePurchaseResponse findById(Long id) {
        return dryCoffeePurchaseRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Compra de cafe seco no encontrada"));
    }
}
