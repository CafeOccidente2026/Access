package com.cafeoccidente.backend.purchases.shared.service;

/**
 * La resolucion DIAN (rango de factura autorizado) es por AGENCIA, no por modulo de compra
 * (ControlRecord.resolutionFrom/resolutionTo no distingue Seco/Verde/Pasilla/Otros) - todos los
 * modulos de una agencia comparten un solo secuencial de factura. Antes cada
 * {@code *PurchaseServiceImpl.nextInvoiceNumber()} miraba solo su propia tabla, lo que podia
 * repetir numeros entre modulos de la misma agencia; este servicio centraliza el maximo real
 * mirando las 4 tablas de compra.
 */
public interface PurchaseInvoiceNumberService {

    /** Maximo numero de factura ya usado por esta agencia, en cualquier modulo. Null si ninguno. */
    Integer findMaxUsed(Long agencyId);
}
