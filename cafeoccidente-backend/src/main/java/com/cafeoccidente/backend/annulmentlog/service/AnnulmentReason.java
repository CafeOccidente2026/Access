package com.cafeoccidente.backend.annulmentlog.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;

/** El motivo de una anulacion: obligatorio, de al menos 10 caracteres sin los espacios de los extremos (26-nov). */
public final class AnnulmentReason {

    public static final int MIN_LENGTH = 10;
    static final String REQUIRED = "El motivo de la anulación es obligatorio (mínimo 10 caracteres)";

    private AnnulmentReason() {
    }

    /** El motivo sin los espacios de los extremos; si no alcanza el minimo, la anulacion no se hace. */
    public static String require(String reason) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.length() < MIN_LENGTH) {
            throw new BusinessRuleException(REQUIRED);
        }
        return trimmed;
    }
}
