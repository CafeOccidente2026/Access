package com.cafeoccidente.backend.purchases.remittance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * Genera Remesa (RN-11.1 a RN-11.9): fondo (RP o LF), Especial elegido (null = "Todos"), las facturas
 * marcadas y los empaques que salen con ella. La agencia sale de la sesion, nunca del request.
 */
public record RemittanceRequest(
        @NotBlank String fundCode,
        String specialType,
        @NotEmpty List<@Valid RemittancePurchaseRef> purchases,
        @NotNull @Positive Integer packagingQuantity,
        @NotBlank String packagingType) {
}
