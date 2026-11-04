package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Umbral de cafes especiales (Q15): en los especiales marcados en productos (los 4 NESPRESSO), un
 * Factor mayor que el umbral del Registro de Control bloquea la compra. La pantalla avisa, limpia
 * los pesos de la muestra y pide digitarlos de nuevo (lo reconoce por {@link #CODE}). No existia en
 * Access: solo aplica a compras nuevas.
 */
public final class SpecialtyThresholdRule {

    public static final String CODE = "UMBRAL_ESPECIALES";

    private SpecialtyThresholdRule() {
    }

    public static void check(boolean applies, BigDecimal factor, BigDecimal threshold) {
        if (applies && threshold != null && factor.compareTo(threshold) > 0) {
            DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("es", "CO")));
            throw new BusinessRuleException("El Factor (" + format.format(factor) + ") supera el umbral de cafés especiales ("
                    + format.format(threshold) + "). Digite de nuevo los pesos de la muestra.", CODE);
        }
    }
}
