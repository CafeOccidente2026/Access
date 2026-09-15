package com.cafeoccidente.backend.purchases.greencoffee.repository;

import java.math.BigDecimal;

/** Acumulado mensual de un caficultor (por cedula), propio de VERDES (macro CalculoReteFteMesVerdes -
 *  Texto105/Texto107 - separado del acumulado de Cafe Seco, cada modulo tiene su propio reporte). */
public record MonthlyGrowerTotals(BigDecimal grossValue, BigDecimal withholding) {
}
