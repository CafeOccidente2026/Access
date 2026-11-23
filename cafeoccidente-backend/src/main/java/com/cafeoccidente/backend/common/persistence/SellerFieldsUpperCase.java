package com.cafeoccidente.backend.common.persistence;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Demo del 23-nov: el vendedor de una compra (nombres, apellidos y direccion, que puede digitarse si el tercero no la
 * tiene) se guarda en mayusculas. Un solo listener para Seco, Verde y Pasilla, en lugar de repetir @Convert.
 */
public class SellerFieldsUpperCase {

    private static final UpperCaseConverter UPPER = new UpperCaseConverter();

    @PrePersist
    @PreUpdate
    public void upperCase(Object entity) {
        SellerFields purchase = (SellerFields) entity;
        purchase.setFirstName(UPPER.convertToDatabaseColumn(purchase.getFirstName()));
        purchase.setLastName(UPPER.convertToDatabaseColumn(purchase.getLastName()));
        purchase.setAddress(UPPER.convertToDatabaseColumn(purchase.getAddress()));
    }
}
