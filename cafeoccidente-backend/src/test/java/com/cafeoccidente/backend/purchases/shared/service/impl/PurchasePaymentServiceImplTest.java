package com.cafeoccidente.backend.purchases.shared.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Cascada FPef/FPch/FPtx/FPdat de Form_COMPRAS.bas: tiene que sumar exacto el Neto a Pagar. */
class PurchasePaymentServiceImplTest {

    private static final BigDecimal NET = new BigDecimal("4944624");
    private final PurchasePaymentServiceImpl service = new PurchasePaymentServiceImpl(mock(JdbcTemplate.class));

    private static PurchasePaymentRequest pay(String cash, String check, String transfer, String card, String number) {
        return new PurchasePaymentRequest(new BigDecimal(cash), new BigDecimal(check), new BigDecimal(transfer),
                new BigDecimal(card), number);
    }

    @Test
    void summarizesTheMethodLikeTheRealData() {
        assertThat(service.validate(pay("4944624", "0", "0", "0", null), NET).paymentMethod()).isEqualTo("EFECTIVO");
        assertThat(service.validate(pay("0", "4944624", "0", "0", "5571"), NET))
                .isEqualTo(new com.cafeoccidente.backend.purchases.shared.service.PurchasePaymentService.ResolvedPayment(
                        "CHEQUE", "5571"));
        assertThat(service.validate(pay("1944624", "3000000", "0", "0", "5379"), NET).paymentMethod()).isEqualTo("MIXTO");
        assertThat(service.validate(pay("0", "0", "0", "4944624", null), NET).paymentMethod()).isEqualTo("DATAFONO");
        // Las 6 compras reales en $0: sin forma de pago, quedan como efectivo en $0 (nunca llegan a Caja).
        assertThat(service.validate(pay("0", "0", "0", "0", null), BigDecimal.ZERO).paymentMethod()).isEqualTo("EFECTIVO");
    }

    @Test
    void rejectsAnUnbalancedPaymentEitherWay() {
        assertThatThrownBy(() -> service.validate(pay("4944623", "0", "0", "0", null), NET))
                .isInstanceOf(BusinessRuleException.class).hasMessage("FORMA DE PAGO DESCUADRADA, REVISE");
        assertThatThrownBy(() -> service.validate(pay("4944624", "1", "0", "0", "5"), NET))
                .hasMessage("FORMA DE PAGO DESCUADRADA, REVISE");
        assertThatThrownBy(() -> service.validate(null, NET)).hasMessage("FORMA DE PAGO DESCUADRADA, REVISE");
    }

    @Test
    void aCheckNeedsItsNumber() {
        for (String number : new String[] {null, "", "  ", "0", "12A", "1234567890"}) {
            assertThatThrownBy(() -> service.validate(pay("0", "4944624", "0", "0", number), NET))
                    .as(String.valueOf(number)).hasMessage("Digite el numero de cheque");
        }
        // Sin monto en cheque el numero no importa y no se guarda.
        assertThat(service.validate(pay("4944624", "0", "0", "0", "99"), NET).checkNumber()).isNull();
    }
}
