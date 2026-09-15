package com.cafeoccidente.backend.purchases.husk.dto;

/** Ver drycoffee.dto.NextInvoiceNumberResponse - mismo mecanismo, propio de PASILLA. */
public record NextInvoiceNumberResponse(Integer invoiceNumber, String prefix, String warning) {
}
