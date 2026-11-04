package com.cafeoccidente.backend.purchases.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una compra en los reportes ANUNCIO / RESUMEN ANUNCIO (RF-24) y COMPRAS POR FECHA (RF-25). Los 3
 * modulos, como en Access (todos estaban en COMPRAS). Lo que no aplica a un modulo va en null:
 * greenKg solo en verde. factor es la columna Porc: el Factor en seco y verde, el % de almendra en
 * pasilla. Una anulada trae sus valores en 0 y paymentMethod = "ANULADA", como AnularFactura.
 */
public record PurchaseReportRow(
        String module,
        String agencyName,
        String announcementNumber,
        String specialType,
        String productCode,
        String productName,
        LocalDate purchaseDate,
        String fundCode,
        String prefix,
        Integer invoiceNumber,
        String idNumber,
        String firstName,
        String lastName,
        BigDecimal greenKg,
        BigDecimal netKg,
        BigDecimal factor,
        BigDecimal unitPrice,
        BigDecimal grossValue,
        BigDecimal inventoryValue,
        BigDecimal associateContribution,
        BigDecimal cooperativeDiscount,
        BigDecimal freightDiscount,
        BigDecimal withholding,
        BigDecimal otherDiscounts,
        BigDecimal netToPay,
        String paymentMethod,
        String status) {
}
