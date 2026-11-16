package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import java.math.BigDecimal;

/**
 * Hallazgo #5 (prueba del 16-nov): con Destare mayor o igual que Kilos Brutos los Kilos Netos quedan en 0 o negativos;
 * Access lo dejaba pasar (Vr. Bruto 0 o negativo). Aplica a Seco, Verde, Pasilla, Anunciadas y Cupos.
 */
public final class NetWeightRule {

    public static final String MESSAGE = "El destare no puede ser mayor o igual que los Kilos Brutos";

    private NetWeightRule() {
    }

    public static void check(BigDecimal grossKg, BigDecimal tareKg) {
        if (tareKg.compareTo(grossKg) >= 0) {
            throw new BusinessRuleException(MESSAGE);
        }
    }
}
