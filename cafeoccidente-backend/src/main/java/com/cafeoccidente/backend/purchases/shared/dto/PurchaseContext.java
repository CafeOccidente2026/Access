package com.cafeoccidente.backend.purchases.shared.dto;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;

/**
 * Lo que toda compra (seco, verde, pasilla) resuelve en el servidor antes de calcular: agencia de
 * trabajo del token, fondo, codigo de producto, anuncio vigente, Registro de Control y vendedor.
 */
public record PurchaseContext(
        Agency agency,
        Fund fund,
        ProductCode productCode,
        AnnouncementResponse announcement,
        ControlRecord controlRecord,
        PurchaseSeller seller) {
}
