package com.cafeoccidente.backend.purchases.drycoffee.dto;

/**
 * Factura reservada al confirmar "Fondo". {@code warning} viene con el mismo mensaje que el VBA
 * mostraba en Cedula_LostFocus cuando la resolucion esta vencida o a punto de agotarse; null si no
 * aplica.
 */
public record NextInvoiceNumberResponse(Integer invoiceNumber, String prefix, String warning) {
}
