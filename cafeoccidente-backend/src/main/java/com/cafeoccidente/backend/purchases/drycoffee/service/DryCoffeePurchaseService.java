package com.cafeoccidente.backend.purchases.drycoffee.service;

import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.QualityPercentagesResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.SpecialInfoResponse;
import java.math.BigDecimal;

public interface DryCoffeePurchaseService {
    DryCoffeePurchaseResponse create(DryCoffeePurchaseRequest request);

    DryCoffeePurchaseResponse findById(Long id);

    /** Recalcula la cascada completa sin persistir (pasos Castigo/Descuento Fro/Otros Desctos). */
    DryCoffeePurchaseCalculation preview(DryCoffeePurchaseRequest request);

    /** Factura: siguiente consecutivo dentro del rango autorizado (paso "Fondo"). */
    NextInvoiceNumberResponse nextInvoiceNumber();

    /** Cod Prod + datos del anuncio vigente (paso "Especial"). */
    SpecialInfoResponse specialInfo(Long agencyId, Long fundId, String specialType);

    /** Porcentajes independientes (pasos "Peso Tot Alm"/"Peso Tot Pasilla"/"Peso Alm Sana"). */
    QualityPercentagesResponse qualityPercentages(
            BigDecimal totalStoredWeight, BigDecimal defectiveStoredWeight, BigDecimal healthyStoredWeight);
}
