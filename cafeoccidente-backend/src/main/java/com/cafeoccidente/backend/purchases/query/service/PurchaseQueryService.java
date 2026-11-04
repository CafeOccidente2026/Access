package com.cafeoccidente.backend.purchases.query.service;

import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentDirection;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.query.dto.PurchaseQueryDetail;
import java.util.List;

/** "Consultar Compras" (RF-26) sobre la agencia de trabajo (RN-26.3): buscar, recorrer y ver el detalle. */
public interface PurchaseQueryService {

    /** RN-26.2: las compras con ese numero de factura. */
    List<AnnulmentCandidate> findByInvoice(Integer invoiceNumber);

    /** RN-26.1: las compras de la factura vecina de "from"; vacio si no hay mas. */
    List<AnnulmentCandidate> navigate(Integer from, AnnulmentDirection direction);

    /** Todos los datos de la compra; otra agencia o inexistente = no encontrada. */
    PurchaseQueryDetail detail(AnnulmentModule module, Long id);
}
