package com.cafeoccidente.backend.purchases.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo del 23-nov (V30): las compras de Seco, Verde y Pasilla tienen la columna anio, el año de su fecha de compra.
 * Es una columna generada por Postgres: se llena sola, sigue a la fecha y no se puede escribir. Transaccional.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class PurchaseYearColumnIntegrationTest {

    private static final long AGENCY = 1L;
    private static final String SELLER = "990009393";

    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theThreePurchaseTablesHaveTheYearGeneratedFromThePurchaseDate() {
        List<Map<String, Object>> columns = jdbc.queryForList("SELECT table_name, is_generated, generation_expression"
                + " FROM information_schema.columns WHERE column_name = 'anio' ORDER BY table_name");
        assertThat(columns).extracting(c -> c.get("table_name"))
                .containsExactly("compras_cafe_seco", "compras_cafe_verde", "compras_pasilla");
        assertThat(columns).allSatisfy(c -> {
            assertThat(c.get("is_generated")).isEqualTo("ALWAYS");
            assertThat((String) c.get("generation_expression")).contains("fecha_compra");
        });
    }

    @Test
    void aPurchaseGetsItsYearAndItFollowsTheDateButCannotBeWritten() {
        Long id = buyDryCoffee();
        assertThat(year(id)).isEqualTo(LocalDate.now().getYear());

        jdbc.update("UPDATE compras_cafe_seco SET fecha_compra = DATE '2025-12-31' WHERE id = ?", id);
        assertThat(year(id)).isEqualTo(2025);

        assertThatThrownBy(() -> jdbc.update("UPDATE compras_cafe_seco SET anio = 2030 WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    private int year(Long id) {
        return jdbc.queryForObject("SELECT anio FROM compras_cafe_seco WHERE id = ?", Integer.class, id);
    }

    private Long buyDryCoffee() {
        User user = new User();
        user.setUsername("prueba_anio");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        Long rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        Long announcement = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec,"
                + " sobrepr_carga_esp, pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000,"
                + " 50, ?, 'RN', true) RETURNING id", Long.class, rp);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias WHERE agencia_id = ?",
                AGENCY, announcement, AGENCY);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'ANIO', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
        DryCoffeePurchaseRequest request = new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"),
                BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        BigDecimal net = dry.preview(request).netToPay();
        return dry.create(new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null))).id();
    }
}
