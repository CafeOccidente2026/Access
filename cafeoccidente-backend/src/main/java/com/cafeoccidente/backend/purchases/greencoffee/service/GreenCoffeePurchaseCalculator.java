package com.cafeoccidente.backend.purchases.greencoffee.service;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Reproduce la cascada de Form_VERDES.bas: Destare_LostFocus (Kilos_Verdes/Kilos_Netos) ->
 * Vr_Kilo_Comp_AfterUpdate (Vr_Kilo) -> Castigo_lostFocus (Vr_Bruto/Aporte_Socio|Descuento_Coop/
 * Retefuente) -> Descuento_Fro_LostFocus/OtrosDescuentos (Neto_a_Pagar). A diferencia de Cafe Seco,
 * el precio de compra (Vr_Kilo_Comp) es digitado a mano - no hay formula de calidad (var1..var5).
 */
@Component
public class GreenCoffeePurchaseCalculator {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param announcementBasePriceLoad Pr_Base_CPS del anuncio vigente (Texto91 en el VBA)
     * @param monthlyAccumulatedGrossValue suma de Vr_Bruto ya registrado en VERDES para esta cedula
     *     en el mes actual (Texto105 / macro CalculoReteFteMesVerdes)
     * @param monthlyAccumulatedWithholding suma de Retefuente ya aplicada en VERDES a esta cedula en
     *     el mes actual (Texto107 / macro CalculoReteFteMesVerdes)
     */
    public GreenCoffeePurchaseCalculation calculate(
            GreenCoffeePurchaseRequest request,
            ControlRecord controlRecord,
            BigDecimal announcementBasePriceLoad,
            BigDecimal monthlyAccumulatedGrossValue,
            BigDecimal monthlyAccumulatedWithholding) {
        // Cedula_AfterUpdate: Pr_Base_PC = Texto91 - (Costos * BaseCarga). Misma formula que Cafe Seco.
        BigDecimal basePriceLoad = announcementBasePriceLoad
                .subtract(request.costs().multiply(BigDecimal.valueOf(controlRecord.getBaseLoad())))
                .setScale(SCALE, RoundingMode.HALF_UP);

        // Destare_LostFocus.
        BigDecimal greenKg = request.grossKg().subtract(request.tareKg());
        BigDecimal netKg = greenKg.multiply(controlRecord.getGreenCoffeePercentage())
                .divide(HUNDRED, MathContext.DECIMAL64)
                .setScale(SCALE, RoundingMode.HALF_UP);

        // Vr_Kilo_Comp_AfterUpdate: Vr_Kilo = Vr_Kilo_Comp (digitado a mano, sin formula de calidad).
        BigDecimal unitPrice = request.compKgPrice().setScale(SCALE, RoundingMode.HALF_UP);

        // Castigo_lostFocus: Vr_Bruto = Vr_Kilo_Comp * Kilos_Verdes (no Kilos_Netos).
        BigDecimal grossValue = unitPrice.multiply(greenKg).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal inventoryValue = grossValue;

        BigDecimal associateContribution = BigDecimal.ZERO;
        BigDecimal cooperativeDiscount = BigDecimal.ZERO;
        if ("S".equalsIgnoreCase(request.growerType())) {
            associateContribution = grossValue.multiply(controlRecord.getAssociatePercentage())
                    .divide(HUNDRED, MathContext.DECIMAL64)
                    .setScale(SCALE, RoundingMode.HALF_UP);
        } else if ("C".equalsIgnoreCase(request.growerType())) {
            cooperativeDiscount = grossValue.multiply(controlRecord.getNonAssociateDiscount())
                    .divide(HUNDRED, MathContext.DECIMAL64)
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }

        // Retefuente incremental sobre el acumulado mensual del caficultor en VERDES (var6).
        BigDecimal var6 = grossValue.add(monthlyAccumulatedGrossValue);
        BigDecimal withholding = BigDecimal.ZERO;
        if (!request.withholdingExempt() && var6.compareTo(controlRecord.getBaseWithholding()) > 0) {
            withholding = var6.multiply(controlRecord.getWithholdingPercentage())
                    .divide(HUNDRED, MathContext.DECIMAL64)
                    .subtract(monthlyAccumulatedWithholding)
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }

        // Descuento_Fro_LostFocus / OtrosDescuentos_AfterUpdate.
        BigDecimal netToPay = grossValue
                .subtract(associateContribution)
                .subtract(cooperativeDiscount)
                .subtract(withholding)
                .subtract(request.shrinkageDiscount())
                .subtract(request.otherDiscounts())
                .setScale(SCALE, RoundingMode.HALF_UP);

        return new GreenCoffeePurchaseCalculation(
                basePriceLoad,
                greenKg.setScale(SCALE, RoundingMode.HALF_UP),
                netKg,
                unitPrice,
                grossValue,
                inventoryValue,
                associateContribution,
                cooperativeDiscount,
                withholding,
                netToPay);
    }
}
