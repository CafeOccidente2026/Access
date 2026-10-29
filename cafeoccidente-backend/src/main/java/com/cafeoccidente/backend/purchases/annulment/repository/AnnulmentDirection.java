package com.cafeoccidente.backend.purchases.annulment.repository;

/**
 * Botones |◄ ◄ ► ►| de "Anular Documento" (RN-23.2): la factura a mostrar, por numero, dentro de la
 * agencia de trabajo. Sin factura de partida, Anterior y Siguiente dan la ultima y la primera.
 */
public enum AnnulmentDirection {
    FIRST("min", null),
    PREV("max", "<"),
    NEXT("min", ">"),
    LAST("max", null);

    final String aggregate;
    /** Comparacion contra la factura de partida; null = no depende de ella. */
    final String comparison;

    AnnulmentDirection(String aggregate, String comparison) {
        this.aggregate = aggregate;
        this.comparison = comparison;
    }
}
