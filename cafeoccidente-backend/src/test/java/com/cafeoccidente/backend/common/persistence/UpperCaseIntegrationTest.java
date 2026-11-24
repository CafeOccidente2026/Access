package com.cafeoccidente.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Demo del 23-nov ("trans oriente"): un conductor digitado en minusculas queda en mayusculas en la base. */
@SpringBootTest
@Transactional
class UpperCaseIntegrationTest {

    @Autowired
    private GrowerRepository growers;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aConductorTypedInLowerCaseIsStoredInUpperCase() {
        Grower grower = new Grower();
        grower.setIdNumber("990008080");
        grower.setFirstName("juan");
        grower.setLastName("pérez");
        grower.setAddress("vereda el diviso");
        grower.setPhone("3001112233");
        grower.setGrowerType("C");
        grower.setTransportCompany("trans oriente");
        grower.setVehiclePlate("abc123");
        growers.saveAndFlush(grower);

        Map<String, Object> row = jdbc.queryForMap("SELECT primer_nombre, primer_apellido, direccion, empresa_transporte,"
                + " vehiculo FROM terceros WHERE identificacion = '990008080'");
        assertThat(row).containsEntry("primer_nombre", "JUAN").containsEntry("primer_apellido", "PÉREZ")
                .containsEntry("direccion", "VEREDA EL DIVISO").containsEntry("empresa_transporte", "TRANS ORIENTE")
                .containsEntry("vehiculo", "ABC123");
    }
}
