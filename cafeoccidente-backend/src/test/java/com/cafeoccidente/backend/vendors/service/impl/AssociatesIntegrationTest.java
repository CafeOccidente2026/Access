package com.cafeoccidente.backend.vendors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.vendors.dto.AssociatePage;
import com.cafeoccidente.backend.vendors.dto.AssociateResponse;
import com.cafeoccidente.backend.vendors.service.VendorService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** HU-53 / RF-28.3 contra Postgres: dos terceros de agencias distintas, insertados y revertidos al final. */
@SpringBootTest
@Transactional
class AssociatesIntegrationTest {

    private static final long AGENCY = 4L;
    private static final String FIRST = "77005301";
    private static final String SECOND = "77005302";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VendorService vendors;

    private String otherAgencyName;
    private long total;

    @BeforeEach
    void setUp() {
        long otherAgency = jdbc.queryForObject("SELECT min(id) FROM agencias WHERE id <> ?", Long.class, AGENCY);
        otherAgencyName = jdbc.queryForObject("SELECT nombre FROM agencias WHERE id = ?", String.class, otherAgency);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,"
                + " agencia_id, fecha_nacimiento, direccion, telefono, tipo, activo, fallecido, retirado, aceptado, habil,"
                + " asociacion, sexo) VALUES (?, 'AURA', 'MARIA', 'TUTISTAR', 'PRUEBA53', ?, ?, 'VDA LA PALMA',"
                + " '3001234567', 'C', true, false, true, true, false, false, 'F')",
                FIRST, AGENCY, LocalDate.of(1970, 3, 1));
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono,"
                + " tipo, activo, fallecido, retirado) VALUES (?, 'PEDRO', 'PRUEBA53', ?, '', '', 'C', true, true, false)",
                SECOND, otherAgency);
        total = jdbc.queryForObject("SELECT count(*) FROM terceros", Long.class);
    }

    @Test
    void ca1ArrowsWalkEveryThirdPartyOfEveryAgencyInIdOrder() {
        AssociatePage first = vendors.associateAt(0);
        AssociatePage beforeLast = vendors.associateAt(total - 2);
        AssociatePage last = vendors.associateAt(total - 1);

        assertThat(first.position()).isZero();
        assertThat(first.total()).isEqualTo(total);
        assertThat(first.associate().idNumber()).isEqualTo(
                jdbc.queryForObject("SELECT identificacion FROM terceros ORDER BY id LIMIT 1", String.class));
        // RN-28.3.3: el recorrido no se limita a una agencia.
        assertThat(beforeLast.associate().idNumber()).isEqualTo(FIRST);
        assertThat(last.associate().idNumber()).isEqualTo(SECOND);
        assertThat(last.associate().agencyName()).isEqualTo(otherAgencyName);
        assertThat(last.total()).isEqualTo(total);
    }

    @Test
    void ca1PositionsOutsideTheRangeStopAtTheEdges() {
        assertThat(vendors.associateAt(-5).position()).isZero();
        assertThatThrownBy(() -> vendors.associateAt(total)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ca2SearchShowsThatThirdPartyAtItsNavigationPosition() {
        AssociatePage found = vendors.findAssociate(" " + FIRST + " ");

        assertThat(found.position()).isEqualTo(total - 2);
        assertThat(found.total()).isEqualTo(total);
        assertThat(vendors.associateAt(found.position()).associate()).isEqualTo(found.associate());
        AssociateResponse a = found.associate();
        assertThat(a.firstName()).isEqualTo("AURA");
        assertThat(a.secondName()).isEqualTo("MARIA");
        assertThat(a.lastName()).isEqualTo("TUTISTAR");
        assertThat(a.secondLastName()).isEqualTo("PRUEBA53");
        assertThat(a.birthDate()).isEqualTo(LocalDate.of(1970, 3, 1));
        assertThat(a.address()).isEqualTo("VDA LA PALMA");
        assertThat(a.accepted()).isTrue();
        assertThat(a.eligible()).isFalse();
        assertThat(a.withdrawn()).isTrue();
        assertThat(a.deceased()).isFalse();
        assertThat(a.sex()).isEqualTo("F");
    }

    @Test
    void ca2SearchNeedsTheExactIdNumber() {
        assertThatThrownBy(() -> vendors.findAssociate("7700530")).isInstanceOf(ResourceNotFoundException.class);
    }
}
