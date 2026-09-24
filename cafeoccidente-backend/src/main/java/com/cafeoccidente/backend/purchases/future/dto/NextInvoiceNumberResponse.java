package com.cafeoccidente.backend.purchases.future.dto;

public record NextInvoiceNumberResponse(Integer invoiceNumber, String prefix, String warning) {
}
