package com.cafeoccidente.backend.common.util;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import java.math.BigDecimal;

/**
 * Piso de sanidad para valores monetarios calculados en las cascadas de compra. No existe en el
 * VBA legado (Access no bloqueaba negativos) - es una regla nueva: un valor negativo en cualquier
 * punto de la cascada (Precio Base Carga, Pr Sustentacion, Vr. Kilo, Vr. Bruto, Neto a Pagar, etc.)
 * es siempre señal de un dato mal configurado (anuncio o ControlRecord de la agencia), nunca un
 * resultado de negocio valido, y no debe dejarse "arrastrar" hasta un resultado final.
 */
public class MoneyValidation {

    private MoneyValidation() {
    }

    public static void requireNonNegative(BigDecimal value, String fieldLabel) {
        if (value.signum() < 0) {
            throw new BusinessRuleException(
                    "\"" + fieldLabel + "\" dio un valor negativo (" + value
                            + "). Revise el anuncio vigente o el ControlRecord de la agencia antes de continuar.");
        }
    }
}
