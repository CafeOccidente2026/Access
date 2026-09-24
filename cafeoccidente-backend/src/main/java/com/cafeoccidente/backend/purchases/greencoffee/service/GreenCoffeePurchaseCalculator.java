package com.cafeoccidente.backend.purchases.greencoffee.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.util.MoneyValidation;
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
        // Mismo guard que Cafe Seco/Otros (Cedula_AfterUpdate en los 3 formularios comparte la
        // verificacion "NO LE PUEDE FACTURAR A UN FALLECIDO"): sin esto, la API se podia llamar
        // directo (sin pasar por el bloqueo del frontend) para facturarle Verde a un caficultor
        // fallecido.
        if ("F".equalsIgnoreCase(request.growerType())) {
            throw new BusinessRuleException("No se le puede facturar a un caficultor fallecido");
        }

        // Cedula_AfterUpdate (Form_VERDES.bas linea 56): Pr_Base_PC = Texto91 - (Costos * Texto176).
        // Misma formula que Cafe Seco; "Costos" es el valor CONGELADO que la macro de anuncio escribe
        // en el textbox al elegir el producto (en todo Form_VERDES.bas es la unica escritura de
        // Costos) -> request.costs(), no ControlRecord.costos. BaseCarga (Texto176) si es vivo del
        // RegControl -> controlRecord.getBaseLoad(). Ojo: no se pudo confirmar en el .bas que control
        // dispara la macro de anuncio en VERDES (posible ligadura por diseñador, no visible aqui);
        // mismo nivel de confianza que los acumulados de retencion (Texto105/Texto107) - no verificado
        // al 100%, pero es la unica fuente de Costos que aparece en todo el modulo.
        BigDecimal basePriceLoad = announcementBasePriceLoad
                .subtract(request.costs().multiply(BigDecimal.valueOf(controlRecord.getBaseLoad())))
                .setScale(SCALE, RoundingMode.HALF_UP);
        MoneyValidation.requireNonNegative(basePriceLoad, "Precio Base Carga PC");

        // Destare_LostFocus.
        BigDecimal greenKg = request.grossKg().subtract(request.tareKg());
        BigDecimal netKg = greenKg.multiply(controlRecord.getGreenCoffeePercentage())
                .divide(HUNDRED, MathContext.DECIMAL64)
                .setScale(SCALE, RoundingMode.HALF_UP);

        // Vr_Kilo_Comp_AfterUpdate: Vr_Kilo = Vr_Kilo_Comp (digitado a mano, sin formula de calidad).
        BigDecimal unitPrice = request.compKgPrice().setScale(SCALE, RoundingMode.HALF_UP);
        MoneyValidation.requireNonNegative(unitPrice, "Vr. Kilo");

        // Castigo_lostFocus: Vr_Bruto = Vr_Kilo_Comp * Kilos_Verdes (no Kilos_Netos).
        BigDecimal grossValue = unitPrice.multiply(greenKg).setScale(SCALE, RoundingMode.HALF_UP);
        MoneyValidation.requireNonNegative(grossValue, "Vr. Bruto");
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
        // SIN VERIFICAR: CalculoReteFteMesVerdes solo abre el reporte "ReteMesCursoVerdes" y copia
        // TotalVrBruto/TotalRetefuente a Texto105/Texto107; el RecordSource del reporte no esta en
        // el export de VBA disponible. sumMonthlyTotalsByIdNumber es el mejor esfuerzo hasta confirmarlo.
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
        MoneyValidation.requireNonNegative(netToPay, "Neto a Pagar");

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
