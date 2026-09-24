package com.cafeoccidente.backend.purchases.othercoffee.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.util.MoneyValidation;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseRequest;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Reproduce la cascada de Form_COMPRASESP.bas ("Cafés Otros"): ~95% identica a
 * {@link com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculator} (misma
 * fuente VBA, mismos procedimientos Destare_LostFocus/Sacos_LostFocus/Castigo_lostFocus), con UNA
 * diferencia real confirmada linea por linea: en Castigo_lostFocus, COMPRASESP calcula el ajuste
 * por Pr_AlmDefec (var10 = var9*Pr_AlmDefec, equivalente al var4 de Cafe Seco) pero NUNCA lo suma -
 * {@code Vr_Kilo = var11 = var1*var2}, sin {@code + var10}. Confirmado por grep: var10 queda sin
 * usar en todo el archivo. Se replica tal cual (decision del usuario 2026-09-23: fidelidad a
 * Access, no "corregir" una formula que el VBA real nunca tuvo).
 */
@Component
public class OtherCoffeePurchaseCalculator {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param announcementBasePriceLoad valor crudo del anuncio (vrcps en el VBA)
     * @param monthlyAccumulatedGrossValue suma de Vr_Bruto ya registrado para esta cedula en el mes
     *     actual EN ESTE MODULO (CalculoReteFteMesEsp/ReteMesCursoEsp - acumulado independiente del
     *     de Cafe Seco, confirmado por reporte propio en el VBA)
     * @param monthlyAccumulatedWithholding suma de Retefuente ya aplicada a esta cedula en el mes
     *     actual EN ESTE MODULO
     */
    public OtherCoffeePurchaseCalculation calculate(
            OtherCoffeePurchaseRequest request,
            ControlRecord controlRecord,
            BigDecimal announcementBasePriceLoad,
            BigDecimal monthlyAccumulatedGrossValue,
            BigDecimal monthlyAccumulatedWithholding) {
        if ("F".equalsIgnoreCase(request.growerType())) {
            throw new BusinessRuleException("No se le puede facturar a un caficultor fallecido");
        }

        BigDecimal basePriceLoad = announcementBasePriceLoad
                .subtract(request.costs().multiply(BigDecimal.valueOf(controlRecord.getBaseLoad())))
                .setScale(SCALE, RoundingMode.HALF_UP);
        MoneyValidation.requireNonNegative(basePriceLoad, "Precio Base Carga PC");
        MoneyValidation.requireNonNegative(request.healthyUnitPrice(), "Pr Sustentación");

        BigDecimal netKg = request.grossKg().subtract(request.tareKg());

        BigDecimal wastePercentage = wastePercentage(request.totalStoredWeight(), controlRecord);
        BigDecimal defectivePercentageRaw =
                defectivePercentageRaw(request.defectiveStoredWeight(), controlRecord);
        BigDecimal defectivePercentage = defectivePercentageRaw.setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal totalStored = request.defectiveStoredWeight().add(request.healthyStoredWeight());
        if (totalStored.compareTo(request.totalStoredWeight()) != 0) {
            throw new BusinessRuleException(
                    "La almendra total debe ser igual a la suma de la almendra sana mas la defectuosa");
        }

        BigDecimal healthyPercentageRaw = healthyPercentageRaw(request.healthyStoredWeight(), controlRecord);
        BigDecimal healthyPercentage = healthyPercentageRaw.setScale(SCALE, RoundingMode.HALF_UP);

        // Sacos_LostFocus: igual que Cafe Seco.
        BigDecimal var5 = request.healthyUnitPrice().subtract(request.penalty());
        BigDecimal var1 = var5.add(request.bonus());
        if (healthyPercentageRaw.signum() == 0) {
            throw new BusinessRuleException("El porcentaje de almendra sana no puede ser cero");
        }
        BigDecimal var2 = controlRecord.getSpecialtyThreshold().divide(healthyPercentageRaw, MathContext.DECIMAL64);
        // Castigo_lostFocus (COMPRASESP): Vr_Kilo = var1*var2, SIN el ajuste por Pr_AlmDefec que
        // Cafe Seco si suma - ver Javadoc de la clase.
        BigDecimal qualityUnitPrice = var2.multiply(var1);

        BigDecimal unitPrice = roundToWholePeso(qualityUnitPrice);
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

        BigDecimal thresholdBase = grossValue.add(monthlyAccumulatedGrossValue);
        BigDecimal withholding = BigDecimal.ZERO;
        if (!request.withholdingExempt() && thresholdBase.compareTo(controlRecord.getBaseWithholding()) > 0) {
            withholding = roundToWholePeso(thresholdBase.multiply(controlRecord.getWithholdingPercentage())
                    .divide(HUNDRED, MathContext.DECIMAL64)
                    .subtract(monthlyAccumulatedWithholding));
        }

        BigDecimal netToPay = roundToWholePeso(grossValue
                .subtract(associateContribution)
                .subtract(cooperativeDiscount)
                .subtract(withholding)
                .subtract(request.freightDiscount())
                .subtract(request.otherDiscounts()));
        MoneyValidation.requireNonNegative(netToPay, "Neto a Pagar");

        return new OtherCoffeePurchaseCalculation(
                basePriceLoad,
                netKg.setScale(SCALE, RoundingMode.HALF_UP),
                wastePercentage,
                defectivePercentage,
                healthyPercentage,
                unitPrice,
                grossValue,
                inventoryValue,
                associateContribution,
                cooperativeDiscount,
                withholding,
                netToPay);
    }

    public BigDecimal wastePercentage(BigDecimal totalStoredWeight, ControlRecord controlRecord) {
        BigDecimal sampleSize = sampleSize(controlRecord);
        return sampleSize.subtract(totalStoredWeight)
                .divide(sampleSize, MathContext.DECIMAL64)
                .multiply(HUNDRED)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal defectivePercentage(BigDecimal defectiveStoredWeight, ControlRecord controlRecord) {
        return defectivePercentageRaw(defectiveStoredWeight, controlRecord).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal defectivePercentageRaw(BigDecimal defectiveStoredWeight, ControlRecord controlRecord) {
        return defectiveStoredWeight.multiply(HUNDRED).divide(sampleSize(controlRecord), MathContext.DECIMAL64);
    }

    public BigDecimal healthyPercentage(BigDecimal healthyStoredWeight, ControlRecord controlRecord) {
        return healthyPercentageRaw(healthyStoredWeight, controlRecord).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal healthyPercentageRaw(BigDecimal healthyStoredWeight, ControlRecord controlRecord) {
        if (healthyStoredWeight.signum() == 0) {
            throw new BusinessRuleException("La almendra sana no puede ser cero");
        }
        return sampleSize(controlRecord).multiply(BigDecimal.valueOf(controlRecord.getBaseFactor()))
                .divide(healthyStoredWeight, MathContext.DECIMAL64);
    }

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
