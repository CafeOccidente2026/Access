package com.cafeoccidente.backend.supplies.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.supplies.dto.LedgerEntryRequest;
import com.cafeoccidente.backend.supplies.dto.SuppliesReportRow;
import com.cafeoccidente.backend.supplies.service.SuppliesEntryService;
import com.cafeoccidente.backend.supplies.service.SuppliesReportService;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5a (RN-27.1.4, RN-27.10.3): el cheque son los ultimos 4 digitos y se guarda como texto, con sus ceros a la izquierda
 * (V21). La base rechaza cualquier otra cosa; los informes lo devuelven tal cual. Transaccional: no deja movimientos.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class CheckNumberIntegrationTest {

    private static final long AGENCY = 1L;
    private static final String FIEL = "990005001";

    @Autowired
    private SuppliesEntryService entries;
    @Autowired
    private SuppliesReportService reports;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;

    @BeforeEach
    void signIn() {
        User user = new User();
        user.setUsername("prueba_5a");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static LedgerEntryRequest issuedCheck(Integer checkNumber) {
        return new LedgerEntryRequest("PRUEBA5A", "RP", null, FIEL, "CHEQUE PRUEBA", new BigDecimal("250000"), null,
                checkNumber);
    }

    /** Un caso por valor: en PostgreSQL, despues de un INSERT rechazado la transaccion queda abortada. */
    @ParameterizedTest
    @CsvSource({"movimientos_caja, 548", "movimientos_caja, 12345", "movimientos_caja, abcd", "suministros, 548",
        "suministros, 12345", "suministros, abcd", "movimientos_caja_menor, 548", "movimientos_caja_menor, 12345",
        "movimientos_caja_menor, abcd"})
    void theDatabaseOnlyAcceptsFourDigits(String table, String wrong) {
        String insert = "INSERT INTO " + table + " (id_transaccion, agencia_id, fondo_id, fecha, cedula, numero_cheque,"
                + " creado_en) VALUES ('PRUEBA5A', ?, (SELECT id FROM fondos WHERE codigo = 'RP'), CURRENT_DATE, ?, ?, now())";

        assertThat(jdbc.update(insert, AGENCY, FIEL, "0548")).isOne();
        assertThatThrownBy(() -> jdbc.update(insert, AGENCY, FIEL, wrong)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anIssuedCheckKeepsItsLeadingZeroUpToTheReport() {
        entries.createIssuedCheck(issuedCheck(548));

        assertThat(jdbc.queryForObject("SELECT numero_cheque FROM suministros WHERE id_transaccion = 'PRUEBA5A'",
                String.class)).isEqualTo("0548");
        assertThat(reports.supplies(AGENCY, "RP", LocalDate.now(clock)))
                .filteredOn(row -> "PRUEBA5A".equals(row.transactionId()))
                .extracting(SuppliesReportRow::checkNumber).containsExactly("0548");
    }

    @Test
    void aCheckWithMoreThanFourDigitsIsRejected() {
        assertThatThrownBy(() -> entries.createIssuedCheck(issuedCheck(12345)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("El cheque son los últimos 4 dígitos");
    }
}
