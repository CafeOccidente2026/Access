package com.cafeoccidente.backend.purchases.husk.service;

import com.cafeoccidente.backend.purchases.husk.dto.AnnouncementInfoResponse;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseResponse;
import com.cafeoccidente.backend.purchases.husk.dto.NextInvoiceNumberResponse;

public interface HuskPurchaseService {
    HuskPurchaseResponse create(HuskPurchaseRequest request);

    HuskPurchaseResponse findById(Long id);

    HuskPurchaseCalculation preview(HuskPurchaseRequest request);

    /** Factura: siguiente consecutivo propio de PASILLA. */
    NextInvoiceNumberResponse nextInvoiceNumber();

    /** Cod Prod + anuncio vigente para la agencia del usuario (Fondo "RP" / Especial "PASILLA"). */
    AnnouncementInfoResponse announcementInfo();
}
