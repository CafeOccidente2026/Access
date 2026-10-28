package com.cafeoccidente.backend.purchases.annulment.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentDirection;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.repository.PurchaseAnnulmentRepository;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseAnnulmentServiceImpl implements PurchaseAnnulmentService {

    /** Compra cerrada en Access (alli marcada Exportado = Si, Etiqueta98 de Cns_ComprasParaAnular). */
    static final String ANNULMENT_LOCKED = "IMPOSIBLE ANULAR: ES UNA COMPRA CERRADA EN EL SISTEMA ANTERIOR.";
    static final String ALREADY_ANNULLED = "El documento soporte ya esta anulado";
    /** Decision del usuario 2026-10-02 (P4): una remision real depende de esa entrada de inventario. */
    static final String HAS_INVENTORY_EXITS = "La compra ya tiene salidas de inventario";
    /** Q35 (pendiente del area): no hay movimiento definido para reversar una cuenta ya saldada. */
    static final String RECEIVABLE_NOT_PENDING = "La compra tiene una cuenta por cobrar ya saldada";

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

    @Override
    public List<AnnulmentCandidate> navigate(Long agencyId, Integer from, AnnulmentDirection direction) {
        return repository.neighborInvoice(agencyId, from, direction)
                .map(invoice -> repository.findByInvoice(agencyId, invoice))
                .orElse(List.of());
    }

    /**
     * Comando87_Click: no deja anular una compra cerrada en Access (anulacion_bloqueada, que en Access era
     * Exportado = Si); las compras nuevas se bloquean si ya hubo salida de inventario (Q11) o si su cuenta
     * por cobrar ya no esta PENDIENTE (Q35). Todo en una transaccion: valores en 0, formas de pago en 0,
     * inventario borrado, cuenta por cobrar ANULADA y kilos devueltos al cupo o a la compra anunciada
     * (RN-23.6; Access no los devolvia). El numero de factura no se reutiliza.
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
        if (purchase.annulmentLocked()) {
            throw new BusinessRuleException(ANNULMENT_LOCKED);
        }
        if (repository.hasInventoryExits(module, id)) {
            throw new BusinessRuleException(HAS_INVENTORY_EXITS);
        }
        if (repository.hasReceivableNotPending(module, id)) {
            throw new BusinessRuleException(RECEIVABLE_NOT_PENDING);
        }
        repository.annul(module, id, securityUtils.getCurrentUserId());
        return repository.lock(module, id).orElseThrow();
    }
}
