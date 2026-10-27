package com.cafeoccidente.backend.purchases.shared.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SpecialtyThresholdRuleTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("93.33");

    @Test
    void aNespressoFactorAboveTheThresholdIsRejectedWithItsCode() {
        assertThatThrownBy(() -> SpecialtyThresholdRule.check(true, new BigDecimal("94.00"), THRESHOLD))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("El Factor (94,00) supera el umbral de cafés especiales (93,33)."
                        + " Digite de nuevo los pesos de la muestra.")
                .extracting("code").isEqualTo(SpecialtyThresholdRule.CODE);
    }

    @Test
    void exactlyTheThresholdDoesNotExceedIt() {
        assertThatCode(() -> SpecialtyThresholdRule.check(true, new BigDecimal("93.33"), THRESHOLD)).doesNotThrowAnyException();
    }

    @Test
    void otherSpecialsAreNotChecked() {
        // RN, Regional Nariño 4C y los demas: en El Tambo llegan a 112,90.
        assertThatCode(() -> SpecialtyThresholdRule.check(false, new BigDecimal("112.90"), THRESHOLD))
                .doesNotThrowAnyException();
    }
}
