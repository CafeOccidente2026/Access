package com.cafeoccidente.backend.purchases.husk.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Reproduce la cascada de Form_PASILLA.bas: W_AlmSana_AfterUpdate (PorcAlmSana) -> Destare_LostFocus
 * (Kilos_Netos) -> Descuento_Fro_LostFocus (Vr_Kilo/Vr_Bruto/Aporte_Socio|Descuento_Coop/Retefuente/
 * Neto_a_Pagar) -> OtrosDescuentos (Neto_a_Pagar). A diferencia de Cafe Seco/VERDES, no hay formula
 * Pr_Base_PC (Costos*BaseCarga) ni split Kilos_Verdes: Kilos_Netos = Kilos_Brutos - Destare directo.
 */
@Component
public class HuskPurchaseCalculator {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param monthlyAccumulatedGrossValue suma de Vr_Bruto ya registrado en PASILLA para esta cedula
     *     en el mes actual (Texto105 / macro CalculoReteFteMesPas)
     * @param monthlyAccumulatedWithholding suma de Retefuente ya aplicada en PASILLA a esta cedula en
     *     el mes actual (Texto107 / macro CalculoReteFteMesPas)
     */
    public HuskPurchaseCalculation calculate(
            HuskPurchaseRequest request,
            ControlRecord controlRecord,
            BigDecimal monthlyAccumulatedGrossValue,
            BigDecimal monthlyAccumulatedWithholding) {
        // W_AlmSana_AfterUpdate: PorcAlmSana = (W_AlmSana * 100) / Muestra.
        BigDecimal sampleSize = sampleSize(controlRecord);
        BigDecimal almondPercentage = request.almondWeight()
                .multiply(HUNDRED)
                .divide(sampleSize, MathContext.DECIMAL64)
                .setScale(SCALE, RoundingMode.HALF_UP);

        // Destare_LostFocus: sin split Kilos_Verdes, resta directa.
        BigDecimal netKg = request.grossKg().subtract(request.tareKg()).setScale(SCALE, RoundingMode.HALF_UP);

        // Descuento_Fro_LostFocus: Vr_Kilo = (Pr_AlmSana * PorcAlmSana / BasePasilla) - Costos.
        BigDecimal unitPrice = request.pointPrice()
                .multiply(almondPercentage)
                .divide(controlRecord.getBaseHusk(), MathContext.DECIMAL64)
                .subtract(request.costs())
                .setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal grossValue = unitPrice.multiply(netKg).setScale(SCALE, RoundingMode.HALF_UP);
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

        // SIN VERIFICAR: CalculoReteFteMesPas solo abre el reporte "ReteMesCursoPas" y copia
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

        BigDecimal netToPay = grossValue
                .subtract(associateContribution)
                .subtract(cooperativeDiscount)
                .subtract(withholding)
                .subtract(request.shrinkageDiscount())
                .subtract(request.otherDiscounts())
                .setScale(SCALE, RoundingMode.HALF_UP);

        return new HuskPurchaseCalculation(
                netKg,
                almondPercentage,
                unitPrice,
                grossValue,
                inventoryValue,
                associateContribution,
                cooperativeDiscount,
                withholding,
                netToPay);
    }

    private BigDecimal sampleSize(ControlRecord controlRecord) {
        BigDecimal sampleSize = controlRecord.getSampleSize();
        if (sampleSize.signum() == 0) {
            throw new BusinessRuleException("El tamaño de muestra configurado no puede ser cero");
        }
        return sampleSize;
    }
}
