package com.cafeoccidente.backend.purchases.shared.dto;

import java.math.BigDecimal;

/** Vr. Bruto y Retefuente ya registrados hoy para una cedula (Texto105/Texto107 en el VBA). */
public record DailyGrowerTotals(BigDecimal grossValue, BigDecimal withholding) {
}
