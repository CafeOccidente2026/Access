package com.cafeoccidente.backend.purchases.remittance.dto;

import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import jakarta.validation.constraints.NotNull;

/** Una factura de la remesa: modulo (seco, verde o pasilla) e id de la compra. */
public record RemittancePurchaseRef(@NotNull AnnulmentModule module, @NotNull Long id) {
}
