package com.cafeoccidente.backend.supplies.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Factura 43261 de El Tambo: FPef 497.837 + FPch 4.000.000 (cheque 5253) = neto 4.497.837. */
class PurchasePaymentTest {

    private final PurchasePayment purchase = new PurchasePayment("DRY", 7L, 43261, 4L, "El Tambo", "LF",
            LocalDate.of(2026, 5, 22), "87304051", new BigDecimal("4497837"), "CHEQUE", 5253, null);

    @Test
    void mixedPaymentBecomesOneRowPerMethodWithAmountLikeActualizaCajaAndCh() {
        var parts = purchase.splitInto(new BigDecimal("497837"), new BigDecimal("4000000"), BigDecimal.ZERO,
                BigDecimal.ZERO, 5253);

        assertThat(parts).extracting(PurchasePayment::paymentMethod).containsExactly("EFECTIVO", "CHEQUE");
        assertThat(parts).extracting(PurchasePayment::netToPay)
                .containsExactly(new BigDecimal("497837"), new BigDecimal("4000000"));
        assertThat(parts).extracting(PurchasePayment::checkNumber).containsExactly(null, 5253);
        // Misma compra: misma clave para la marca de Relacion Cheques.
        assertThat(parts).extracting(PurchasePayment::id).containsOnly(7L);
    }

    @Test
    void transferAndCardPartsKeepTheirAccessNames() {
        assertThat(purchase.splitInto(BigDecimal.ZERO, null, new BigDecimal("1"), new BigDecimal("2"), null))
                .extracting(PurchasePayment::paymentMethod).containsExactly("TRANSFERENCIA", "DATAFONO");
    }
}
