package com.cafeoccidente.backend.purchases.query.dto;

import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import java.util.List;

/**
 * Una compra en "Consultar Compras" (Cns_Compras, RF-26).
 *
 * @param purchase la respuesta de su modulo (seco, verde o pasilla), con todos sus campos
 * @param status VALIDA o ANULADA (RN-26.5)
 * @param payment las formas de pago guardadas (FPef/FPch/FPtx/FPdat y Num Cheque); null en compras sin desglose
 * @param remissions numeros de las remisiones por las que salio su inventario
 */
public record PurchaseQueryDetail(
        AnnulmentModule module,
        Object purchase,
        String status,
        PurchasePaymentRequest payment,
        List<String> remissions) {
}
