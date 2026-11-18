package com.cafeoccidente.backend.purchases.future.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila de "Restaurar Compras Anunciadas": la compra anunciada con su saldo guardado y el que sale de recalcular
 * (kilos anunciados menos los kilos netos de las compras no anuladas que la facturaron).
 */
public record FuturePurchaseRestoreRow(
        Long id,
        String announcementNumber,
        LocalDate announcementDate,
        String specialType,
        BigDecimal announcedKg,
        BigDecimal deliveredKg,
        BigDecimal currentBalance,
        BigDecimal recalculatedBalance) {
}
