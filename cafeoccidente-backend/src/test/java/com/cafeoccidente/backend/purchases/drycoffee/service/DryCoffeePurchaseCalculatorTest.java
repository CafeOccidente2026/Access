package com.cafeoccidente.backend.purchases.drycoffee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DryCoffeePurchaseCalculatorTest {

    private static final BigDecimal ANNOUNCEMENT_BASE = new BigDecimal("1200000.00");

    private final DryCoffeePurchaseCalculator calculator = new DryCoffeePurchaseCalculator();

    /** Valores del RegControl sembrado en V4__purchases_shared_and_announcement.sql. */
    private ControlRecord controlRecord() {
        ControlRecord cr = new ControlRecord();
        cr.setExcelsoKg(70);
        cr.setBaseFactor(new BigDecimal("94"));
        cr.setSpecialtyThreshold(new BigDecimal("93.33"));
        cr.setBaseWithholding(new BigDecimal("8379840.00"));
        cr.setBaseLoad(125);
        cr.setWithholdingPercentage(new BigDecimal("0.5"));
        cr.setAvgHuskPercentage(new BigDecimal("6.14"));
        cr.setCosts(new BigDecimal("692.00"));
        cr.setSampleSize(new BigDecimal("250.00"));
        cr.setAssociatePercentage(new BigDecimal("2"));
        cr.setNonAssociateDiscount(new BigDecimal("0.8"));
        return cr;
    }

    /** Anuncio vigente: Pr Sustentacion 12000, Bonificacion 100 y los Costos y Pr_AlmDefec dados. */
    private static AnnouncementResponse announcement(String costs, String defectivePrice) {
        return new AnnouncementResponse(1L, "1", null, ANNOUNCEMENT_BASE, new BigDecimal(defectivePrice),
                new BigDecimal("12000"), new BigDecimal("100"), new BigDecimal(costs), 1L, 1L, "RN", null);
    }

    private static AnnouncementResponse announcement() {
        return announcement("692", "0");
    }

    private static PurchaseSeller seller(String growerType, boolean association) {
        return new PurchaseSeller("123456", "Juan", "Perez", growerType, "Vereda", "3001234567", association);
    }

    private static DryCoffeePurchaseRequest request(String grossKg) {
        return new DryCoffeePurchaseRequest(
                1L, "RN", "123456", null, null,
                10, new BigDecimal(grossKg), new BigDecimal("50"),
                new BigDecimal("240"), new BigDecimal("20"), new BigDecimal("220"),
                new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    private static DryCoffeePurchaseRequest request() {
        return request("1250");
    }

    @Test
    void computesBasePriceLoadFromAnnouncementMinusCostsTimesBaseLoad() {
        DryCoffeePurchaseCalculation result = calculator.calculate(
                request(), controlRecord(), announcement(), seller("S", false), BigDecimal.ZERO, BigDecimal.ZERO);

        // 1200000 - (692 * 125) = 1200000 - 86500 = 1113500
        assertThat(result.basePriceLoad()).isEqualByComparingTo("1113500.00");
    }

    @Test
    void basePriceLoadUsesTheAnnouncementCostsNotTheControlRecordOnes() {
        // Form_COMPRAS.bas fija "Costos" desde el anuncio (macro "Asignar numero anuncio * PCompras");
        // el calculo usa announcement.costs() (700), no controlRecord.getCosts() (692).
        DryCoffeePurchaseCalculation result = calculator.calculate(
                request(), controlRecord(), announcement("700", "0"), seller("S", false),
                BigDecimal.ZERO, BigDecimal.ZERO);

        // 1200000 - (700 * 125) = 1200000 - 87500 = 1112500 (NO 1113500, que seria con el 692)
        assertThat(result.basePriceLoad()).isEqualByComparingTo("1112500.00");
    }

    @Test
    void announcementDefectiveUnitPriceFeedsTheQualityUnitPrice() {
        DryCoffeePurchaseCalculation withoutDefectivePrice = calculator.calculate(
                request(), controlRecord(), announcement("692", "0"), seller("S", false),
                BigDecimal.ZERO, BigDecimal.ZERO);

        DryCoffeePurchaseCalculation withDefectivePrice = calculator.calculate(
                request(), controlRecord(), announcement("692", "8000"), seller("S", false),
                BigDecimal.ZERO, BigDecimal.ZERO);

        // var4 pasa de 0 a un valor positivo -> el precio unitario (y por lo tanto Vr. Bruto) sube.
        assertThat(withDefectivePrice.unitPrice()).isGreaterThan(withoutDefectivePrice.unitPrice());
    }

    @Test
    void grossValueIsRoundedToWholePesoLikeAccess() {
        // Kilos_Netos con decimales (1250.3 - 50 = 1200.3): Vr_Bruto es DecimalPlaces=0 en
        // COMPRAS.txt -> Access lo muestra/guarda entero, no 2.233.473,6.
        DryCoffeePurchaseCalculation result = calculator.calculate(
                request("1250.3"), controlRecord(), announcement(), seller("S", false),
                BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal expected = result.unitPrice().multiply(new BigDecimal("1200.3"))
                .setScale(0, java.math.RoundingMode.HALF_UP);
        assertThat(result.grossValue()).isEqualByComparingTo(expected);
        assertThat(result.grossValue().stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
    }

    @Test
    void firstPurchaseOfDayWithholdsOnItsOwnGrossValue() {
        DryCoffeePurchaseCalculation result = calculator.calculate(
                request(), controlRecord(), announcement(), seller("S", false), BigDecimal.ZERO, BigDecimal.ZERO);

        // withholding = grossValue * 0.5 / 100 (por encima del umbral, sin acumulado previo)
        BigDecimal expected = result.grossValue()
                .multiply(new BigDecimal("0.5"))
                .divide(new BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(result.withholding()).isEqualByComparingTo(expected);
    }

    @Test
    void secondPurchaseOfDayWithholdsOnlyTheIncrementOverTheAccumulatedTotal() {
        DryCoffeePurchaseCalculation first = calculator.calculate(
                request(), controlRecord(), announcement(), seller("S", false), BigDecimal.ZERO, BigDecimal.ZERO);

        DryCoffeePurchaseCalculation second = calculator.calculate(
                request(), controlRecord(), announcement(), seller("S", false),
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
    void anAssociationNeverWithholds() {
        DryCoffeePurchaseCalculation result = calculator.calculate(
                request(), controlRecord(), announcement(), seller("S", true),
                new BigDecimal("50000000"), new BigDecimal("250000"));

        assertThat(result.withholding()).isEqualByComparingTo("0");
    }

    @Test
    void aNegativeMoneyValueInTheCascadeBlocksThePurchase() {
        // Base Carga mal digitada en el Registro de Control (2000 en vez de 125):
        // Precio Base Carga PC = 1200000 - (692 * 2000) = -184000.
        ControlRecord wrongBaseLoad = controlRecord();
        wrongBaseLoad.setBaseLoad(2000);

        assertThatThrownBy(() -> calculator.calculate(
                request(), wrongBaseLoad, announcement(), seller("S", false), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("\"Precio Base Carga PC\" dio un valor negativo (-184000.00). Revise el anuncio"
                        + " vigente o el Registro de Control de la agencia antes de continuar.");
    }

    @Test
    void deadGrowerIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(
                request(), controlRecord(), announcement(), seller("F", false), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }
}
