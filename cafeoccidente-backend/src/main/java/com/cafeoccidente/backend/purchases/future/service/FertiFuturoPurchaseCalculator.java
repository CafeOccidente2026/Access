package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.util.MoneyValidation;
import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseRequest;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Reproduce Form_FERTIFUTURO.bas, Castigo_lostFocus. A diferencia de los otros 4 calculadores de
 * compra, usa constantes FIJAS del VBA en vez de leer ControlRecord (confirmado linea por linea:
 * el VBA nunca lee RegControl para muestra/Aporte_Socio/Descuento_Coop/umbral o % de Retefuente en
 * esta cascada) - decision del usuario 2026-09-23: replicar tal cual, no unificar con
 * ControlRecord.
 */
@Component
public class FertiFuturoPurchaseCalculator {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Muestra fija (250) - Castigo_lostFocus/W_AlmSana_AfterUpdate/W_AlmDefec_AfterUpdate nunca
     *  leen ControlRecord.sampleSize aca. */
    private static final BigDecimal SAMPLE_SIZE = new BigDecimal("250");
    /** Umbral de PorcAlmSana desde el cual empieza a sumar IncCalidad (W_AlmSana_AfterUpdate). */
    private static final BigDecimal QUALITY_INCREMENT_THRESHOLD = new BigDecimal("75");
    private static final BigDecimal ASSOCIATE_PERCENTAGE = new BigDecimal("0.02");
    private static final BigDecimal COOPERATIVE_DISCOUNT_PERCENTAGE = new BigDecimal("0.008");
    private static final BigDecimal WITHHOLDING_BASE_THRESHOLD = new BigDecimal("4295000");
    private static final BigDecimal WITHHOLDING_PERCENTAGE = new BigDecimal("0.005");

    /**
     * @param announcementQualityIncrementRate VrIncCalidad crudo del anuncio (0 si no aplica).
     * @param monthlyAccumulatedGrossValue Texto105 (ReteMesCursoFerti) - acumulado propio del modulo.
     * @param monthlyAccumulatedWithholding Texto107 (ReteMesCursoFerti).
     */
    public FertiFuturoPurchaseCalculation calculate(
            FertiFuturoPurchaseRequest request,
            BigDecimal announcementHealthyUnitPrice,
            BigDecimal announcementDefectiveUnitPrice,
            BigDecimal announcementBonus,
            BigDecimal announcementCosts,
            BigDecimal announcementQualityIncrementRate,
            BigDecimal monthlyAccumulatedGrossValue,
            BigDecimal monthlyAccumulatedWithholding) {
        if ("F".equalsIgnoreCase(request.growerType())) {
            throw new BusinessRuleException("No se le puede facturar a un caficultor fallecido");
        }

        // W_AlmSana_AfterUpdate: PorcAlmSana = (W_AlmSana*100)/250; IncCalidad solo suma por encima
        // del umbral de calidad (75).
        BigDecimal healthyPercentage = request.healthyStoredWeight()
                .multiply(HUNDRED)
                .divide(SAMPLE_SIZE, MathContext.DECIMAL64)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal qualityIncrementAmount = healthyPercentage.compareTo(QUALITY_INCREMENT_THRESHOLD) > 0
                ? healthyPercentage.subtract(QUALITY_INCREMENT_THRESHOLD).multiply(announcementQualityIncrementRate)
                : BigDecimal.ZERO;

        // W_AlmDefec_AfterUpdate.
        BigDecimal defectivePercentage = request.defectiveStoredWeight()
                .multiply(HUNDRED)
                .divide(SAMPLE_SIZE, MathContext.DECIMAL64)
                .setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal tareKg = request.grossKg().subtract(request.netKg()).setScale(SCALE, RoundingMode.HALF_UP);

        // Castigo_lostFocus.
        BigDecimal var5 = announcementHealthyUnitPrice.subtract(request.penalty());
        BigDecimal var1 = var5.add(qualityIncrementAmount).add(announcementBonus);
        BigDecimal var2 = request.defectiveStoredWeight().multiply(announcementDefectiveUnitPrice);
        BigDecimal var3 = request.healthyStoredWeight().multiply(var1);
        BigDecimal var4 = var3.add(var2).divide(SAMPLE_SIZE, MathContext.DECIMAL64);

        // Igual convencion de peso entero que el resto de los modulos (COP no tiene centavos) - no
        // esta explicito en este VBA como si lo esta en Form_COMPRAS.bas, pero es la misma moneda.
        BigDecimal unitPrice = roundToWholePeso(var4.subtract(announcementCosts));
        MoneyValidation.requireNonNegative(unitPrice, "Vr. Kilo");
        BigDecimal grossValue = unitPrice.multiply(request.netKg()).setScale(SCALE, RoundingMode.HALF_UP);
        MoneyValidation.requireNonNegative(grossValue, "Vr. Bruto");
        BigDecimal inventoryValue = grossValue;

        BigDecimal associateContribution = BigDecimal.ZERO;
        BigDecimal cooperativeDiscount = BigDecimal.ZERO;
        if ("S".equalsIgnoreCase(request.growerType())) {
            associateContribution = roundToWholePeso(grossValue.multiply(ASSOCIATE_PERCENTAGE));
        } else if ("C".equalsIgnoreCase(request.growerType())) {
            cooperativeDiscount = roundToWholePeso(grossValue.multiply(COOPERATIVE_DISCOUNT_PERCENTAGE));
        }

        BigDecimal thresholdBase = grossValue.add(monthlyAccumulatedGrossValue);
        BigDecimal withholding = BigDecimal.ZERO;
        if (!request.withholdingExempt() && thresholdBase.compareTo(WITHHOLDING_BASE_THRESHOLD) > 0) {
            withholding = roundToWholePeso(
                    thresholdBase.multiply(WITHHOLDING_PERCENTAGE).subtract(monthlyAccumulatedWithholding));
        }

        BigDecimal netToPay = roundToWholePeso(grossValue
                .subtract(associateContribution)
                .subtract(cooperativeDiscount)
                .subtract(withholding)
                .subtract(request.freightDiscount())
                .subtract(request.otherDiscounts()));
        MoneyValidation.requireNonNegative(netToPay, "Neto a Pagar");

        return new FertiFuturoPurchaseCalculation(
                tareKg,
                healthyPercentage,
                defectivePercentage,
                qualityIncrementAmount.setScale(SCALE, RoundingMode.HALF_UP),
                unitPrice,
                grossValue,
                inventoryValue,
                associateContribution,
                cooperativeDiscount,
                withholding,
                netToPay);
    }

    private BigDecimal roundToWholePeso(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
