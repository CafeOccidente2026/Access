package com.cafeoccidente.backend.controlrecord.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import java.math.BigDecimal;

/**
 * Porcentaje Rete Fuente del Registro de Control (hallazgo del 18-nov): Access guarda 0,5 y divide entre 100, asi que un
 * 5 por 0,5 retiene diez veces mas. El tope lo confirma el area.
 */
public final class WithholdingPercentageRule {

    /** Tope del Porcentaje Rete Fuente; se cambia aqui. */
    public static final BigDecimal MAX_PERCENTAGE = new BigDecimal("3");

    private WithholdingPercentageRule() {
    }

    public static void check(BigDecimal percentage) {
        if (percentage.signum() < 0 || percentage.compareTo(MAX_PERCENTAGE) > 0) {
            throw new BusinessRuleException("El porcentaje de retención en la fuente debe estar entre 0 y "
                    + MAX_PERCENTAGE.toPlainString() + " % (ejemplo: 0,5)");
        }
    }
}
