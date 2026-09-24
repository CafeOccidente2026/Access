package com.cafeoccidente.backend.purchases.future.repository;

import java.math.BigDecimal;

/** Acumulado mensual propio de FERTIFUTURO (ReteMesCursoFerti, Texto105/Texto107). */
public record FertiFuturoMonthlyTotals(BigDecimal grossValue, BigDecimal withholding) {
}
