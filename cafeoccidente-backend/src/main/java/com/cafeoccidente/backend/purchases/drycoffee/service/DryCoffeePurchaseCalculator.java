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
 * Neto_a_Pagar). No persiste nada ni conoce HTTP: solo hace la matematica; el acumulado mensual
 * del caficultor y los valores del anuncio los consulta el servicio y se pasan como parametro.
 */
@Component
public class DryCoffeePurchaseCalculator {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param announcementBasePriceLoad valor crudo del anuncio (vrcps en el VBA)
     * @param announcementDefectiveUnitPrice Pr_AlmDefec vigente en el anuncio (ver
     *     Announcement.defectiveUnitPrice)
     * @param monthlyAccumulatedGrossValue suma de Vr_Bruto ya registrado para esta cedula en el
     *     mes actual (Texto105 en el VBA / macro CalculoReteFteMes)
     * @param monthlyAccumulatedWithholding suma de Retefuente ya aplicada a esta cedula en el mes
     *     actual (Texto107 en el VBA / macro CalculoReteFteMes)
     */
    public DryCoffeePurchaseCalculation calculate(
            DryCoffeePurchaseRequest request,
            ControlRecord controlRecord,
            BigDecimal announcementBasePriceLoad,
            BigDecimal announcementDefectiveUnitPrice,
            BigDecimal monthlyAccumulatedGrossValue,
            BigDecimal monthlyAccumulatedWithholding) {
        if ("F".equalsIgnoreCase(request.growerType())) {
            throw new BusinessRuleException("No se le puede facturar a un caficultor fallecido");
        }

        // Cedula_LostFocus: Pr_Base_PC = vrcps - (Costos * BaseCarga). BaseCarga = ControlRecord.baseLoad (Texto176).
        BigDecimal basePriceLoad = announcementBasePriceLoad
                .subtract(request.costs().multiply(BigDecimal.valueOf(controlRecord.getBaseLoad())))
                .setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal netKg = request.grossKg().subtract(request.tareKg());

        BigDecimal wastePercentage = wastePercentage(request.totalStoredWeight(), controlRecord);
        BigDecimal defectivePercentage = defectivePercentage(request.defectiveStoredWeight(), controlRecord);

        BigDecimal totalStored = request.defectiveStoredWeight().add(request.healthyStoredWeight());
        if (totalStored.compareTo(request.totalStoredWeight()) != 0) {
            throw new BusinessRuleException(
                    "La almendra total debe ser igual a la suma de la almendra sana mas la defectuosa");
        }

        BigDecimal healthyPercentage = healthyPercentage(request.healthyStoredWeight(), controlRecord);

        // Sacos_LostFocus: precios unitarios intermedios (Texto190/Texto191 en el VBA original).
        BigDecimal var5 = request.healthyUnitPrice().subtract(request.penalty());
        BigDecimal var1 = var5.add(request.bonus());
        if (healthyPercentage.signum() == 0) {
            throw new BusinessRuleException("El porcentaje de almendra sana no puede ser cero");
        }
        BigDecimal var2 = controlRecord.getSpecialtyThreshold().divide(healthyPercentage, MathContext.DECIMAL64);
        BigDecimal var3 = healthyPercentage.multiply(defectivePercentage).divide(HUNDRED, MathContext.DECIMAL64);
        // var4 = (var3 - PorcKgPasProm) / PorcAlmSana * Pr_AlmDefec.
        // PorcKgPasProm = ControlRecord.avgHuskPercentage (Texto164).
        // Pr_AlmDefec = Announcement.defectiveUnitPrice (lo trae el anuncio vigente).
        BigDecimal var4 = var3.subtract(controlRecord.getAvgHuskPercentage())
                .divide(healthyPercentage, MathContext.DECIMAL64)
                .multiply(announcementDefectiveUnitPrice);
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

        // Retefuente incremental sobre el acumulado mensual del caficultor (macro CalculoReteFteMes):
        // el umbral y el porcentaje se aplican sobre (Vr_Bruto de esta compra + lo ya comprado este
        // mes), y se descuenta la Retefuente ya practicada este mes, dejando solo el diferencial.
        // NOTA a revisar con el negocio: hoy el acumulado solo mira compras del modulo drycoffee.
        // Cuando existan othercoffee/greencoffee/husk habra que decidir si tambien deben sumar.
        BigDecimal thresholdBase = grossValue.add(monthlyAccumulatedGrossValue);
        BigDecimal withholding = BigDecimal.ZERO;
        if (!request.withholdingExempt() && thresholdBase.compareTo(controlRecord.getBaseWithholding()) > 0) {
            withholding = thresholdBase.multiply(controlRecord.getWithholdingPercentage())
                    .divide(HUNDRED, MathContext.DECIMAL64)
                    .subtract(monthlyAccumulatedWithholding)
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

    /** PorcMerma: paso "Peso Tot Alm" (independiente del resto de la cascada). */
    public BigDecimal wastePercentage(BigDecimal totalStoredWeight, ControlRecord controlRecord) {
        BigDecimal sampleSize = sampleSize(controlRecord);
        return sampleSize.subtract(totalStoredWeight)
                .divide(sampleSize, MathContext.DECIMAL64)
                .multiply(HUNDRED)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** PorcAlmDefec: paso "Peso Tot Pasilla" (independiente del resto de la cascada). */
    public BigDecimal defectivePercentage(BigDecimal defectiveStoredWeight, ControlRecord controlRecord) {
        return defectiveStoredWeight
                .multiply(HUNDRED)
                .divide(sampleSize(controlRecord), MathContext.DECIMAL64)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** PorcAlmSana (Factor): paso "Peso Alm Sana" (independiente del resto de la cascada). */
    public BigDecimal healthyPercentage(BigDecimal healthyStoredWeight, ControlRecord controlRecord) {
        if (healthyStoredWeight.signum() == 0) {
            throw new BusinessRuleException("La almendra sana no puede ser cero");
        }
        return sampleSize(controlRecord).multiply(BigDecimal.valueOf(controlRecord.getBaseFactor()))
                .divide(healthyStoredWeight, MathContext.DECIMAL64)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sampleSize(ControlRecord controlRecord) {
        BigDecimal sampleSize = controlRecord.getSampleSize();
        if (sampleSize.signum() == 0) {
            throw new BusinessRuleException("El tamaño de muestra configurado no puede ser cero");
        }
        return sampleSize;
    }
}
