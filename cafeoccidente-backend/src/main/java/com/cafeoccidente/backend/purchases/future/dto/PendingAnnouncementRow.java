package com.cafeoccidente.backend.purchases.future.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila de "Anunciadas Hoy" (HU-31, P2): un anuncio con las compras a futuro que todavia tienen saldo. Kilos y saldo son
 * la suma de esas compras; growers, cuantas son.
 */
public record PendingAnnouncementRow(
        String announcementNumber,
        LocalDate announcementDate,
        String specialType,
        BigDecimal healthyUnitPrice,
        BigDecimal announcedKg,
        BigDecimal remainingKg,
        long growers) {
}
