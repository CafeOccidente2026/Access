package com.cafeoccidente.backend.purchases.othercoffee.repository;

import java.math.BigDecimal;

/** Acumulado mensual de un caficultor en ESTE modulo (CalculoReteFteMesEsp/ReteMesCursoEsp). */
public record MonthlyGrowerTotals(BigDecimal grossValue, BigDecimal withholding) {
}
