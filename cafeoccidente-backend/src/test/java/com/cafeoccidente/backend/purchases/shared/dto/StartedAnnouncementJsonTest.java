package com.cafeoccidente.backend.purchases.shared.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Hallazgo #6: con el constructor corto de los tests, el JSON sigue llegando por el constructor completo. */
class StartedAnnouncementJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void theStartedAnnouncementNumberArrivesInTheThreeRequests() {
        String body = "{\"fundId\": 1, \"idNumber\": \"1\", \"startedAnnouncementNumber\": 31}";
        assertThat(json.readValue(body, DryCoffeePurchaseRequest.class).startedAnnouncementNumber()).isEqualTo(31);
        assertThat(json.readValue(body, GreenCoffeePurchaseRequest.class).startedAnnouncementNumber()).isEqualTo(31);
        assertThat(json.readValue(body, HuskPurchaseRequest.class).startedAnnouncementNumber()).isEqualTo(31);
        assertThat(json.readValue("{\"fundId\": 1}", HuskPurchaseRequest.class).startedAnnouncementNumber()).isNull();
    }
}
