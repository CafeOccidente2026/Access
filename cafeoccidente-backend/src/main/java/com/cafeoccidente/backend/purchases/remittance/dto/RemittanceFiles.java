package com.cafeoccidente.backend.purchases.remittance.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que necesitan los 4 archivos de la remesa (RF-12): el numero (Remision), la agencia, la fecha y hora en
 * que se genero (fechaOrigEntr de ParaOpen) y una fila calculada por factura.
 */
public record RemittanceFiles(String number, String agencyName, LocalDateTime generatedAt, List<RemittanceLine> lines) {
}
