package com.cafeoccidente.backend.purchases.remittance.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.inventory.repository.RemissionConsecutiveRepository;
import com.cafeoccidente.backend.inventory.service.impl.RemissionServiceImpl;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceFiles;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittancePurchase;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittancePurchaseRef;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceRequest;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceResponse;
import com.cafeoccidente.backend.purchases.remittance.repository.RemittanceRepository;
import com.cafeoccidente.backend.purchases.remittance.repository.RemittanceRepository.LockedPurchase;
import com.cafeoccidente.backend.purchases.remittance.service.RemittanceService;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera Remesa: en Access el fiel marcaba las facturas en Genera Plano (COMPRAS.Plano, COMPRAS.Remision) y
 * exportaba 4 archivos. Aca la remesa toma su numero del consecutivo que comparte con las Salidas de
 * inventario (RN-11.4, RN-11.5), marca las facturas, registra la salida de empaques y da las filas de los
 * archivos. Una factura solo puede estar en una remesa vigente.
 */
@Service
public class RemittanceServiceImpl implements RemittanceService {

    private static final Set<String> PACKAGING_TYPES = Set.of("NUEVO", "USADO");
    static final String VIGENTE = "VIGENTE";

    private final RemittanceRepository repository;
    private final RemissionConsecutiveRepository consecutives;
    private final ControlRecordService controlRecordService;
    private final FundRepository fundRepository;
    private final CurrentAgencyProvider currentAgencyProvider;
    private final SecurityUtils securityUtils;
    private final ChangeHistoryService history;

    public RemittanceServiceImpl(RemittanceRepository repository, RemissionConsecutiveRepository consecutives,
            ControlRecordService controlRecordService, FundRepository fundRepository,
            CurrentAgencyProvider currentAgencyProvider, SecurityUtils securityUtils, ChangeHistoryService history) {
        this.repository = repository;
        this.consecutives = consecutives;
        this.controlRecordService = controlRecordService;
        this.fundRepository = fundRepository;
        this.currentAgencyProvider = currentAgencyProvider;
        this.securityUtils = securityUtils;
        this.history = history;
    }

    @Override
    public List<String> specials(String fundCode) {
        return repository.specials(agency(), fundCode);
    }

    @Override
    public List<RemittancePurchase> pending(String fundCode, String specialType) {
        return repository.pending(agency(), fundCode, blankToNull(specialType));
    }

    @Override
    @Transactional
    public RemittanceResponse create(RemittanceRequest request) {
        Long agencyId = agency();
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        String packagingType = request.packagingType().trim().toUpperCase();
        if (!PACKAGING_TYPES.contains(packagingType)) {
            throw new BusinessRuleException("El tipo de empaque debe ser NUEVO o USADO");
        }
        Fund fund = fundRepository.findByCode(request.fundCode().trim().toUpperCase())
                .orElseThrow(() -> new BusinessRuleException("El fondo debe ser RP o LF"));
        String specialType = blankToNull(request.specialType());
        Map<AnnulmentModule, List<Long>> byModule = request.purchases().stream().distinct().collect(
                Collectors.groupingBy(RemittancePurchaseRef::module, Collectors.mapping(RemittancePurchaseRef::id,
                        Collectors.toList())));
        byModule.forEach((module, ids) -> check(repository.lock(module, ids), ids, agencyId, fund, specialType));

        int sequence = consecutives.take(agencyId, fund.getId());
        String number = RemissionServiceImpl.displayNumber(controlRecord.getPrefix(), fund.getCode(), sequence);
        Long id = repository.insert(agencyId, fund.getId(), sequence, number, specialType, request.packagingQuantity(),
                packagingType, securityUtils.getCurrentUserId());
        byModule.forEach((module, ids) -> repository.assign(module, ids, id));
        repository.addPackagingExit(agencyId, number, packagingType, controlRecord.getTrustedId(),
                request.packagingQuantity());
        return repository.find(agencyId, id).orElseThrow();
    }

    /** RN-11.2 y RN-11.6: solo facturas de la agencia, del fondo y del Especial elegidos, validas y sin remesa. */
    private static void check(List<LockedPurchase> locked, List<Long> ids, Long agencyId, Fund fund, String specialType) {
        if (locked.size() != ids.size() || locked.stream().anyMatch(p -> !p.agencyId().equals(agencyId))) {
            throw new ResourceNotFoundException("No existe esa compra en la agencia");
        }
        for (LockedPurchase p : locked) {
            if (!fund.getCode().equals(p.fundCode())) {
                throw new BusinessRuleException("La factura " + p.invoiceNumber() + " es del fondo " + p.fundCode());
            }
            if (specialType != null && !specialType.equals(p.specialType())) {
                throw new BusinessRuleException("La factura " + p.invoiceNumber() + " es de " + p.specialType());
            }
            if (p.remittanceId() != null || !"VALIDA".equals(p.status()) || p.netKg().signum() <= 0) {
                throw new BusinessRuleException("La factura " + p.invoiceNumber()
                        + " ya no está pendiente de remesar; vuelva a consultar");
            }
        }
    }

    @Override
    public List<RemittanceResponse> findByNumber(String number) {
        return repository.findByNumber(agency(), number);
    }

    @Override
    public RemittanceFiles files(Long id) {
        RemittanceResponse remittance = repository.find(agency(), id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe esa remesa en la agencia"));
        RemittanceRepository.Header header = repository.header(id);
        return new RemittanceFiles(remittance.number(), header.agencyName(), header.generatedAt(), repository.lines(id));
    }

    @Override
    @Transactional
    public RemittanceResponse unmark(Long id) {
        Long agencyId = agency();
        RemittanceResponse remittance = repository.lockRemittance(agencyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe esa remesa en la agencia"));
        if (!VIGENTE.equals(remittance.status())) {
            throw new BusinessRuleException("La remesa " + remittance.number() + " ya está desmarcada");
        }
        repository.unmark(agencyId, id, remittance.number(), securityUtils.getCurrentUserId());
        RemittanceResponse unmarked = repository.find(agencyId, id).orElseThrow();
        history.record(ChangeHistoryService.Entity.REMESA, id, ChangeHistoryService.Action.DESMARCAR, agencyId,
                Map.of("number", remittance.number(), "status", remittance.status(),
                        "purchases", String.valueOf(remittance.purchases())),
                Map.of("number", unmarked.number(), "status", unmarked.status(),
                        "purchases", String.valueOf(unmarked.purchases())));
        return unmarked;
    }

    /** RN-01.3: la agencia de trabajo de la sesion, nunca la del request. */
    private Long agency() {
        return currentAgencyProvider.currentAgencyId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
