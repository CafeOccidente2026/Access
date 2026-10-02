package com.cafeoccidente.backend.purchases.shared.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseRequest;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * withWithholdingExempt es la copia que usan los 5 servicios para imponer la exencion resuelta por el
 * servidor: tiene que cambiar solo ese campo (un argumento corrido cambiaria kilos o valores).
 */
class PurchaseRequestWithholdingExemptTest {

    @Test
    void copiesEveryFieldAndChangesOnlyTheExemption() throws Exception {
        for (Class<?> type : List.of(DryCoffeePurchaseRequest.class, GreenCoffeePurchaseRequest.class,
                HuskPurchaseRequest.class, OtherCoffeePurchaseRequest.class, FertiFuturoPurchaseRequest.class)) {
            RecordComponent[] components = type.getRecordComponents();
            Object[] values = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                values[i] = sample(types[i], i);
            }
            Object original = type.getDeclaredConstructor(types).newInstance(values);
            Object copy = type.getMethod("withWithholdingExempt", boolean.class).invoke(original, true);

            for (RecordComponent c : components) {
                Object expected = c.getName().equals("withholdingExempt") ? Boolean.TRUE : c.getAccessor().invoke(original);
                assertThat(c.getAccessor().invoke(copy)).as(type.getSimpleName() + "." + c.getName()).isEqualTo(expected);
            }
        }
    }

    /** Un valor distinto por posicion, para detectar argumentos corridos. */
    private static Object sample(Class<?> type, int i) {
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        if (type == Long.class) {
            return (long) i;
        }
        if (type == Integer.class) {
            return i;
        }
        if (type == BigDecimal.class) {
            return BigDecimal.valueOf(i);
        }
        if (type == PurchasePaymentRequest.class) {
            return new PurchasePaymentRequest(BigDecimal.valueOf(i), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, "7");
        }
        return "v" + i;
    }
}
