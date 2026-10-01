package com.cafeoccidente.backend.vendors.dto;

import java.math.BigDecimal;

/** Consulta "NESSYRAINSALDO": CEDULA, NOMBRES, programa, cupo, FACTURADOS y SALDO = cupo - FACTURADOS. */
public record NessQuotaBalanceRow(
        String idNumber, String names, String program, BigDecimal quota, BigDecimal invoicedKg, BigDecimal balance) {
}
