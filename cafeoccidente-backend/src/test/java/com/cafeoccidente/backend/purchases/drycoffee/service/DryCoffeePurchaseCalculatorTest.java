package com.cafeoccidente.backend.purchases.drycoffee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DryCoffeePurchaseCalculatorTest {

    private static final BigDecimal ANNOUNCEMENT_BASE = new BigDecimal("1200000.00");

    private final DryCoffeePurchaseCalculator calculator = new DryCoffeePurchaseCalculator();

    /** Valores del RegControl sembrado en V4__purchases_shared_and_announcement.sql. */
    private ControlRecord controlRecord() {
        ControlRecord cr = new ControlRecord();
        cr.setBaseFactor(94);
        cr.setBaseWithholding(new BigDecimal("8379840.00"));
        cr.setBaseLoad(125);
        cr.setWithholdingPercentage(new BigDecimal("0.5"));
        cr.setAvgHuskPercentage(new BigDecimal("6.14"));
        cr.setCosts(new BigDecimal("692.00"));
        cr.setSampleSize(new BigDecimal("250.00"));
        cr.setSpecialtyThreshold(new BigDecimal("93.33"));
        cr.setAssociatePercentage(new BigDecimal("2"));
        cr.setNonAssociateDiscount(new BigDecimal("0.8"));
        return cr;
    }

    private DryCoffeePurchaseRequest request(String growerType, boolean withholdingExempt) {
        return new DryCoffeePurchaseRequest(
                1L, 1L, "RN", "123456", "Juan", "Perez", growerType, "Vereda", "3001234567",
                10, new BigDecimal("1250"), new BigDecimal("50"),
                new BigDecimal("240"), new BigDecimal("20"), new BigDecimal("220"),
                new BigDecimal("12000"), new BigDecimal("8000"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("692"),
                withholdingExempt, BigDecimal.ZERO, BigDecimal.ZERO, "EFECTIVO", null);
    }

    @Test
    void computesBasePriceLoadFromAnnouncementMinusCostsTimesBaseLoad() {
        DryCoffeePurchaseCalculation result = calculator.calculate(
                request("S", false), controlRecord(), ANNOUNCEMENT_BASE, BigDecimal.ZERO, BigDecimal.ZERO);

        // 1200000 - (692 * 125) = 1200000 - 86500 = 1113500
        assertThat(result.basePriceLoad()).isEqualByComparingTo("1113500.00");
    }

    @Test
    void firstPurchaseOfMonthWithholdsOnItsOwnGrossValue() {
        DryCoffeePurchaseCalculation result = calculator.calculate(
                request("S", false), controlRecord(), ANNOUNCEMENT_BASE, BigDecimal.ZERO, BigDecimal.ZERO);

        // withholding = grossValue * 0.5 / 100 (por encima del umbral, sin acumulado previo)
        BigDecimal expected = result.grossValue()
                .multiply(new BigDecimal("0.5"))
                .divide(new BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(result.withholding()).isEqualByComparingTo(expected);
    }

    @Test
    void secondPurchaseOfMonthWithholdsOnlyTheIncrementOverTheAccumulatedTotal() {
        DryCoffeePurchaseCalculation first = calculator.calculate(
                request("S", false), controlRecord(), ANNOUNCEMENT_BASE, BigDecimal.ZERO, BigDecimal.ZERO);

        DryCoffeePurchaseCalculation second = calculator.calculate(
                request("S", false), controlRecord(), ANNOUNCEMENT_BASE,
                first.grossValue(), first.withholding());

        // Retefuente incremental = (grossValue2 + grossValue1) * 0.5/100 - retefuente1
        BigDecimal expected = second.grossValue().add(first.grossValue())
                .multiply(new BigDecimal("0.5"))
                .divide(new BigDecimal("100"))
                .subtract(first.withholding())
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(second.withholding()).isEqualByComparingTo(expected);

        // Como ambas compras son iguales, el incremento de la segunda ~= retefuente de la primera.
        assertThat(second.withholding()).isEqualByComparingTo(first.withholding());
    }

    @Test
    void exemptGrowerNeverWithholds() {
        DryCoffeePurchaseCalculation result = calculator.calculate(
                request("S", true), controlRecord(), ANNOUNCEMENT_BASE,
                new BigDecimal("50000000"), new BigDecimal("250000"));

        assertThat(result.withholding()).isEqualByComparingTo("0");
    }

    @Test
    void deadGrowerIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(
                request("F", false), controlRecord(), ANNOUNCEMENT_BASE, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }
}
