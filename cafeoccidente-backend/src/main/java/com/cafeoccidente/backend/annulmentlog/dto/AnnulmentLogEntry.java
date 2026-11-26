package com.cafeoccidente.backend.annulmentlog.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Una fila del registro de anulaciones. Las de Access (origin ACCESS) no tienen fecha, usuario ni motivo: Access no
 * los guarda. values: la foto del documento al anularlo (kilos, valores, forma de pago, caficultor; en una remision
 * kilos, valor de salida, destino, conductor, placa, fondo y facturas). purchaseDate: la fecha de la compra anulada
 * (null en una remision).
 */
public record AnnulmentLogEntry(
        Long id,
        String origin,
        String documentType,
        String documentNumber,
        String agencyName,
        Long documentId,
        OffsetDateTime annulledAt,
        LocalDate purchaseDate,
        String username,
        String reason,
        Map<String, String> values,
        String note) {
}
