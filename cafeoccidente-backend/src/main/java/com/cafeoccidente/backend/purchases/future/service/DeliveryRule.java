package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import java.math.BigDecimal;

/**
 * "Esta Entrega" contra un saldo (cupo del anuncio, RF-21.2, o compra anunciada, RF-21.5): la entrega no puede pasar el
 * saldo y los kilos netos de la compra no pueden pasar la entrega ni el saldo. Sin entrega solo se mira el saldo.
 */
public final class DeliveryRule {

    private DeliveryRule() {
    }

    /** {@code of} dice de que es el saldo, p. ej. "del cupo del anuncio 7" o "de la compra anunciada". */
    public static void check(BigDecimal balance, BigDecimal deliveryKg, BigDecimal netKg, String of) {
        if (deliveryKg != null && deliveryKg.compareTo(balance) > 0) {
            throw new BusinessRuleException("Esta Entrega (" + deliveryKg.toPlainString() + ") supera el saldo " + of
                    + " (" + balance.toPlainString() + ")");
        }
        if (deliveryKg != null && netKg.compareTo(deliveryKg) > 0) {
            throw new BusinessRuleException("Los kilos netos (" + netKg.toPlainString() + ") superan Esta Entrega ("
                    + deliveryKg.toPlainString() + ")");
        }
        if (netKg.compareTo(balance) > 0) {
            throw new BusinessRuleException("Los kilos netos (" + netKg.toPlainString() + ") superan el saldo " + of
                    + " (" + balance.toPlainString() + ")");
        }
    }
}
