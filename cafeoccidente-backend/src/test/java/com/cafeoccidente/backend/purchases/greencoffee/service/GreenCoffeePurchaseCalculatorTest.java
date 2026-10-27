package com.cafeoccidente.backend.purchases.greencoffee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;
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

    /** Anuncio CV vigente: Pr Alm Sana 1200, Bonificacion 0 y los Costos dados. */
    private static AnnouncementResponse announcement(String costs) {
        return new AnnouncementResponse(1L, "1", null, new BigDecimal("1200000.00"), BigDecimal.ZERO,
                new BigDecimal("1200"), BigDecimal.ZERO, new BigDecimal(costs), 1L, 1L, "CV", null);
    }

    private static PurchaseSeller seller(String growerType) {
        return new PurchaseSeller("123456", "Juan", "Perez", growerType, "Vereda", "3001234567", false);
    }

    private static GreenCoffeePurchaseRequest request(String grossKg, String compKgPrice) {
        return new GreenCoffeePurchaseRequest(
                1L, "123456", null, null,
                10, new BigDecimal(grossKg), new BigDecimal("50"),
                BigDecimal.ZERO, new BigDecimal(compKgPrice), BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    @Test
    void basePriceLoadUsesTheAnnouncementCostsNotTheControlRecordOnes() {
        // Form_VERDES.bas (Cedula_AfterUpdate) usa el "Costos" que fija la macro del anuncio CV:
        // announcement.costs() (700), no controlRecord.getCosts() (692).
        GreenCoffeePurchaseCalculation result = calculator.calculate(
                request("1250", "1300"), controlRecord(new BigDecimal("692.00")), announcement("700"), seller("S"),
                BigDecimal.ZERO, BigDecimal.ZERO);

        // 1200000 - (700 * 125) = 1200000 - 87500 = 1112500 (NO 1113500, que seria con el 692)
        assertThat(result.basePriceLoad()).isEqualByComparingTo("1112500.00");
    }

    @Test
    void wholePesoControlsAreRoundedLikeAccess() {
        // Vr_Kilo, Aporte_Socio, Retefuente y Neto_a_Pagar son DecimalPlaces=0 en VERDES.txt.
        GreenCoffeePurchaseCalculation result = calculator.calculate(
                request("1250.3", "1300.6"), controlRecord(new BigDecimal("692.00")), announcement("692"),
                seller("S"), new BigDecimal("8000000"), BigDecimal.ZERO);

        assertThat(result.unitPrice()).isEqualByComparingTo("1301");               // 1300.6
        assertThat(result.grossValue()).isEqualByComparingTo("1561590");           // 1301 * 1200.3
        assertThat(result.associateContribution()).isEqualByComparingTo("31232");  // 31231.8
        assertThat(result.withholding()).isEqualByComparingTo("47808");            // 9561590 * 0.5% = 47807.95
        assertThat(result.netToPay()).isEqualByComparingTo("1482550");
    }

    @Test
    void cooperativeDiscountIsRoundedToWholePeso() {
        GreenCoffeePurchaseCalculation result = calculator.calculate(
                request("1250.3", "1300.6"), controlRecord(new BigDecimal("692.00")), announcement("692"),
                seller("C"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.cooperativeDiscount()).isEqualByComparingTo("12493");    // 1561590 * 0.8% = 12492.72
        assertThat(result.netToPay()).isEqualByComparingTo("1549097");
    }

    @Test
    void deadGrowerIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(
                request("1250", "1300"), controlRecord(new BigDecimal("692.00")), announcement("692"), seller("F"),
                BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }
}
