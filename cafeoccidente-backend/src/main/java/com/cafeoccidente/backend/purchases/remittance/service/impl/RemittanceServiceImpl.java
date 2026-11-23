package com.cafeoccidente.backend.purchases.remittance.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceFiles;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceResponse;
import com.cafeoccidente.backend.purchases.remittance.repository.RemittanceRepository;
import com.cafeoccidente.backend.purchases.remittance.repository.RemittanceRepository.LockedPurchase;
import com.cafeoccidente.backend.purchases.remittance.service.RemittanceService;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remesas (RF-11, RF-12). Desde el 23-nov cada remesa la genera su remision (Registrar Salidas); aca quedan la
 * busqueda por numero, las filas de los 4 archivos y "Desmarca Remesa" para las remesas viejas sin remision (las de
 * Access y las de Genera Remesa). Una factura solo puede estar en una remesa vigente.
 */
@Service
public class RemittanceServiceImpl implements RemittanceService {

    public static final Set<String> PACKAGING_TYPES = Set.of("NUEVO", "USADO");
    static final String VIGENTE = "VIGENTE";

    private final RemittanceRepository repository;
    private final CurrentAgencyProvider currentAgencyProvider;
    private final SecurityUtils securityUtils;
    private final ChangeHistoryService history;

    public RemittanceServiceImpl(RemittanceRepository repository, CurrentAgencyProvider currentAgencyProvider,
            SecurityUtils securityUtils, ChangeHistoryService history) {
        this.repository = repository;
        this.currentAgencyProvider = currentAgencyProvider;
        this.securityUtils = securityUtils;
        this.history = history;
    }

    /** RN-11.2 y RN-11.6: solo facturas de la agencia, del fondo y del Especial elegidos, validas y sin remesa. */
    public static void check(List<LockedPurchase> locked, List<Long> ids, Long agencyId, Fund fund, String specialType) {
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

    /** Solo las remesas sin remision: las que la tienen se deshacen con Anular Remision (demo del 23-nov). */
    @Override
    @Transactional
    public RemittanceResponse unmark(Long id) {
        Long agencyId = agency();
        RemittanceResponse remittance = repository.lockRemittance(agencyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe esa remesa en la agencia"));
        if (remittance.fromRemission()) {
            throw new BusinessRuleException("La remesa " + remittance.number()
                    + " salió con una remisión; se deshace con Anular Remisión");
        }
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
}
