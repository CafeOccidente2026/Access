package com.cafeoccidente.backend.purchases.annulment.service;

import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import java.util.List;

/** "Anular Documento" (Cns_ComprasParaAnular + macro AnularFactura). agencyId ya resuelto por rol: null = todas. */
public interface PurchaseAnnulmentService {

    List<AnnulmentCandidate> findByInvoice(Long agencyId, Integer invoiceNumber);

    /** Rechaza si ya se exporto, si ya estaba anulada o si su inventario tuvo salidas. */
    AnnulmentCandidate annul(Long agencyId, AnnulmentModule module, Long id);
}
