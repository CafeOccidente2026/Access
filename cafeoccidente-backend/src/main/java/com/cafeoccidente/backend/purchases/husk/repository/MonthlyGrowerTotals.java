package com.cafeoccidente.backend.purchases.husk.repository;

import java.math.BigDecimal;

/** Acumulado mensual de un caficultor (por cedula), propio de PASILLA (macro CalculoReteFteMesPas -
 *  Texto105/Texto107 - separado del acumulado de Cafe Seco y VERDES). */
public record MonthlyGrowerTotals(BigDecimal grossValue, BigDecimal withholding) {
}
