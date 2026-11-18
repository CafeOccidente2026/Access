package com.cafeoccidente.backend.purchases.shared.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Hallazgo #5 (prueba del 16-nov): Kilos Netos en 0 o negativos no se facturan. */
class NetWeightRuleTest {

    @Test
    void aTareEqualOrGreaterThanTheGrossKilosIsRejected() {
        for (String tare : new String[] {"2", "3"}) {
            assertThatThrownBy(() -> NetWeightRule.check(new BigDecimal("2"), new BigDecimal(tare)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(NetWeightRule.MESSAGE);
        }
        assertThatThrownBy(() -> NetWeightRule.check(BigDecimal.ZERO, BigDecimal.ZERO)).hasMessage(NetWeightRule.MESSAGE);
    }

    @Test
    void aTareSmallerThanTheGrossKilosIsAccepted() {
        assertThatCode(() -> NetWeightRule.check(new BigDecimal("2"), new BigDecimal("1.99"))).doesNotThrowAnyException();
        assertThatCode(() -> NetWeightRule.check(new BigDecimal("500"), BigDecimal.ZERO)).doesNotThrowAnyException();
    }
}
