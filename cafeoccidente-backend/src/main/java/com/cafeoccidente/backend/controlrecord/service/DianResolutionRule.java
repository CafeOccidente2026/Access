package com.cafeoccidente.backend.controlrecord.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Pedido del area (17-nov, R1): la resolucion DIAN de facturacion de cada agencia (Registro de Control: Resol DIAN,
 * Fecha Res, Desde Res, Hasta Res y Vigencia en meses, como VigenciaRes de Access). Access solo la copiaba en la
 * factura; aqui ademas se avisa antes de que venza y se bloquea la factura cuando vence o se acaban los numeros.
 */
public final class DianResolutionRule {

    /** Dias antes del vencimiento desde los que se avisa (pedido del area, 17-nov). */
    public static final int ALERT_DAYS = 10;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DianResolutionRule() {
    }

    /** Fecha de la resolucion mas la vigencia en meses: ultimo dia en que se factura con ella. */
    public static LocalDate expiration(ControlRecord controlRecord) {
        return controlRecord.getResolutionDate().plusMonths(controlRecord.getValidity());
    }

    public static long daysLeft(ControlRecord controlRecord, LocalDate today) {
        return ChronoUnit.DAYS.between(today, expiration(controlRecord));
    }

    /** Se avisa desde {@link #ALERT_DAYS} dias antes y mientras no se registre una resolucion nueva. */
    public static boolean alerts(ControlRecord controlRecord, LocalDate today) {
        return daysLeft(controlRecord, today) <= ALERT_DAYS;
    }

    /** Bloquea la factura si la resolucion vencio o si el numero pasa de Hasta Res. */
    public static void requireUsable(ControlRecord controlRecord, int invoiceNumber, LocalDate today) {
        if (daysLeft(controlRecord, today) < 0) {
            throw new BusinessRuleException("La resolución DIAN de esta agencia venció el "
                    + DATE.format(expiration(controlRecord))
                    + ". El Administrador debe registrar la nueva resolución en el Registro de Control.");
        }
        if (invoiceNumber > controlRecord.getResolutionTo()) {
            throw new BusinessRuleException("Se acabaron los números de la resolución DIAN de esta agencia (hasta "
                    + controlRecord.getResolutionTo()
                    + "). El Administrador debe registrar la nueva resolución en el Registro de Control.");
        }
    }
}
