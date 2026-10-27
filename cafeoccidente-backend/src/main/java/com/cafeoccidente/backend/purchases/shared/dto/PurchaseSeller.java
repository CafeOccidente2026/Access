package com.cafeoccidente.backend.purchases.shared.dto;

/**
 * Vendedor de una compra tal como lo resuelve el servidor desde TERCEROS (Q8): el navegador solo
 * manda la cedula, y la direccion o el celular cuando el tercero no los tiene.
 */
public record PurchaseSeller(
        String idNumber,
        String firstName,
        String lastName,
        String growerType,
        String address,
        String cellphone,
        boolean association) {
}
