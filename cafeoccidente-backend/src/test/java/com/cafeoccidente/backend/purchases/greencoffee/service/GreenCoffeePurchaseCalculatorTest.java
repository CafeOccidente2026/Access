package com.cafeoccidente.backend.purchases.greencoffee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GreenCoffeePurchaseCalculatorTest {

    private final GreenCoffeePurchaseCalculator calculator = new GreenCoffeePurchaseCalculator();

    private ControlRecord controlRecord(BigDecimal costs) {
        ControlRecord cr = new ControlRecord();
        cr.setBaseLoad(125);
        cr.setGreenCoffeePercentage(new BigDecimal("50"));
        cr.setAssociatePercentage(new BigDecimal("2"));
        cr.setNonAssociateDiscount(new BigDecimal("0.8"));
        cr.setBaseWithholding(new BigDecimal("8379840.00"));
        cr.setWithholdingPercentage(new BigDecimal("0.5"));
        cr.setCosts(costs);
        return cr;
    }

    private GreenCoffeePurchaseRequest request(BigDecimal costs) {
        return request("S", costs);
    }

    private GreenCoffeePurchaseRequest request(String growerType, BigDecimal costs) {
        return new GreenCoffeePurchaseRequest(
                1L, 1L, 1, "123456", "Juan", "Perez", growerType, "Vereda", "3001234567",
                10, new BigDecimal("1250"), new BigDecimal("50"),
                new BigDecimal("1200"), BigDecimal.ZERO, costs, BigDecimal.ZERO,
                new BigDecimal("1300"), false, BigDecimal.ZERO, BigDecimal.ZERO, "EFECTIVO", null);
    }

    @Test
    void basePriceLoadUsesTheFrozenAnnouncementCostsNotTheLiveControlRecord() {
        // Mismo caso que Dry Coffee: si el RegControl de la agencia cambio DESPUES de publicado el
        // anuncio, Form_VERDES.bas (Cedula_AfterUpdate) sigue usando el "Costos" que quedo fijado en
        // el textbox al elegir el anuncio -> request.costs() (700), no controlRecord.getCosts() (692).
        ControlRecord controlRecordWithChangedCosts = controlRecord(new BigDecimal("692.00"));

        GreenCoffeePurchaseCalculation result = calculator.calculate(
                request(new BigDecimal("700")), controlRecordWithChangedCosts,
                new BigDecimal("1200000.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        // 1200000 - (700 * 125) = 1200000 - 87500 = 1112500 (NO 1113500, que seria con el 692 vivo)
        assertThat(result.basePriceLoad()).isEqualByComparingTo("1112500.00");
    }

    @Test
    void deadGrowerIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(
                request("F", new BigDecimal("692")), controlRecord(new BigDecimal("692.00")),
                new BigDecimal("1200000.00"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }
}
