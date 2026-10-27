package com.cafeoccidente.backend.purchases.shared.dto;

/**
 * Proxima factura del punto, solo para mostrarla: el numero real lo asigna el servidor al guardar.
 * {@code warning} trae el mismo mensaje que el VBA mostraba en Cedula_LostFocus cuando la
 * resolucion esta vencida o a punto de agotarse; null si no aplica.
 */
public record NextInvoiceNumberResponse(Integer invoiceNumber, String prefix, String warning) {
}
