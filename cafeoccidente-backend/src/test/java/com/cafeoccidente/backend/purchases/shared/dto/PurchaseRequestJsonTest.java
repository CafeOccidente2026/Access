package com.cafeoccidente.backend.purchases.shared.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * El JSON real del frontend tiene que deserializarse con el mapper de la aplicacion: en preview
 * manda payment null. Ademas, un cliente viejo o alterado que todavia mande factura, precios,
 * costos, bonificacion o datos del vendedor no rompe la peticion: esos campos ya no existen en el
 * request y se ignoran (6b, los pone el servidor).
 */
@SpringBootTest
class PurchaseRequestJsonTest {

    @Autowired
    private JsonMapper json;

    @Test
    void theFormsJsonDeserializes() {
        for (Class<?> type : List.of(DryCoffeePurchaseRequest.class, GreenCoffeePurchaseRequest.class,
                HuskPurchaseRequest.class)) {
            assertThat(json.readValue("{\"idNumber\":\"1\",\"payment\":null}", type)).as(type.getSimpleName()).isNotNull();
        }
        DryCoffeePurchaseRequest withPayment = json.readValue("{\"payment\":{\"cashAmount\":10,\"checkAmount\":5,"
                + "\"transferAmount\":0,\"cardAmount\":0,\"checkNumber\":\"777\"}}", DryCoffeePurchaseRequest.class);
        assertThat(withPayment.payment().checkAmount()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    void fieldsThatTheServerResolvesAreIgnoredIfTheClientSendsThem() {
        String tampered = "{\"idNumber\":\"1\",\"invoiceNumber\":1,\"healthyUnitPrice\":999999,\"bonus\":9999,"
                + "\"costs\":1,\"pointPrice\":999999,\"firstName\":\"FALSO\",\"lastName\":\"FALSO\","
                + "\"growerType\":\"S\",\"withholdingExempt\":true}";
        for (Class<?> type : List.of(DryCoffeePurchaseRequest.class, GreenCoffeePurchaseRequest.class,
                HuskPurchaseRequest.class)) {
            assertThat(json.readValue(tampered, type)).as(type.getSimpleName()).isNotNull();
        }
    }
}
