package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRestoreRow;
import java.util.List;

/**
 * "Restaurar Compras Anunciadas" (Access "RestauraFutureBuys", solo el Administrador): recalcula desde cero el saldo de
 * las compras anunciadas de una cedula en la agencia de trabajo. Nunca suma: la anulacion ya devuelve los kilos sola.
 */
public interface FuturePurchaseRestoreService {

    /** Lo que quedaria: saldo guardado y saldo recalculado, sin escribir nada. */
    List<FuturePurchaseRestoreRow> preview(String idNumber);

    /** Pone el saldo recalculado en las que no cuadran y lo deja en el Historial. */
    List<FuturePurchaseRestoreRow> restore(String idNumber);
}
