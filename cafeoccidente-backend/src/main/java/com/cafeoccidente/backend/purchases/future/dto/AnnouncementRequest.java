package com.cafeoccidente.backend.purchases.future.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * "Actualizar Anuncio con Factor" (RN-08.2): el Fondo y el Pr Base Carg Perg Seco del dia. El precio aplica
 * a todos los tipos de cafe del fondo (RN-08.3); la bonificacion y el precio de pasilla de cada tipo los
 * conserva el servidor (RN-08.4).
 */
public record AnnouncementRequest(@NotNull Long fundId, @NotNull @Positive BigDecimal basePriceLoad) {
}
