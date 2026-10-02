package com.cafeoccidente.backend.purchases.annulment.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.repository.PurchaseAnnulmentRepository;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseAnnulmentServiceImpl implements PurchaseAnnulmentService {

    /** Etiqueta98 de Cns_ComprasParaAnular. */
    static final String ALREADY_EXPORTED = "IMPOSIBLE ANULAR EL DOCUMENTO SOPORTE PORQUE YA EXPORTO LA INFORMACION.";
    static final String ALREADY_ANNULLED = "El documento soporte ya esta anulado";
    /** Decision del usuario 2026-10-02 (P4): una remision real depende de esa entrada de inventario. */
    static final String HAS_INVENTORY_EXITS = "La compra ya tiene salidas de inventario";

    private final PurchaseAnnulmentRepository repository;
    private final SecurityUtils securityUtils;

    public PurchaseAnnulmentServiceImpl(PurchaseAnnulmentRepository repository, SecurityUtils securityUtils) {
        this.repository = repository;
        this.securityUtils = securityUtils;
    }

    @Override
    public List<AnnulmentCandidate> findByInvoice(Long agencyId, Integer invoiceNumber) {
        return repository.findByInvoice(agencyId, invoiceNumber);
    }

    /**
     * Comando87_Click: con Exportado = Si no deja anular. AnularFactura no devuelve el saldo de Compras
     * a Futuro ni el cupo del anuncio (P7: se replica literal).
     */
    @Override
    @Transactional
    public AnnulmentCandidate annul(Long agencyId, AnnulmentModule module, Long id) {
        AnnulmentCandidate purchase = repository.lock(module, id)
                .filter(p -> agencyId == null || p.agencyId().equals(agencyId))
                .orElseThrow(() -> new ResourceNotFoundException("No existe esa compra en la agencia"));
        if ("ANULADA".equals(purchase.status())) {
            throw new BusinessRuleException(ALREADY_ANNULLED);
        }
        if (purchase.exported()) {
            throw new BusinessRuleException(ALREADY_EXPORTED);
        }
        if (repository.hasInventoryExits(module, id)) {
            throw new BusinessRuleException(HAS_INVENTORY_EXITS);
        }
        repository.annul(module, id, securityUtils.getCurrentUserId());
        return repository.lock(module, id).orElseThrow();
    }
}
