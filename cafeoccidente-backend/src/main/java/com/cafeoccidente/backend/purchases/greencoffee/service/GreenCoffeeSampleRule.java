package com.cafeoccidente.backend.purchases.greencoffee.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculator;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeeSample;
import java.math.BigDecimal;
import java.util.stream.Stream;

/**
 * Cafe verde (HU-23 CA-2, Q23): la muestra sale del Registro de Control. Mientras alguno de los 3
 * pesos de verde este en 0 no se puede registrar la compra (decision del usuario, 2026-10-28: los 2
 * precios de verde del Registro no cuentan, porque desde Q36 salen del anuncio CV). Peso Alm Sana
 * se toma tal cual, aunque no sume con los otros dos.
 */
public final class GreenCoffeeSampleRule {

    public static final String CODE = "VERDE_SIN_VALORES";
    static final String MISSING = "Faltan los valores de café verde en el Registro de Control (Peso Tot Alm,"
            + " Peso Tot Pasilla y Peso Alm Sana). Pídale al Administrador que los digite.";

    private GreenCoffeeSampleRule() {
    }

    /** La muestra, o null si falta alguno de los 3 pesos. */
    public static GreenCoffeeSample sampleOrNull(ControlRecord controlRecord, DryCoffeePurchaseCalculator formulas) {
        BigDecimal total = controlRecord.getGreenTotalStoredWeight();
        BigDecimal husk = controlRecord.getGreenTotalHuskWeight();
        BigDecimal healthy = controlRecord.getGreenHealthyStoredWeight();
        if (Stream.of(total, husk, healthy).anyMatch(weight -> weight == null || weight.signum() <= 0)) {
            return null;
        }
        return new GreenCoffeeSample(
                total, formulas.wastePercentage(total, controlRecord),
                husk, formulas.defectivePercentage(husk, controlRecord),
                healthy, formulas.healthyPercentage(healthy, controlRecord));
    }

    /** La muestra para calcular o guardar una compra; sin los 3 pesos, la compra no se registra. */
    public static GreenCoffeeSample require(ControlRecord controlRecord, DryCoffeePurchaseCalculator formulas) {
        GreenCoffeeSample sample = sampleOrNull(controlRecord, formulas);
        if (sample == null) {
            throw new BusinessRuleException(MISSING, CODE);
        }
        return sample;
    }

    /**
     * Prueba a mano del 16-nov: al guardar el Registro de Control, la muestra de verde (si se digito) no puede pasar
     * la muestra; la pasilla y la almendra sana no pueden pasar el total. En 0 sigue sin verde (sampleOrNull).
     */
    public static void checkValues(BigDecimal sampleSize, BigDecimal total, BigDecimal husk, BigDecimal healthy) {
        if (total == null || total.signum() <= 0) {
            return;
        }
        if (sampleSize != null && total.compareTo(sampleSize) > 0) {
            throw new BusinessRuleException("Peso Tot Alm de cafe verde no puede ser mayor que la muestra ("
                    + DryCoffeePurchaseCalculator.grams(sampleSize) + ")");
        }
        if (husk != null && husk.compareTo(total) > 0) {
            throw new BusinessRuleException("Peso Tot Pasilla de cafe verde no puede ser mayor que Peso Tot Alm ("
                    + DryCoffeePurchaseCalculator.grams(total) + ")");
        }
        if (healthy != null && healthy.compareTo(total) > 0) {
            throw new BusinessRuleException("Peso Alm Sana de cafe verde no puede ser mayor que Peso Tot Alm ("
                    + DryCoffeePurchaseCalculator.grams(total) + ")");
        }
    }
}
