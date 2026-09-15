package com.cafeoccidente.backend.purchases.greencoffee.dto;

/** Ver drycoffee.dto.NextInvoiceNumberResponse - mismo mecanismo, propio de VERDES. */
public record NextInvoiceNumberResponse(Integer invoiceNumber, String prefix, String warning) {
}
