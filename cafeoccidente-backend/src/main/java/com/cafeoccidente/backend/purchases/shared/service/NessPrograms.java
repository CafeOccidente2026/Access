package com.cafeoccidente.backend.purchases.shared.service;

import java.util.List;

/**
 * Programas de la tabla NESS, con el texto exacto con el que llegaron del Excel de la Federacion. Compras los
 * cruza por ese texto (GrowerServiceImpl) y Actualiza Cupos NESS solo acepta estos (decision del usuario,
 * 2026-11-04; el RF-28.4 los nombra "NESPRESSO / RN4C / LEY HARVEST" y hay que corregirlo).
 */
public final class NessPrograms {

    public static final String NESPRESSO = "NESPRESSO";
    public static final String LATE_HARVEST = "NESPRESSO LATE HARVEST";
    public static final String REGIONAL_NARINO_4C = "REGIONAL NARIÑO 4C";
    public static final List<String> ALL = List.of(NESPRESSO, LATE_HARVEST, REGIONAL_NARINO_4C);

    private NessPrograms() {
    }
}
