package com.cafeoccidente.backend.purchases.drycoffee.repository;

import java.math.BigDecimal;

/**
 * Acumulado mensual de un caficultor (por cedula) usado para calcular la Retefuente incremental,
 * equivalente a lo que la macro Access CalculoReteFteMes leia como Texto105/Texto107.
 */
public record MonthlyGrowerTotals(BigDecimal grossValue, BigDecimal withholding) {
}
