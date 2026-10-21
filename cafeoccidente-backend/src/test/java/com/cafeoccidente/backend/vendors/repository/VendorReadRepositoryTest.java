package com.cafeoccidente.backend.vendors.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** purchase() concatena el nombre de tabla al SQL: solo acepta las 5 tablas de compra. */
class VendorReadRepositoryTest {

    @Test
    void acceptsAPurchaseTable() {
        assertThat(VendorReadRepository.purchase("compras_pasilla", "0", "p.porc_almendra", "p.descuento_financiero"))
                .contains("FROM compras_pasilla p");
    }

    @Test
    void rejectsATableOutsideTheList() {
        assertThatThrownBy(() -> VendorReadRepository.purchase("usuarios", "0", "p.factor", "p.descuento_financiero"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
