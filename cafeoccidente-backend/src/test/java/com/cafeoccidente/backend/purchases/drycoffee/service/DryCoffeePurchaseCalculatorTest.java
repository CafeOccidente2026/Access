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
        return request(grossKg, "50");
    }

    private static DryCoffeePurchaseRequest request(String grossKg, String penalty) {
        return new DryCoffeePurchaseRequest(
                1L, "RN", "123456", null, null,
                10, new BigDecimal(grossKg), new BigDecimal("50"),
                new BigDecimal("240"), new BigDecimal("20"),
                new BigDecimal(penalty), BigDecimal.ZERO, BigDecimal.ZERO, null);
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
    void aPositivePenaltyIsTakenAsNegativeAndLowersTheKiloPrice() {
        // Q1 / RN-14.7: 23 se registra como -23; sin castigo el Vr. Kilo es mayor.
        BigDecimal typedPositive = calculator.calculate(request("1250", "23"), controlRecord(), announcement(),
                seller("S", false), BigDecimal.ZERO, BigDecimal.ZERO).unitPrice();
        BigDecimal typedNegative = calculator.calculate(request("1250", "-23"), controlRecord(), announcement(),
                seller("S", false), BigDecimal.ZERO, BigDecimal.ZERO).unitPrice();
        BigDecimal none = calculator.calculate(request("1250", "0"), controlRecord(), announcement(),
                seller("S", false), BigDecimal.ZERO, BigDecimal.ZERO).unitPrice();

        assertThat(typedPositive).isEqualByComparingTo(typedNegative);
        assertThat(typedPositive).isLessThan(none);
    }

    @Test
    void theHealthyAlmondIsTotalMinusHuskAndFeedsTheFactor() {
        // Q2: se digitan Peso Tot Alm (200) y Peso Pasilla (10); Alm Sana = 190 y Factor = 250 x 70 / 190.
        assertThat(calculator.healthyStoredWeight(new BigDecimal("200"), new BigDecimal("10"))).isEqualByComparingTo("190");
        assertThat(calculator.healthyPercentage(new BigDecimal("190"), controlRecord())).isEqualByComparingTo("92.11");
        // Ejemplo del RF (198,3 / 7,1 / 186,17 -> Factor 94,0001): no suma; con la regla del area
        // (Q2) la Alm Sana es 191,20 y el Factor 91,53.
        BigDecimal rfHealthy = calculator.healthyStoredWeight(new BigDecimal("198.3"), new BigDecimal("7.1"));
        assertThat(rfHealthy).isEqualByComparingTo("191.20");
        assertThat(calculator.healthyPercentage(rfHealthy, controlRecord())).isEqualByComparingTo("91.53");
    }

    @Test
    void theHuskCannotReachTheTotalAlmond() {
        assertThatThrownBy(() -> calculator.healthyStoredWeight(new BigDecimal("200"), new BigDecimal("200")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("La pasilla debe ser menor que la almendra total");
    }

    @Test
    void deadGrowerIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(
                request(), controlRecord(), announcement(), seller("F", false), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }

    private static AnnouncementResponse announcement(String base, String costs, String defectivePrice,
            String healthyPrice, String bonus) {
        return new AnnouncementResponse(1L, "1", null, new BigDecimal(base), new BigDecimal(defectivePrice),
                new BigDecimal(healthyPrice), new BigDecimal(bonus), new BigDecimal(costs), 1L, 1L, "RN", null);
    }

    private static DryCoffeePurchaseRequest sample(String grossKg, String totalStored, String husk) {
        return new DryCoffeePurchaseRequest(1L, "RN", "123456", null, null, 1, new BigDecimal(grossKg), BigDecimal.ZERO,
                new BigDecimal(totalStored), new BigDecimal(husk), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null);
    }

    @Test
    void rf14ExampleWithWeightsThatAddUpGivesKiloPrice12780() {
        // RF-14 con Peso Tot Alm 193,27 (Q2: 193,27 - 7,1 = 186,17), PasillaRef 6,36 y Precio Pasilla 6.000.
        ControlRecord cr = controlRecord();
        cr.setAvgHuskPercentage(new BigDecimal("6.36"));

        DryCoffeePurchaseCalculation result = calculator.calculate(sample("100", "193.27", "7.1"), cr,
                announcement("1546900", "0", "6000", "12375.20", "640"), seller("S", false),
                BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.basePriceLoad()).isEqualByComparingTo("1546900");
        assertThat(result.wastePercentage()).isEqualByComparingTo("22.69");
        assertThat(result.defectivePercentage()).isEqualByComparingTo("2.84");
        assertThat(result.healthyStoredWeight()).isEqualByComparingTo("186.17");
        assertThat(result.healthyPercentage()).isEqualByComparingTo("94.00");
        // 13.015,23 + (-235,56) = 12.779,67: Vr. Kilo en pesos enteros HALF_UP (Q3).
        assertThat(result.unitPrice()).isEqualByComparingTo("12780");
        assertThat(result.grossValue()).isEqualByComparingTo("1278000");
    }

    @Test
    void invoice43002OfElTamboMatchesAccess() {
        // Factura real 43002 (17-ene-2026): Access guarda Vr. Kilo 20.963, calculado con el Factor completo
        // (92,105263...); con el Factor redondeado a 92,11 daria 20.962. El Factor se muestra 92,11.
        DryCoffeePurchaseCalculation result = calculator.calculate(sample("30", "200", "10"), controlRecord(),
                announcement("2657500", "698", "10000", "20562", "240"), seller("S", false),
                BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.basePriceLoad()).isEqualByComparingTo("2570250");
        assertThat(result.healthyPercentage()).isEqualByComparingTo("92.11");
        assertThat(result.wastePercentage()).isEqualByComparingTo("20");
        assertThat(result.defectivePercentage()).isEqualByComparingTo("4");
        assertThat(result.unitPrice()).isEqualByComparingTo("20963");
        assertThat(result.grossValue()).isEqualByComparingTo("628890");
        assertThat(result.associateContribution()).isEqualByComparingTo("12578");
        assertThat(result.cooperativeDiscount()).isEqualByComparingTo("0");
        assertThat(result.withholding()).isEqualByComparingTo("0");
        assertThat(result.netToPay()).isEqualByComparingTo("616312");
    }

    /** Factor 100 = Factor Base y sin pasilla: Vr. Kilo = Pr Sustentacion + Bonificacion, sin termino 2. */
    private ControlRecord flatRecord() {
        ControlRecord cr = controlRecord();
        cr.setBaseFactor(new BigDecimal("100"));
        cr.setBaseWithholding(new BigDecimal("1000000"));
        cr.setAssociatePercentage(new BigDecimal("0.8"));
        return cr;
    }

    private DryCoffeePurchaseCalculation flat(String netKg, String bonus, String growerType,
            String accumulatedGross, String accumulatedWithholding) {
        return calculator.calculate(sample(netKg, "175", "0"), flatRecord(),
                announcement("1200000", "0", "0", "6000", bonus), seller(growerType, false),
                new BigDecimal(accumulatedGross), new BigDecimal(accumulatedWithholding));
    }

    @Test
    void ca5ZeroBonusLeavesTheBasePriceAsTheSupportPrice() {
        assertThat(flat("100", "0", "S", "0", "0").unitPrice()).isEqualByComparingTo("6000");
        assertThat(flat("100", "240", "S", "0", "0").unitPrice()).isEqualByComparingTo("6240");
    }

    @Test
    void associateContributionAndCooperativeDiscountUseTheControlRecordPercentage() {
        // 600.000 x 0,8 %: S paga aporte y C descuento cooperativo, nunca los dos.
        DryCoffeePurchaseCalculation associate = flat("100", "0", "S", "0", "0");
        DryCoffeePurchaseCalculation nonAssociate = flat("100", "0", "C", "0", "0");

        assertThat(associate.associateContribution()).isEqualByComparingTo("4800");
        assertThat(associate.cooperativeDiscount()).isEqualByComparingTo("0");
        assertThat(nonAssociate.associateContribution()).isEqualByComparingTo("0");
        assertThat(nonAssociate.cooperativeDiscount()).isEqualByComparingTo("4800");
        assertThat(nonAssociate.netToPay()).isEqualByComparingTo("595200");
    }

    @Test
    void rf16WithholdingInDryCoffee600kThen600kThen300k() {
        // Base 1.000.000 y 0,5 %: 600.000 no retiene; 600.000 + 600.000 = 1.200.000 -> 6.000; luego 300.000 -> 1.500.
        assertThat(flat("100", "0", "C", "0", "0").withholding()).isEqualByComparingTo("0");
        assertThat(flat("100", "0", "C", "600000", "0").withholding()).isEqualByComparingTo("6000");
        assertThat(flat("50", "0", "C", "1200000", "6000").withholding()).isEqualByComparingTo("1500");
    }
}
