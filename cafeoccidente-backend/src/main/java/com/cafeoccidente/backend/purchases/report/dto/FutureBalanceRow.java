package com.cafeoccidente.backend.purchases.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una persona del informe "COMPRAS A FUTURO" (Saldo Anuncios, HU-35): K Anun = kilos anunciados, K Entr = kilos
 * entregados (anunciados menos saldo), Saldo Kg = lo que le falta por entregar.
 */
public record FutureBalanceRow(
        String agencyName,
        String announcementNumber,
        LocalDate announcementDate,
        String idNumber,
        String firstName,
        String lastName,
        BigDecimal announcedKg,
        BigDecimal deliveredKg,
        BigDecimal remainingKg,
        BigDecimal healthyUnitPrice,
        BigDecimal bonus,
        String specialType,
        LocalDate deliveryDate) {
}
