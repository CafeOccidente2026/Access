package com.cafeoccidente.backend.supplies.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import java.util.Arrays;

/**
 * RF §2.1: digito + codigo del Id de transaccion de cada alta (PREFIJO + codigo + consecutivo). Cada codigo lleva su
 * propio consecutivo por agencia. {@code screen} es la clave de la pantalla en supplies-entry.json; {@code value} es el
 * fondo o el tipo de empaque que elige el usuario. Ajustes Caja (AJU) no esta en el RF (decision D5, 2026-11-05).
 */
public enum TransactionCode {
    CASH_LF("cash", "LF", "1"),
    CASH_RP("cash", "RP", "2"),
    ADJUSTMENT_LF("adjustment", "LF", "1AJU"),
    ADJUSTMENT_RP("adjustment", "RP", "2AJU"),
    SUPPLY_RP("supply", "RP", "3SUM"),
    SUPPLY_LF("supply", "LF", "4SUM"),
    ISSUED_CHECK_RP("issuedCheck", "RP", "3CHQ"),
    ISSUED_CHECK_LF("issuedCheck", "LF", "4CHQ"),
    PACKAGING_USED("packaging", "USADO", "5SUM"),
    PACKAGING_NEW("packaging", "NUEVO", "6SUM"),
    LOAN_USED("packagingLoan", "USADO", "5PRE"),
    LOAN_NEW("packagingLoan", "NUEVO", "6PRE"),
    // RN-27.12.1: Caja Menor siempre es RP.
    PETTY_CASH_IN("pettyCash", "RP", "7ING"),
    PETTY_CASH_OUT("pettyCashExpense", "RP", "7GAS");

    private final String screen;
    private final String value;
    private final String code;

    TransactionCode(String screen, String value, String code) {
        this.screen = screen;
        this.value = value;
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** El de la pantalla y el fondo o tipo elegidos; en Caja Menor el fondo no se elige. */
    public static TransactionCode of(String screen, String value) {
        return Arrays.stream(values())
                .filter(c -> c.screen.equals(screen) && (c.screen.startsWith("pettyCash") || c.value.equals(value)))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("No hay Id de transacción para " + screen + " " + value));
    }
}
