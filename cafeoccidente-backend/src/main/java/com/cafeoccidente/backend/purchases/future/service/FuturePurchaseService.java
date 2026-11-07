package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseResponse;
import com.cafeoccidente.backend.purchases.future.dto.PendingAnnouncementRow;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import java.math.BigDecimal;
import java.util.List;

public interface FuturePurchaseService {

    /** RN-21.4.3 y RN-21.4.4: los anuncios de hoy (Colombia) del especial, fondo RP, de la agencia de trabajo. */
    List<AnnouncementResponse> todayAnnouncements(String specialType);

    FuturePurchaseResponse create(FuturePurchaseRequest request);

    FuturePurchaseResponse findById(Long id);

    /** Anunciadas Hoy (HU-31, P2): compras a futuro con saldo de la agencia de trabajo, de cualquier fecha, por anuncio. */
    List<PendingAnnouncementRow> pending();

    /** RN-21.5.2: las compras anunciadas de la cedula con saldo, de la agencia de trabajo. */
    List<FuturePurchaseResponse> openFor(String idNumber);

    /**
     * La compra anunciada que se va a facturar, de la agencia de trabajo y de esa cedula (si viene). Con {@code lock}
     * la fila queda bloqueada hasta el fin de la transaccion de la compra.
     */
    FuturePurchase forBilling(Long id, String idNumber, boolean lock);

    /** Su anuncio, precios y especial, como los copia la macro "Establecer Anunciados" a la compra. */
    AnnouncementResponse billingAnnouncement(FuturePurchase purchase, Long fundId);

    /** RN-21.5.3: descuenta los kilos netos del saldo, en la transaccion de la compra (ver DeliveryRule). */
    void deliver(FuturePurchase purchase, BigDecimal deliveryKg, BigDecimal netKg);
}
