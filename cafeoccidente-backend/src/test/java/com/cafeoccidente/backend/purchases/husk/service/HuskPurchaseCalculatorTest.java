package com.cafeoccidente.backend.purchases.husk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class HuskPurchaseCalculatorTest {

    private final HuskPurchaseCalculator calculator = new HuskPurchaseCalculator();

    /** Valores del RegControl sembrado en V4__purchases_shared_and_announcement.sql. */
    private ControlRecord controlRecord() {
        ControlRecord cr = new ControlRecord();
        cr.setBaseHusk(new BigDecimal("12.5"));
        cr.setSampleSize(new BigDecimal("250.00"));
        cr.setBaseWithholding(new BigDecimal("8379840.00"));
        cr.setWithholdingPercentage(new BigDecimal("0.5"));
        cr.setAssociatePercentage(new BigDecimal("2"));
        cr.setNonAssociateDiscount(new BigDecimal("0.8"));
        return cr;
    }

    private HuskPurchaseRequest request(String growerType, boolean withholdingExempt) {
        return new HuskPurchaseRequest(
                1L, 1L, 27867, "123456", "Juan", "Perez", growerType, "Vereda", "3001234567",
                new BigDecimal("200"), 10, new BigDecimal("1250"), new BigDecimal("50"),
                new BigDecimal("8908"), new BigDecimal("692"),
                withholdingExempt, BigDecimal.ZERO, BigDecimal.ZERO, "EFECTIVO", null);
    }

    @Test
    void netKgIsGrossMinusTareWithoutAnySplit() {
        HuskPurchaseCalculation result = calculator.calculate(
                request("S", false), controlRecord(), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.netKg()).isEqualByComparingTo("1200.00");
    }

    @Test
    void almondPercentageIsAlmondWeightTimesHundredOverSampleSize() {
        HuskPurchaseCalculation result = calculator.calculate(
                request("S", false), controlRecord(), BigDecimal.ZERO, BigDecimal.ZERO);

        // 200 * 100 / 250 = 80.00
        assertThat(result.almondPercentage()).isEqualByComparingTo("80.00");
    }

    @Test
    void unitPriceIsPointPriceTimesAlmondPercentageOverBaseHuskMinusCosts() {
        HuskPurchaseCalculation result = calculator.calculate(
                request("S", false), controlRecord(), BigDecimal.ZERO, BigDecimal.ZERO);

        // (8908 * 80 / 12.5) - 692 = 57011.2 - 692 = 56319.2 -> redondeado a peso entero: 56319
        assertThat(result.unitPrice()).isEqualByComparingTo("56319.00");
    }

    @Test
    void sampleSizeOfZeroIsRejected() {
        ControlRecord zeroSample = controlRecord();
        zeroSample.setSampleSize(BigDecimal.ZERO);

        assertThatThrownBy(() -> calculator.calculate(
                request("S", false), zeroSample, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void associateGrowerPaysContributionNotCooperativeDiscount() {
        HuskPurchaseCalculation result = calculator.calculate(
                request("S", false), controlRecord(), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.associateContribution()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.cooperativeDiscount()).isEqualByComparingTo("0");
    }

    @Test
    void nonAssociateGrowerPaysCooperativeDiscountNotContribution() {
        HuskPurchaseCalculation result = calculator.calculate(
                request("C", false), controlRecord(), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.cooperativeDiscount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.associateContribution()).isEqualByComparingTo("0");
    }

    @Test
    void firstPurchaseOfMonthWithholdsOnItsOwnGrossValue() {
        HuskPurchaseCalculation result = calculator.calculate(
                request("S", false), controlRecord(), BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal expected = result.grossValue()
                .multiply(new BigDecimal("0.5"))
                .divide(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(result.withholding()).isEqualByComparingTo(expected);
    }

    @Test
    void exemptGrowerNeverWithholds() {
        HuskPurchaseCalculation result = calculator.calculate(
                request("S", true), controlRecord(), new BigDecimal("50000000"), new BigDecimal("250000"));

        assertThat(result.withholding()).isEqualByComparingTo("0");
    }

    @Test
    void deadGrowerIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(
                request("F", false), controlRecord(), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void negativePointPriceIsRejected() {
        HuskPurchaseRequest negative = new HuskPurchaseRequest(
                1L, 1L, 27867, "123456", "Juan", "Perez", "S", "Vereda", "3001234567",
                new BigDecimal("200"), 10, new BigDecimal("1250"), new BigDecimal("50"),
                new BigDecimal("-100"), new BigDecimal("692"),
                false, BigDecimal.ZERO, BigDecimal.ZERO, "EFECTIVO", null);

        assertThatThrownBy(() -> calculator.calculate(negative, controlRecord(), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }
}
