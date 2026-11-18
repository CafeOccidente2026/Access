package com.cafeoccidente.backend.supplies.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alta de Caja / Caja Menor / Suministros. El Id de transaccion, la agencia y la fecha los pone el servidor (RF §2.1,
 * D2, D8); un transactionId que mande el cliente se ignora. entryDate solo vale en Ingresar Suministros (Q16); vacio es
 * hoy. idNumber no se usa en Ingresar Suministros Caja Menor: sale del Fiel del Registro de Control. checkNumber: los
 * ultimos 4 digitos (RN-27.1.4), lo valida el servicio.
 */
public record LedgerEntryRequest(
        String fundCode,
        LocalDate entryDate,
        @Pattern(regexp = "[0-9]{1,20}") String idNumber,
        @Size(max = 255) String detail,
        @NotNull @Positive BigDecimal amount,
        String paymentMethod,
        String checkNumber) {
}
