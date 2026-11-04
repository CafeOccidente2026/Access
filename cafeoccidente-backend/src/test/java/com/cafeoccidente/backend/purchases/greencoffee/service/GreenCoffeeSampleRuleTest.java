package com.cafeoccidente.backend.purchases.greencoffee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculator;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeeSample;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GreenCoffeeSampleRuleTest {

    private final DryCoffeePurchaseCalculator formulas = new DryCoffeePurchaseCalculator();

    private static ControlRecord controlRecord(String total, String husk, String healthy) {
        ControlRecord cr = new ControlRecord();
        cr.setSampleSize(new BigDecimal("250"));
        cr.setExcelsoKg(70);
        cr.setGreenTotalStoredWeight(total == null ? null : new BigDecimal(total));
        cr.setGreenTotalHuskWeight(husk == null ? null : new BigDecimal(husk));
        cr.setGreenHealthyStoredWeight(healthy == null ? null : new BigDecimal(healthy));
        return cr;
    }

    @Test
    void theExampleOfHu23Gives44_5And94() {
        // HU-23 CA-2: 140 / 12,5 / 186,18 -> Porc Merma 44 %, Porc Kg Pas 5 % y Factor 94. Alm Sana tal cual.
        GreenCoffeeSample sample = GreenCoffeeSampleRule.require(controlRecord("140", "12.5", "186.18"), formulas);

        assertThat(sample.wastePercentage()).isEqualByComparingTo("44.00");
        assertThat(sample.huskPercentage()).isEqualByComparingTo("5.00");
        assertThat(sample.factor()).isEqualByComparingTo("94.00");
        assertThat(sample.healthyStoredWeight()).isEqualByComparingTo("186.18");
    }

    @Test
    void anyMissingWeightBlocksThePurchase() {
        for (ControlRecord cr : new ControlRecord[] {controlRecord("0", "12.5", "186.18"),
                controlRecord("140", "0", "186.18"), controlRecord("140", "12.5", null)}) {
            assertThat(GreenCoffeeSampleRule.sampleOrNull(cr, formulas)).isNull();
            assertThatThrownBy(() -> GreenCoffeeSampleRule.require(cr, formulas))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(GreenCoffeeSampleRule.MISSING)
                    .extracting("code").isEqualTo(GreenCoffeeSampleRule.CODE);
        }
    }
}
