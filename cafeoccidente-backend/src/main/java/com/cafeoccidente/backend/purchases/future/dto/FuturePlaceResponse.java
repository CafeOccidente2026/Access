package com.cafeoccidente.backend.purchases.future.dto;

/** Hallazgo #14 (16-nov): Finca, Mpio y Vereda de la ultima compra a futuro de la cedula, para no digitarlos otra vez. */
public record FuturePlaceResponse(String finca, String municipality, String vereda) {
}
