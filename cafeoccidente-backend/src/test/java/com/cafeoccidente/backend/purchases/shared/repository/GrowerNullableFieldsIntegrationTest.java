package com.cafeoccidente.backend.purchases.shared.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V34: Asociados de Access tiene filas sin "1er Apellido" (asociaciones) y sin Agencia; se guardan
 * tal cual y Compras a Futuro puede copiar ese apellido nulo. Transaccional: no deja nada en la base.
 */
@SpringBootTest
@Transactional
class GrowerNullableFieldsIntegrationTest {

    @Autowired
    private GrowerRepository growerRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void growerWithoutAgencyOrLastNameIsSavedAsIs() {
        Grower grower = new Grower();
        grower.setIdNumber("V34-TEST-0001");
        grower.setFirstName("FUNDACION SUYUSAMA");
        grower.setAddress("");
        grower.setPhone("");
        grower.setGrowerType("C");
        grower.setIsAssociation(true);
        growerRepository.saveAndFlush(grower);

        assertThat(jdbc.queryForMap(
                "SELECT primer_apellido, agencia_id, asociacion FROM terceros WHERE identificacion = 'V34-TEST-0001'"))
                .containsEntry("primer_apellido", null)
                .containsEntry("agencia_id", null)
                .containsEntry("asociacion", true);
    }

    @Test
    void futurePurchaseLastNameAcceptsNull() {
        assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns"
                + " WHERE table_name = 'compras_anunciadas' AND column_name = 'apellidos'", String.class))
                .isEqualTo("YES");
    }
}
