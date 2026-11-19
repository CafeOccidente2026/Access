package com.cafeoccidente.backend.purchases.future.dto;

import java.math.BigDecimal;

/**
 * Anuncio de la lista de Asignar Cupo (RN-21.1.5 y RN-21.1.6): sus datos, los Kilos Entregados (kilos netos de todas
 * las compras validas con ese anuncio y especial, informativo) y los Kilos con Cupo (solo Facturar Anuncios con Cupos).
 */
public record QuotaCandidateResponse(AnnouncementResponse announcement, BigDecimal deliveredKg, BigDecimal quotaKg) {
}
