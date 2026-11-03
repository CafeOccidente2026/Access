package com.cafeoccidente.backend.purchases.remittance.dto;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una factura en los archivos de la remesa, con los valores de las consultas de Access (GeneraPlanoAdd,
 * ParaOperaciones, ParaOpen, ParaAlmacafe). Los nombres van en ingles; entre parentesis la columna del archivo.
 *
 * @param factor            FACTOR / PORC_AS de ParaOperaciones (PorcAlmSana; en pasilla, % Almendra)
 * @param kas               KAS de ParaOperaciones = cantidad x PORC_AS, sin dividir por 100, como Access
 * @param healthyPercentage PORC_AS de GeneraPlano = W_AlmSana / Muestra x 100
 * @param huskPercentage    PORC_AD (PorcAlmDefec)
 * @param announcementBasePrice PR_BASE_TOT: el Pr_Base_CPS del anuncio (columna cruzada en Access)
 * @param bonusTotal        VrSprTtl = bonificacion x cantidad
 * @param purchaseBasePrice SPR_NESS_TOT: el Pr_Base_PC de la compra (columna cruzada en Access)
 */
public record RemittanceLine(
        String idNumber,
        String sellerName,
        LocalDateTime purchaseDateTime,
        String invoice,
        String fundCode,
        String specialType,
        LocalDate announcementDate,
        BigDecimal netKg,
        BigDecimal factor,
        BigDecimal kas,
        BigDecimal grossValue,
        BigDecimal unitPrice,
        BigDecimal healthyPrice,
        BigDecimal bonus,
        BigDecimal healthyPercentage,
        BigDecimal huskPercentage,
        BigDecimal wastePercentage,
        BigDecimal announcementBasePrice,
        BigDecimal bonusTotal,
        BigDecimal purchaseBasePrice) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** W_AlmSana / Muestra x 100; sin muestra en el Registro de Control, 0. */
    public static BigDecimal healthyPercentage(BigDecimal healthyWeight, BigDecimal sampleSize) {
        if (sampleSize == null || sampleSize.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return healthyWeight.multiply(HUNDRED).divide(sampleSize, MathContext.DECIMAL64);
    }
}
