package com.cafeoccidente.backend.purchases.remittance.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que necesitan los 4 archivos de la remesa (RF-12): el numero (Remision), el tipo de cafe para el nombre del
 * archivo (SECO, VERDE o PASILLA; una remesa vieja de Access que mezcla tipos lleva los dos, ej. SECO-PASILLA), la
 * agencia, la fecha y hora en que se genero (fechaOrigEntr de ParaOpen) y una fila calculada por factura.
 */
public record RemittanceFiles(String number, String coffeeType, String agencyName, LocalDateTime generatedAt,
        List<RemittanceLine> lines) {
}
