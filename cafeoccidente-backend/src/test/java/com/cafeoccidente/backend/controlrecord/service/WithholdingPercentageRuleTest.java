package com.cafeoccidente.backend.controlrecord.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Hallazgo del 18-nov: El Tambo quedó con 5 en vez de 0,5 y retuvo el 5 %. */
class WithholdingPercentageRuleTest {

    private static final String MESSAGE = "El porcentaje de retención en la fuente debe estar entre 0 y 3 % (ejemplo: 0,5)";

    @Test
    void acceptsFromZeroToTheTop() {
        for (String value : new String[] {"0", "0.5", "3"}) {
            assertThatCode(() -> WithholdingPercentageRule.check(new BigDecimal(value))).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsBelowZeroOrAboveTheTop() {
        for (String value : new String[] {"-0.1", "3.01", "5"}) {
            assertThatThrownBy(() -> WithholdingPercentageRule.check(new BigDecimal(value)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(MESSAGE);
        }
    }
}
