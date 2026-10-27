package com.cafeoccidente.backend.purchases.husk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class HuskPurchaseCalculatorTest {

    private static final HuskPurchaseRequest REQUEST = new HuskPurchaseRequest(
            1L, "123456", null, null,
            new BigDecimal("200"), 10, new BigDecimal("1250"), new BigDecimal("50"),
            BigDecimal.ZERO, BigDecimal.ZERO, null);

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

    /** Anuncio de pasilla vigente: Pr Punto (healthyUnitPrice) dado y Costos 692. */
    private static AnnouncementResponse announcement(String pointPrice) {
        return new AnnouncementResponse(1L, "1", null, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(pointPrice), BigDecimal.ZERO, new BigDecimal("692"), 1L, 1L, "PASILLA", null);
    }

    private static PurchaseSeller seller(String growerType, boolean association) {
        return new PurchaseSeller("123456", "Juan", "Perez", growerType, "Vereda", "3001234567", association);
    }

    private HuskPurchaseCalculation calculate(String growerType, boolean association) {
        return calculator.calculate(REQUEST, controlRecord(), announcement("8908"), seller(growerType, association),
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void netKgIsGrossMinusTareWithoutAnySplit() {
        assertThat(calculate("S", false).netKg()).isEqualByComparingTo("1200.00");
    }

    @Test
    void almondPercentageIsAlmondWeightTimesHundredOverSampleSize() {
        // 200 * 100 / 250 = 80.00
        assertThat(calculate("S", false).almondPercentage()).isEqualByComparingTo("80.00");
    }

    @Test
    void unitPriceIsPointPriceTimesAlmondPercentageOverBaseHuskMinusCosts() {
        // (8908 * 80 / 12.5) - 692 = 57011.2 - 692 = 56319.2 -> redondeado a peso entero: 56319
        assertThat(calculate("S", false).unitPrice()).isEqualByComparingTo("56319.00");
    }

    @Test
    void sampleSizeOfZeroIsRejected() {
        ControlRecord zeroSample = controlRecord();
        zeroSample.setSampleSize(BigDecimal.ZERO);

        assertThatThrownBy(() -> calculator.calculate(
                REQUEST, zeroSample, announcement("8908"), seller("S", false), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void associateGrowerPaysContributionNotCooperativeDiscount() {
        HuskPurchaseCalculation result = calculate("S", false);

        assertThat(result.associateContribution()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.cooperativeDiscount()).isEqualByComparingTo("0");
    }

    @Test
    void nonAssociateGrowerPaysCooperativeDiscountNotContribution() {
        HuskPurchaseCalculation result = calculate("C", false);

        assertThat(result.cooperativeDiscount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.associateContribution()).isEqualByComparingTo("0");
    }

    @Test
    void firstPurchaseOfDayWithholdsOnItsOwnGrossValue() {
        HuskPurchaseCalculation result = calculate("S", false);

        BigDecimal expected = result.grossValue()
                .multiply(new BigDecimal("0.5"))
                .divide(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(result.withholding()).isEqualByComparingTo(expected);
    }

    @Test
    void anAssociationNeverWithholds() {
        HuskPurchaseCalculation result = calculator.calculate(REQUEST, controlRecord(), announcement("8908"),
                seller("S", true), new BigDecimal("50000000"), new BigDecimal("250000"));

        assertThat(result.withholding()).isEqualByComparingTo("0");
    }

    @Test
    void deadGrowerIsRejected() {
        assertThatThrownBy(() -> calculate("F", false)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void negativePointPriceIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(REQUEST, controlRecord(), announcement("-100"),
                seller("S", false), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }
}
