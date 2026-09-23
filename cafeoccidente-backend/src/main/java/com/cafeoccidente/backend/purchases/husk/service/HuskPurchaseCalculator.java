package com.cafeoccidente.backend.purchases.husk.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.util.MoneyValidation;
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
        // W_AlmSana_AfterUpdate: PorcAlmSana = (W_AlmSana * 100) / Muestra. Igual que en
        // Form_COMPRAS.bas (Sacos_LostFocus), el VBA no redondea antes de usarla en Vr_Kilo, solo
        // al mostrarla en pantalla (DecimalPlaces=2 es formato de display, no trunca el valor
        // ligado) - el control tiene la misma firma que el "Factor"/PorcAlmSana de Cafe Seco, donde
        // esto se confirmo contra compras_migrar.csv. Se guarda la version con precision completa
        // para el calculo y se redondea solo la que se expone en la respuesta.
        BigDecimal sampleSize = sampleSize(controlRecord);
        BigDecimal almondPercentageRaw = request.almondWeight()
                .multiply(HUNDRED)
                .divide(sampleSize, MathContext.DECIMAL64);
        BigDecimal almondPercentage = almondPercentageRaw.setScale(SCALE, RoundingMode.HALF_UP);

        // Pr Sustentacion en Pasilla = Pr_AlmSana (Punto de Compra) tal cual, sin formula propia
        // (ver docs/informe-auditoria-completa-2026-09-22.md seccion 2) - se valida igual porque
        // alimenta Vr_Kilo mas abajo.
        MoneyValidation.requireNonNegative(request.pointPrice(), "Pr Sustentación");

        // Destare_LostFocus: sin split Kilos_Verdes, resta directa.
        BigDecimal netKg = request.grossKg().subtract(request.tareKg()).setScale(SCALE, RoundingMode.HALF_UP);

        // Descuento_Fro_LostFocus: Vr_Kilo = (Pr_AlmSana * PorcAlmSana / BasePasilla) - Costos.
        // Vr_Kilo, Vr_Bruto, Aporte_Socio/Descuento_Coop, Retefuente y Neto_a_Pagar son controles
        // ligados a campos sin decimales (el COP no tiene centavos) igual que en Cafe Seco - mismo
        // control Vr_Kilo (DecimalPlaces=0, Format=Standard) en Form_PASILLA.bas que en
        // Form_COMPRAS.bas. No hay CSV historico de Pasilla para confirmarlo empiricamente (a
        // diferencia de Seco, ver docs/informe-auditoria-completa-2026-09-22.md seccion 3), pero la
        // firma del control es identica y la convencion de peso entero es del COP, no del formulario.
        BigDecimal unitPrice = roundToWholePeso(request.pointPrice()
                .multiply(almondPercentageRaw)
                .divide(controlRecord.getBaseHusk(), MathContext.DECIMAL64)
                .subtract(request.costs()));
        MoneyValidation.requireNonNegative(unitPrice, "Vr. Kilo");

        BigDecimal grossValue = unitPrice.multiply(netKg).setScale(SCALE, RoundingMode.HALF_UP);
        MoneyValidation.requireNonNegative(grossValue, "Vr. Bruto");
        BigDecimal inventoryValue = grossValue;

        BigDecimal associateContribution = BigDecimal.ZERO;
        BigDecimal cooperativeDiscount = BigDecimal.ZERO;
        if ("S".equalsIgnoreCase(request.growerType())) {
            associateContribution = roundToWholePeso(grossValue.multiply(controlRecord.getAssociatePercentage())
                    .divide(HUNDRED, MathContext.DECIMAL64));
        } else if ("C".equalsIgnoreCase(request.growerType())) {
            cooperativeDiscount = roundToWholePeso(grossValue.multiply(controlRecord.getNonAssociateDiscount())
                    .divide(HUNDRED, MathContext.DECIMAL64));
        }

        // SIN VERIFICAR: CalculoReteFteMesPas solo abre el reporte "ReteMesCursoPas" y copia
        // TotalVrBruto/TotalRetefuente a Texto105/Texto107; el RecordSource del reporte no esta en
        // el export de VBA disponible. sumMonthlyTotalsByIdNumber es el mejor esfuerzo hasta confirmarlo.
        BigDecimal var6 = grossValue.add(monthlyAccumulatedGrossValue);
        BigDecimal withholding = BigDecimal.ZERO;
        if (!request.withholdingExempt() && var6.compareTo(controlRecord.getBaseWithholding()) > 0) {
            withholding = roundToWholePeso(var6.multiply(controlRecord.getWithholdingPercentage())
                    .divide(HUNDRED, MathContext.DECIMAL64)
                    .subtract(monthlyAccumulatedWithholding));
        }

        BigDecimal netToPay = roundToWholePeso(grossValue
                .subtract(associateContribution)
                .subtract(cooperativeDiscount)
                .subtract(withholding)
                .subtract(request.shrinkageDiscount())
                .subtract(request.otherDiscounts()));
        MoneyValidation.requireNonNegative(netToPay, "Neto a Pagar");

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

    /** Redondea a peso entero (COP no tiene centavos) y vuelve a escalar a SCALE para poder operar
     *  con el resto de la cascada, que siempre trabaja en BigDecimal de 2 decimales. */
    private BigDecimal roundToWholePeso(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sampleSize(ControlRecord controlRecord) {
        BigDecimal sampleSize = controlRecord.getSampleSize();
        if (sampleSize.signum() == 0) {
            throw new BusinessRuleException("El tamaño de muestra configurado no puede ser cero");
        }
        return sampleSize;
    }
}
