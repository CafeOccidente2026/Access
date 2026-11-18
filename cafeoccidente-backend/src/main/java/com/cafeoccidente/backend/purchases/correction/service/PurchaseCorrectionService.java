package com.cafeoccidente.backend.purchases.correction.service;

import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.correction.dto.PurchaseCorrection;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import java.math.BigDecimal;

/**
 * Lo comun de corregir una compra de seco, verde o pasilla (HU-05, 11-nov). Cada modulo recalcula con su
 * calculador y llama, dentro de su transaccion: {@link #start}, {@link #settle} antes de guardar la compra y
 * {@link #close} despues.
 */
public interface PurchaseCorrectionService {

    /**
     * Bloquea la compra de la agencia de trabajo, rechaza lo que tampoco se podria anular y arma su contexto: el
     * anuncio original (no el vigente), el Registro de Control, el vendedor con la direccion y el celular nuevos (si
     * vienen) y lo vendido ese dia sin contar esta compra (retencion, Q10).
     */
    PurchaseCorrection start(AnnulmentModule module, Long id, String address, String cellphone);

    /** Cupo del anuncio y compra anunciada con los kilos nuevos, formas de pago, cuenta por cobrar e inventario viejo. */
    void settle(PurchaseCorrection correction, BigDecimal netKg, PurchasePaymentRequest payment);

    /** Marca la compra como corregida y deja en el Historial el antes y el despues. */
    void close(PurchaseCorrection correction);
}
