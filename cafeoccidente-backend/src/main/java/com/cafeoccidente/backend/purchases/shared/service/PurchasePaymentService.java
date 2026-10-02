package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import java.math.BigDecimal;

/**
 * Forma de pago de una compra nueva: valida el panel contra el Neto a Pagar y guarda el desglose en
 * purchase_payment_split, que es lo que leen Caja, Suministros, Relacion Cheques y Exportar.
 */
public interface PurchasePaymentService {

    /** Forma de pago resumida y numero de cheque que quedan en la compra. */
    record ResolvedPayment(String paymentMethod, String checkNumber) {
    }

    /** Rechaza el pago descuadrado o el cheque sin numero (NumCheque_LostFocus / Texto203_LostFocus). */
    ResolvedPayment validate(PurchasePaymentRequest payment, BigDecimal netToPay);

    /** source = clave del modulo en purchase_payment_split (DRY, GREEN, HUSK, OTHER, FERTI). */
    void record(String source, Long purchaseId, PurchasePaymentRequest payment);
}
