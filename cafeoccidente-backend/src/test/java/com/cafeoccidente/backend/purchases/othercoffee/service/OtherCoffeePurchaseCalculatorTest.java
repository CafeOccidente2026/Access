package com.cafeoccidente.backend.purchases.othercoffee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculation;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculator;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Ver OtherCoffeePurchaseCalculator - la diferencia real y confirmada contra Form_COMPRASESP.bas
 *  es que Vr_Kilo NO suma el ajuste por Pr_AlmDefec que si suma Cafe Seco. */
class OtherCoffeePurchaseCalculatorTest {

    private static final BigDecimal ANNOUNCEMENT_BASE = new BigDecimal("1200000.00");

    private final OtherCoffeePurchaseCalculator calculator = new OtherCoffeePurchaseCalculator();

    /** Mismos valores del RegControl sembrado en V4, usados por DryCoffeePurchaseCalculatorTest. */
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

    private OtherCoffeePurchaseRequest request(String growerType, boolean withholdingExempt) {
        return new OtherCoffeePurchaseRequest(
                1L, 1L, 27867, "RN", "123456", "Juan", "Perez", growerType, "Vereda", "3001234567",
                10, new BigDecimal("1250"), new BigDecimal("50"),
                new BigDecimal("240"), new BigDecimal("20"), new BigDecimal("220"),
                new BigDecimal("12000"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("692"),
                withholdingExempt, BigDecimal.ZERO, BigDecimal.ZERO, "EFECTIVO", null);
    }

    @Test
    void unitPriceMatchesDryCoffeeWithZeroDefectiveAlmondAdjustment() {
        // No se puede que Pr_AlmDefec afecte Vr_Kilo aca (ni siquiera es un parametro de este
        // calculador) - se verifica indirectamente contra DryCoffeePurchaseCalculator: con
        // announcementDefectiveUnitPrice=0, su var4 da exactamente 0 sea cual sea var3/
        // avgHuskPercentage, asi que su formula colapsa a la misma que usa este modulo (var2*var1,
        // sin sumar nada mas). Si algun dia alguien reintroduce el termino aca por error, este test
        // lo detecta sin depender de aritmetica calculada a mano.
        OtherCoffeePurchaseRequest otherRequest = request("S", false);
        OtherCoffeePurchaseCalculation otherResult = calculator.calculate(
                otherRequest, controlRecord(), ANNOUNCEMENT_BASE, BigDecimal.ZERO, BigDecimal.ZERO);

        DryCoffeePurchaseRequest dryRequest = new DryCoffeePurchaseRequest(
                otherRequest.agencyId(), otherRequest.fundId(), otherRequest.invoiceNumber(),
                otherRequest.specialType(), otherRequest.idNumber(), otherRequest.firstName(),
                otherRequest.lastName(), otherRequest.growerType(), otherRequest.address(),
                otherRequest.cellphone(), otherRequest.bagsCount(), otherRequest.grossKg(),
                otherRequest.tareKg(), otherRequest.totalStoredWeight(), otherRequest.defectiveStoredWeight(),
                otherRequest.healthyStoredWeight(), otherRequest.healthyUnitPrice(), otherRequest.bonus(),
                otherRequest.penalty(), otherRequest.costs(), otherRequest.withholdingExempt(),
                otherRequest.freightDiscount(), otherRequest.otherDiscounts(), otherRequest.paymentMethod(),
                otherRequest.checkNumber());
        DryCoffeePurchaseCalculation dryResult = new DryCoffeePurchaseCalculator().calculate(
                dryRequest, controlRecord(), ANNOUNCEMENT_BASE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(otherResult.unitPrice()).isEqualByComparingTo(dryResult.unitPrice());
    }

    @Test
    void basePriceLoadUsesTheFrozenAnnouncementCosts() {
        OtherCoffeePurchaseCalculation result = calculator.calculate(
                request("S", false), controlRecord(), ANNOUNCEMENT_BASE, BigDecimal.ZERO, BigDecimal.ZERO);

        // 1200000 - (692 * 125) = 1113500 - misma formula que Cafe Seco (Pr_Base_PC).
        assertThat(result.basePriceLoad()).isEqualByComparingTo("1113500.00");
    }

    @Test
    void deadGrowerIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(
                request("F", false), controlRecord(), ANNOUNCEMENT_BASE, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void exemptGrowerNeverWithholds() {
        OtherCoffeePurchaseCalculation result = calculator.calculate(
                request("S", true), controlRecord(), ANNOUNCEMENT_BASE,
                new BigDecimal("50000000"), new BigDecimal("250000"));

        assertThat(result.withholding()).isEqualByComparingTo("0");
    }
}
