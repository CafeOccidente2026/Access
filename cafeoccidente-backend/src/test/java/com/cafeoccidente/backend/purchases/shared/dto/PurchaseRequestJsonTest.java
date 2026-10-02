package com.cafeoccidente.backend.purchases.shared.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Los formularios ya no mandan withholdingExempt (lo resuelve el servidor) y en preview mandan
 * payment null: el JSON real del frontend tiene que deserializarse con el mapper de la aplicacion.
 * Regresion: con un boolean primitivo, Jackson 3 rechazaba la falta del campo y ninguna compra se
 * podia previsualizar ni guardar.
 */
@SpringBootTest
class PurchaseRequestJsonTest {

    @Autowired
    private JsonMapper json;

    @Test
    void theFormsJsonWithoutWithholdingExemptDeserializes() {
        for (Class<?> type : List.of(DryCoffeePurchaseRequest.class, GreenCoffeePurchaseRequest.class,
                HuskPurchaseRequest.class, OtherCoffeePurchaseRequest.class, FertiFuturoPurchaseRequest.class)) {
            assertThat(json.readValue("{\"idNumber\":\"1\",\"payment\":null}", type)).as(type.getSimpleName()).isNotNull();
        }
        DryCoffeePurchaseRequest withPayment = json.readValue("{\"payment\":{\"cashAmount\":10,\"checkAmount\":5,"
                + "\"transferAmount\":0,\"cardAmount\":0,\"checkNumber\":\"777\"}}", DryCoffeePurchaseRequest.class);
        assertThat(withPayment.payment().checkAmount()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(withPayment.withholdingExempt()).isNull();
    }
}
