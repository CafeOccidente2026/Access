package com.cafeoccidente.backend.purchases.future.dto;

import java.math.BigDecimal;

/**
 * Anuncio de la lista de Asignar Cupo (RN-21.1.5 y RN-21.1.6): sus datos y los Kilos Entregados, que
 * son la suma de los kilos netos comprados con ese anuncio.
 */
public record QuotaCandidateResponse(AnnouncementResponse announcement, BigDecimal deliveredKg) {
}
