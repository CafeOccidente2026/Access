package com.cafeoccidente.backend.inventory.repository;

import java.math.BigDecimal;

/**
 * Sumas de un Cod_Prod para la consulta Sld2 de Access: kilos, valor y kilos x factor (KXFC en las
 * entradas, KXFrSal en las salidas). Sin filas, SUM devuelve null: se toma como 0 (Nz de Access).
 */
public record CodeTotals(BigDecimal kg, BigDecimal value, BigDecimal kgFactor) {

    public CodeTotals {
        kg = kg == null ? BigDecimal.ZERO : kg;
        value = value == null ? BigDecimal.ZERO : value;
        kgFactor = kgFactor == null ? BigDecimal.ZERO : kgFactor;
    }
}
