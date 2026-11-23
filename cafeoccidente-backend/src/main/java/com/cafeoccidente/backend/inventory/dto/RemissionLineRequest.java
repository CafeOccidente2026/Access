package com.cafeoccidente.backend.inventory.dto;

import jakarta.validation.constraints.NotNull;

/** Una factura que sale (demo del 23-nov: sin salidas parciales, sale todo su saldo con sus sacos y kilos brutos;
 *  el servidor los toma de la entrada de inventario). */
public record RemissionLineRequest(@NotNull Long inventoryMovementId) {
}
