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
        // D9: la cedula del cheque girado tiene que estar en TERCEROS.
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono,"
                + " tipo, asociacion) VALUES (?, 'FIEL', 'PRUEBA', ?, 'VEREDA', '3001112233', 'C', false)", FIEL, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static LedgerEntryRequest issuedCheck(String checkNumber) {
        return new LedgerEntryRequest("RP", null, FIEL, "CHEQUE PRUEBA", new BigDecimal("250000"), null, checkNumber);
    }

    /** Un caso por valor: en PostgreSQL, despues de un INSERT rechazado la transaccion queda abortada. */
    @ParameterizedTest
    @CsvSource({"movimientos_caja, 5a", "movimientos_caja, 1234567890123456", "movimientos_caja, abcd",
        "suministros, 5a", "suministros, 1234567890123456", "suministros, abcd", "movimientos_caja_menor, 5a",
        "movimientos_caja_menor, 1234567890123456", "movimientos_caja_menor, abcd"})
    void theDatabaseAcceptsOneToFifteenDigits(String table, String wrong) {
        String insert = "INSERT INTO " + table + " (id_transaccion, agencia_id, fondo_id, fecha, cedula, numero_cheque,"
                + " creado_en) VALUES ('PRUEBA5A', ?, (SELECT id FROM fondos WHERE codigo = 'RP'), CURRENT_DATE, ?, ?, now())";

        assertThat(jdbc.update(insert, AGENCY, FIEL, "0548")).isOne();
        // 25-nov (V34): la base acepta los cheques cortos de Access tal cual (52 en Samaniego); la pantalla no.
        assertThat(jdbc.update(insert, AGENCY, FIEL, "52")).isOne();
        assertThat(jdbc.update(insert, AGENCY, FIEL, "7")).isOne();
        assertThat(jdbc.update(insert, AGENCY, FIEL, "13324")).isOne();
        assertThat(jdbc.update(insert, AGENCY, FIEL, "012345678901234")).isOne();
        assertThatThrownBy(() -> jdbc.update(insert, AGENCY, FIEL, wrong)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anIssuedCheckKeepsItsLeadingZeroUpToTheReport() {
        String id = entries.createIssuedCheck(issuedCheck("0548")).transactionId();

        assertThat(jdbc.queryForObject("SELECT numero_cheque FROM suministros WHERE id_transaccion = ?",
                String.class, id)).isEqualTo("0548");
        assertThat(reports.supplies(AGENCY, "RP", LocalDate.now(clock)))
                .filteredOn(row -> id.equals(row.transactionId()))
                .extracting(SuppliesReportRow::checkNumber).containsExactly("0548");
    }

    @Test
    void aLongCheckGoesUpToTheReportAndTheCheckRelation() {
        // 24-nov (V32): Consaca usa cheques de 5 digitos; se aceptan hasta 15, con sus ceros.
        String id = entries.createIssuedCheck(issuedCheck("012345678901234")).transactionId();

        assertThat(reports.supplies(AGENCY, "RP", LocalDate.now(clock)))
                .filteredOn(row -> id.equals(row.transactionId()))
                .extracting(SuppliesReportRow::checkNumber).containsExactly("012345678901234");
        assertThat(reports.checkRelation(AGENCY, null, null, LocalDate.now(clock)))
                .extracting(SuppliesReportRow::checkNumber).contains("012345678901234");
    }

    @Test
    void aShortCheckFromAccessGoesUpToTheReportAndTheCheckRelationAsItIs() {
        // 25-nov (V34): un cheque de Access de 2 digitos se carga tal cual y sale igual, sin ceros de relleno.
        jdbc.update("INSERT INTO suministros (id_transaccion, agencia_id, fondo_id, fecha, cedula, detalle, entradas,"
                + " salidas, forma_pago, numero_cheque, creado_en) VALUES ('PRUEBA52', ?, (SELECT id FROM fondos"
                + " WHERE codigo = 'RP'), CURRENT_DATE, ?, 'CHEQUE CORTO', 0, 250000, 'CHEQUE', '52', now())", AGENCY, FIEL);

        assertThat(reports.supplies(AGENCY, "RP", LocalDate.now(clock)))
                .filteredOn(row -> "PRUEBA52".equals(row.transactionId()))
                .extracting(SuppliesReportRow::checkNumber).containsExactly("52");
        assertThat(reports.checkRelation(AGENCY, null, null, LocalDate.now(clock)))
                .extracting(SuppliesReportRow::checkNumber).contains("52");
    }

    @Test
    void aCheckOutsideFourToFifteenDigitsIsRejected() {
        for (String wrong : new String[] {"548", "1234567890123456", "12A45", "00000"}) {
            assertThatThrownBy(() -> entries.createIssuedCheck(issuedCheck(wrong)))
                    .as(wrong).isInstanceOf(BusinessRuleException.class)
                    .hasMessage("El número de cheque debe tener entre 4 y 15 dígitos");
        }
    }

    /** V25 y V32: el cheque de las compras tambien es texto (0548 sigue 0548), de 4 a 15 digitos. */
    @ParameterizedTest
    @CsvSource({"5a", "1234567890123456", "abcd"})
    void purchasePaymentsKeepTheirDigitsToo(String wrong) {
        String insert = "INSERT INTO formas_pago_compras (origen, origen_id, efectivo, cheque, transferencia, datafono,"
                + " numero_cheque) VALUES ('DRY', ?, 0, 0, 0, 0, ?)";
        assertThat(jdbc.update(insert, -1L, "0548")).isOne();
        assertThat(jdbc.update(insert, -3L, "52")).isOne();
        assertThat(jdbc.queryForObject("SELECT numero_cheque FROM formas_pago_compras WHERE origen = 'DRY' AND origen_id = -1",
                String.class)).isEqualTo("0548");
        assertThatThrownBy(() -> jdbc.update(insert, -2L, wrong)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
