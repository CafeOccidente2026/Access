package com.cafeoccidente.backend.vendors.dto;

import java.math.BigDecimal;

/** Consulta "CuposNess" (tabla NESS): CEDULA, NOMBRES, programa, cupo. */
public record NessQuotaRow(String idNumber, String names, String program, BigDecimal quota) {
}
