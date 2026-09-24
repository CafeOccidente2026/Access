package com.cafeoccidente.backend.purchases.othercoffee.dto;

public record NextInvoiceNumberResponse(Integer invoiceNumber, String prefix, String warning) {
}
