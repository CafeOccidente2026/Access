package com.cafeoccidente.backend.purchases.future.dto;

import java.math.BigDecimal;

/** Fila de la ventana de Facturar Anuncios con Cupos (RN-21.2.4): el anuncio con su Cupo, Kl_Entregados y Saldo. */
public record QuotaBalanceResponse(
        AnnouncementResponse announcement, BigDecimal assignedQuota, BigDecimal deliveredKg, BigDecimal balance) {
}
