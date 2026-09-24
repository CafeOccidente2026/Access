package com.cafeoccidente.backend.purchases.future.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Form_FERTIFUTURO.bas, Castigo_lostFocus/W_AlmSana_AfterUpdate - constantes fijas (250, 2%,
 *  0.8%, $4.295.000, 0.5%), no ControlRecord (decision del usuario 2026-09-23). */
class FertiFuturoPurchaseCalculatorTest {

    private final FertiFuturoPurchaseCalculator calculator = new FertiFuturoPurchaseCalculator();

    private FertiFuturoPurchaseRequest request(
            String growerType, boolean withholdingExempt, BigDecimal healthyStoredWeight) {
        return new FertiFuturoPurchaseRequest(
                1L, 1L, 27867, "RN", "123456", "Juan", "Perez", growerType, "Vereda", null,
                10, new BigDecimal("1200"), new BigDecimal("1250"), healthyStoredWeight,
                new BigDecimal("20"), new BigDecimal("50"), withholdingExempt,
                BigDecimal.ZERO, BigDecimal.ZERO, "EFECTIVO", null);
    }

    @Test
    void qualityIncrementIsZeroAtOrBelowTheSeventyFivePercentThreshold() {
        // healthyStoredWeight=187.5 -> PorcAlmSana = 187.5*100/250 = 75.00 (limite, no ">").
        FertiFuturoPurchaseCalculation result = calculator.calculate(
                request("S", false, new BigDecimal("187.5")),
                new BigDecimal("12000"), BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("692"),
                new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.healthyPercentage()).isEqualByComparingTo("75.00");
        assertThat(result.qualityIncrementAmount()).isEqualByComparingTo("0");
    }

    @Test
    void qualityIncrementAppliesOnlyAboveTheThreshold() {
        // healthyStoredWeight=220 -> PorcAlmSana = 220*100/250 = 88.00 -> (88-75)*500 = 6500.
        FertiFuturoPurchaseCalculation result = calculator.calculate(
                request("S", false, new BigDecimal("220")),
                new BigDecimal("12000"), BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("692"),
                new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.qualityIncrementAmount()).isEqualByComparingTo("6500.00");
    }

    @Test
    void deadGrowerIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(
                request("F", false, new BigDecimal("220")),
                new BigDecimal("12000"), BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("692"),
                new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void exemptGrowerNeverWithholdsEvenAboveTheFixedThreshold() {
        FertiFuturoPurchaseCalculation result = calculator.calculate(
                request("S", true, new BigDecimal("220")),
                new BigDecimal("12000"), BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("692"),
                new BigDecimal("500"), new BigDecimal("50000000"), new BigDecimal("250000"));

        assertThat(result.withholding()).isEqualByComparingTo("0");
    }

    @Test
    void associateContributionUsesTheFixedTwoPercentRate() {
        FertiFuturoPurchaseCalculation result = calculator.calculate(
                request("S", false, new BigDecimal("220")),
                new BigDecimal("12000"), BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("692"),
                new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal expected = result.grossValue().multiply(new BigDecimal("0.02"))
                .setScale(0, java.math.RoundingMode.HALF_UP).setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(result.associateContribution()).isEqualByComparingTo(expected);
    }
}
