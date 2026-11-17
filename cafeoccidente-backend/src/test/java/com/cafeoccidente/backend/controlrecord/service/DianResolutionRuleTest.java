package com.cafeoccidente.backend.controlrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** R1 (17-nov): vencimiento y rango de la resolucion DIAN, como VigenciaRes (meses) de Access. */
class DianResolutionRuleTest {

    private static ControlRecord resolution(LocalDate date, int months, int to) {
        ControlRecord record = new ControlRecord();
        record.setResolutionDate(date);
        record.setValidity(months);
        record.setResolutionFrom(1);
        record.setResolutionTo(to);
        return record;
    }

    private final ControlRecord tambo = resolution(LocalDate.of(2026, 7, 4), 24, 500);

    @Test
    void itExpiresTheResolutionDatePlusItsMonths() {
        assertThat(DianResolutionRule.expiration(tambo)).isEqualTo(LocalDate.of(2028, 7, 4));
        assertThat(DianResolutionRule.daysLeft(tambo, LocalDate.of(2028, 6, 24))).isEqualTo(10);
    }

    @Test
    void theWarningStartsTenDaysBefore() {
        assertThat(DianResolutionRule.alerts(tambo, LocalDate.of(2028, 6, 23))).isFalse();
        assertThat(DianResolutionRule.alerts(tambo, LocalDate.of(2028, 6, 24))).isTrue();
        assertThat(DianResolutionRule.alerts(tambo, LocalDate.of(2028, 8, 1))).isTrue();
    }

    @Test
    void theLastDayStillInvoicesAndTheNextOneIsBlocked() {
        assertThatCode(() -> DianResolutionRule.requireUsable(tambo, 10, LocalDate.of(2028, 7, 4))).doesNotThrowAnyException();
        assertThatThrownBy(() -> DianResolutionRule.requireUsable(tambo, 10, LocalDate.of(2028, 7, 5)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("La resolución DIAN de esta agencia venció el 04/07/2028. El Administrador debe registrar"
                        + " la nueva resolución en el Registro de Control.");
    }

    @Test
    void aNumberPastHastaIsBlocked() {
        assertThatCode(() -> DianResolutionRule.requireUsable(tambo, 500, LocalDate.of(2027, 1, 1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> DianResolutionRule.requireUsable(tambo, 501, LocalDate.of(2027, 1, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("Se acabaron los números de la resolución DIAN de esta agencia (hasta 500)");
    }
}
