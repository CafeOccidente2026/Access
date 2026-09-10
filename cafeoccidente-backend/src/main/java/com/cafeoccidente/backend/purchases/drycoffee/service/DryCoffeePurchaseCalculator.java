package com.cafeoccidente.backend.purchases.drycoffee.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Unica responsabilidad: reproducir la cascada de calculo del formulario Access original
 * (Destare -> Kilos_Netos -> W_TotAlm/W_AlmSana/W_AlmDefec -> PorcMerma/PorcAlmSana/PorcAlmDefec
 * -> Sacos -> Castigo -> Vr_Kilo -> Vr_Bruto -> Aporte_Socio/Descuento_Coop -> Retefuente ->
 * Neto_a_Pagar). No persiste nada ni conoce HTTP: solo hace la matematica.
 *
 * <p>Variables del VBA que no se pudieron mapear con certeza (Texto164, Texto105, Texto107) se
 * usan como cero, marcadas con TODO abajo. Ver README para el detalle de que falta para
 * completarlas (macro CalculoReteFteMes y programa pcompras).
 */
@Component
public class DryCoffeePurchaseCalculator {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // TODO: Texto164 (formula var4 de Sacos_LostFocus) no se pudo mapear con certeza a un campo
    // existente. Se necesita ver la macro CalculoReteFteMes / el programa pcompras.
    private static final BigDecimal TODO_TEXTO164 = BigDecimal.ZERO;
    // TODO: Texto105 (var6 = Vr_Bruto + Texto105 en Castigo_lostFocus), mismo motivo que arriba.
    private static final BigDecimal TODO_TEXTO105 = BigDecimal.ZERO;
    // TODO: Texto107 (restado en la formula de Retefuente), mismo motivo que arriba.
    private static final BigDecimal TODO_TEXTO107 = BigDecimal.ZERO;

    public DryCoffeePurchaseCalculation calculate(DryCoffeePurchaseRequest request, ControlRecord controlRecord) {
        if ("F".equalsIgnoreCase(request.growerType())) {
            throw new BusinessRuleException("No se le puede facturar a un caficultor fallecido");
        }

        BigDecimal netKg = request.grossKg().subtract(request.tareKg());

        BigDecimal sampleSize = controlRecord.getSampleSize();
        if (sampleSize.signum() == 0) {
            throw new BusinessRuleException("El tamaño de muestra configurado no puede ser cero");
        }
        BigDecimal wastePercentage = sampleSize.subtract(request.totalStoredWeight())
                .divide(sampleSize, MathContext.DECIMAL64)
                .multiply(HUNDRED)
                .setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal defectivePercentage = request.defectiveStoredWeight()
                .multiply(HUNDRED)
                .divide(sampleSize, MathContext.DECIMAL64)
                .setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal totalStored = request.defectiveStoredWeight().add(request.healthyStoredWeight());
        if (totalStored.compareTo(request.totalStoredWeight()) != 0) {
            throw new BusinessRuleException(
                    "La almendra total debe ser igual a la suma de la almendra sana mas la defectuosa");
        }

        if (request.healthyStoredWeight().signum() == 0) {
            throw new BusinessRuleException("La almendra sana no puede ser cero");
        }
        BigDecimal healthyPercentage = sampleSize.multiply(BigDecimal.valueOf(controlRecord.getBaseFactor()))
                .divide(request.healthyStoredWeight(), MathContext.DECIMAL64)
                .setScale(SCALE, RoundingMode.HALF_UP);

        // Sacos_LostFocus: precios unitarios intermedios (Texto190/Texto191 en el VBA original).
        BigDecimal var5 = request.healthyUnitPrice().subtract(request.penalty());
        BigDecimal var1 = var5.add(request.bonus());
        if (healthyPercentage.signum() == 0) {
            throw new BusinessRuleException("El porcentaje de almendra sana no puede ser cero");
        }
        BigDecimal var2 = controlRecord.getSpecialtyThreshold().divide(healthyPercentage, MathContext.DECIMAL64);
        BigDecimal var3 = healthyPercentage.multiply(defectivePercentage).divide(HUNDRED, MathContext.DECIMAL64);
        BigDecimal var4 = var3.subtract(TODO_TEXTO164)
                .divide(healthyPercentage, MathContext.DECIMAL64)
                .multiply(request.defectiveUnitPrice());
        BigDecimal qualityUnitPrice = var2.multiply(var1).add(var4);

        // Castigo_lostFocus: Vr_Kilo siempre toma la rama viva del VBA (Texto191).
        BigDecimal unitPrice = qualityUnitPrice.setScale(SCALE, RoundingMode.HALF_UP);
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

        BigDecimal var6 = grossValue.add(TODO_TEXTO105);
        BigDecimal withholding = BigDecimal.ZERO;
        if (!request.withholdingExempt() && var6.compareTo(controlRecord.getBaseWithholding()) > 0) {
            withholding = var6.multiply(controlRecord.getWithholdingPercentage())
                    .divide(HUNDRED, MathContext.DECIMAL64)
                    .subtract(TODO_TEXTO107)
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal netToPay = grossValue
                .subtract(associateContribution)
                .subtract(cooperativeDiscount)
                .subtract(withholding)
                .subtract(request.freightDiscount())
                .subtract(request.otherDiscounts())
                .setScale(SCALE, RoundingMode.HALF_UP);

        return new DryCoffeePurchaseCalculation(
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
}
