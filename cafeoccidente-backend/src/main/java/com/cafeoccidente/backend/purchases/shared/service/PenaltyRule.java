package com.cafeoccidente.backend.purchases.shared.service;

import java.math.BigDecimal;

/**
 * Castigo (Q1, Q18, RN-14.7, RN-19.10): solo cero o negativo y sin tope. Si se digita un positivo
 * (23) se registra como negativo (-23); se suma al Pr Sustentacion, que es restarlo.
 */
public final class PenaltyRule {

    private PenaltyRule() {
    }

    public static BigDecimal normalize(BigDecimal typed) {
        return typed == null ? BigDecimal.ZERO : typed.abs().negate();
    }
}
