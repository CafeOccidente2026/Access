package com.cafeoccidente.backend.purchases.correction.dto;

import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseContext;
import java.util.Map;

/**
 * Una correccion en curso: la compra ya bloqueada, su contexto con el anuncio original y lo vendido en el dia sin
 * ella, y como estaba antes (para el Historial).
 */
public record PurchaseCorrection(
        AnnulmentModule module,
        Long id,
        CorrectedPurchase purchase,
        PurchaseContext context,
        Map<String, String> before) {
}
