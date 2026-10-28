package com.cafeoccidente.backend.purchases.annulment.service;

import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentDirection;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import java.util.List;

/** "Anular Documento" (Cns_ComprasParaAnular + macro AnularFactura). agencyId ya resuelto por rol: null = todas. */
public interface PurchaseAnnulmentService {

    List<AnnulmentCandidate> findByInvoice(Long agencyId, Integer invoiceNumber);

    /** RN-23.2: las compras de la factura vecina de "from"; vacio si no hay mas en esa direccion. */
    List<AnnulmentCandidate> navigate(Long agencyId, Integer from, AnnulmentDirection direction);

    /**
     * Rechaza si ya estaba anulada, si es una compra cerrada en Access, si su inventario tuvo salidas o si su
     * cuenta por cobrar ya no esta PENDIENTE (Q35).
     */
    AnnulmentCandidate annul(Long agencyId, AnnulmentModule module, Long id);
}
