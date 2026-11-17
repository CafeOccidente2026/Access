package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.FuturePlaceResponse;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseResponse;
import com.cafeoccidente.backend.purchases.future.dto.PendingAnnouncementRow;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FuturePurchaseService {

    /** RN-21.4.4 y R6 (17-nov): el anuncio vigente (el ultimo) del especial, fondo RP, de la agencia de trabajo. */
    List<AnnouncementResponse> todayAnnouncements(String specialType);

    FuturePurchaseResponse create(FuturePurchaseRequest request);

    FuturePurchaseResponse findById(Long id);

    /** Hallazgo #14: Finca, Mpio y Vereda de la ultima compra a futuro de la cedula en la agencia; null si no tiene. */
    FuturePlaceResponse lastPlace(String idNumber);

    /**
     * Anunciadas de Hoy de la agencia de trabajo, por anuncio: las anunciadas entre {@code from} y {@code to} (hoy si
     * vienen vacias), o con {@code withBalance} todas las que tienen saldo, de cualquier fecha (HU-31, P2).
     */
    List<PendingAnnouncementRow> pending(LocalDate from, LocalDate to, boolean withBalance);

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
