package com.cafeoccidente.backend.supplies.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.config.ClockConfig;
import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.supplies.dto.EntryResponse;
import com.cafeoccidente.backend.supplies.dto.LedgerEntryRequest;
import com.cafeoccidente.backend.supplies.dto.PackagingEntryRequest;
import com.cafeoccidente.backend.supplies.service.SuppliesEntryService;
import com.cafeoccidente.backend.supplies.service.SuppliesReportService;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5b (RF-27, §2.1, HU-36 a HU-48): altas de Suministros y Caja Menor sobre la semilla (agencia 1, prefijo SDBU).
 * El Id lo genera el servidor (D1); la fecha es la de Colombia (D8), salvo Ingresar Suministros (D2); la cedula se
 * valida en TERCEROS donde el RF lo pide y donde el movimiento pasa por la Relacion Cheques (D9). El reloj esta fijo a
 * las 20:00 de Colombia del 2026-01-14 (en UTC ya es el 15). Transaccional: no deja movimientos ni consecutivos.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class SuppliesEntryIntegrationTest {

    private static final long AGENCY = 1L;
    private static final LocalDate COLOMBIAN_DAY = LocalDate.of(2026, 1, 14);
    private static final String REGISTERED = "990005101";
    private static final String UNKNOWN = "990005999";
    private static final String NOT_REGISTERED = "La cédula 990005999 no está registrada en TERCEROS";
    private static final BigDecimal AMOUNT = new BigDecimal("250000");

    @MockitoBean
    private Clock clock;
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

    @BeforeEach
    void eightPmInColombia() {
        when(clock.instant()).thenReturn(Instant.parse("2026-01-15T01:00:00Z"));
        when(clock.getZone()).thenReturn(ClockConfig.COLOMBIA);

        User user = new User();
        user.setUsername("prueba_5b");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        jdbc.update("DELETE FROM consecutivos_transaccion WHERE agencia_id = ?", AGENCY);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono,"
                + " tipo, asociacion) VALUES (?, 'ANA', 'PRUEBA', ?, 'VEREDA', '3001112233', 'C', false)", REGISTERED, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static LedgerEntryRequest ledger(String fund, String idNumber, String method, String check) {
        return new LedgerEntryRequest(fund, null, idNumber, "PRUEBA 5B", AMOUNT, method, check);
    }

    private static PackagingEntryRequest packaging(String type, String idNumber) {
        return new PackagingEntryRequest(type, idNumber, "PRUEBA 5B", 10);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE agencia_id = ? AND detalle = 'PRUEBA 5B'",
                Long.class, AGENCY);
    }

    private long consecutives() {
        return jdbc.queryForObject("SELECT count(*) FROM consecutivos_transaccion WHERE agencia_id = ?", Long.class, AGENCY);
    }

    @Test
    void eachScreenGeneratesItsIdWithTheAgencyPrefix() {
        assertThat(entries.createCashSupply(ledger("LF", REGISTERED, "EFECTIVO", null)).transactionId()).isEqualTo("SDBU-1-001");
        assertThat(entries.createCashSupply(ledger("RP", REGISTERED, "EFECTIVO", null)).transactionId()).isEqualTo("SDBU-2-001");
        assertThat(entries.createCashAdjustment(ledger("RP", REGISTERED, null, null)).transactionId()).isEqualTo("SDBU-2AJU-001");
        assertThat(entries.createSupply(ledger("RP", REGISTERED, null, null)).transactionId()).isEqualTo("SDBU-3SUM-001");
        assertThat(entries.createSupply(ledger("LF", REGISTERED, null, null)).transactionId()).isEqualTo("SDBU-4SUM-001");
        assertThat(entries.createIssuedCheck(ledger("RP", REGISTERED, null, "0548")).transactionId()).isEqualTo("SDBU-3CHQ-001");
        assertThat(entries.createIssuedCheck(ledger("LF", REGISTERED, null, "0549")).transactionId()).isEqualTo("SDBU-4CHQ-001");
        assertThat(entries.createPackagingEntry(packaging("USADO", REGISTERED)).transactionId()).isEqualTo("SDBU-5SUM-001");
        assertThat(entries.createPackagingEntry(packaging("NUEVO", REGISTERED)).transactionId()).isEqualTo("SDBU-6SUM-001");
        assertThat(entries.createPackagingLoan(packaging("USADO", REGISTERED)).transactionId()).isEqualTo("SDBU-5PRE-001");
        assertThat(entries.createPackagingLoan(packaging("NUEVO", REGISTERED)).transactionId()).isEqualTo("SDBU-6PRE-001");
        assertThat(entries.createPettyCashSupply(ledger(null, null, null, null)).transactionId()).isEqualTo("SDBU-7ING-001");
        assertThat(entries.createPettyCashExpense(ledger(null, REGISTERED, null, null)).transactionId())
                .isEqualTo("SDBU-7GAS-001");
    }

    @Test
    void theSecondOfTheSameScreenTakesTheNextNumberAndThePeekShowsIt() {
        assertThat(entries.nextTransactionId("packaging", "USADO")).isEqualTo("SDBU-5SUM-001");
        entries.createPackagingEntry(packaging("USADO", REGISTERED));

        assertThat(entries.nextTransactionId("packaging", "USADO")).isEqualTo("SDBU-5SUM-002");
        assertThat(entries.createPackagingEntry(packaging("USADO", REGISTERED)).transactionId()).isEqualTo("SDBU-5SUM-002");
        // RN-27.6.2: el prestamo del mismo tipo lleva su propio consecutivo.
        assertThat(entries.nextTransactionId("packagingLoan", "USADO")).isEqualTo("SDBU-5PRE-001");
        assertThat(entries.nextTransactionId("cash", "RP")).isEqualTo("SDBU-2-001");
    }

    @Test
    void theDateIsTheColombianDayEvenAtEightPm() {
        EntryResponse cash = entries.createCashSupply(ledger("RP", REGISTERED, "EFECTIVO", null));
        EntryResponse loan = entries.createPackagingLoan(packaging("USADO", REGISTERED));

        assertThat(cash.entryDate()).isEqualTo(COLOMBIAN_DAY);
        assertThat(loan.entryDate()).isEqualTo(COLOMBIAN_DAY);
    }

    @Test
    void ingresarSuministrosKeepsTheChosenDateOrTakesToday() {
        // D2 (Q16): Ingresar Suministros es la unica alta con fecha editable; vacia es la de hoy.
        LedgerEntryRequest chosen = new LedgerEntryRequest("RP", LocalDate.of(2026, 1, 10), REGISTERED, "PRUEBA 5B",
                AMOUNT, null, null);

        assertThat(entries.createSupply(chosen).entryDate()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(entries.createSupply(ledger("RP", REGISTERED, null, null)).entryDate()).isEqualTo(COLOMBIAN_DAY);
    }

    @Test
    void anUnknownCedulaIsRejectedWithoutSavingOrConsumingTheId() {
        // HU-41, 42, 44 y 48 (CA-2); Cheques Girados y Suministro de Efectivo con cheque (D9).
        assertRejected(() -> entries.createPackagingEntry(packaging("USADO", UNKNOWN)));
        assertRejected(() -> entries.createPackagingLoan(packaging("USADO", UNKNOWN)));
        assertRejected(() -> entries.createSupply(ledger("RP", UNKNOWN, null, null)));
        assertRejected(() -> entries.createPettyCashExpense(ledger(null, UNKNOWN, null, null)));
        assertRejected(() -> entries.createIssuedCheck(ledger("RP", UNKNOWN, null, "0548")));
        assertRejected(() -> entries.createCashSupply(ledger("RP", UNKNOWN, "CHEQUE", "0548")));

        assertThat(count("empaques") + count("suministros") + count("movimientos_caja") + count("movimientos_caja_menor"))
                .isZero();
        assertThat(consecutives()).isZero();
    }

    @Test
    void aCashSupplyInCashDoesNotCheckTheCedula() {
        // D9: en efectivo no pasa por la Relacion Cheques; el RF solo pide numeros.
        assertThat(entries.createCashSupply(ledger("RP", UNKNOWN, "EFECTIVO", null)).idNumber()).isEqualTo(UNKNOWN);
    }

    @Test
    void pettyCashTakesTheFielFromTheControlRecord() {
        String fiel = jdbc.queryForObject("SELECT cedula_fiel FROM registros_control WHERE agencia_id = ? AND activo",
                String.class, AGENCY);

        // RN-27.12.1.2: lo que mande el cliente se ignora.
        assertThat(entries.createPettyCashSupply(ledger(null, REGISTERED, null, null)).idNumber()).isEqualTo(fiel);
    }

    @Test
    void theCheckIsMandatoryWithChequeAndKeepsItsLeadingZero() {
        assertThatThrownBy(() -> entries.createCashSupply(ledger("RP", REGISTERED, "CHEQUE", null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> entries.createIssuedCheck(ledger("RP", REGISTERED, null, null)))
                .isInstanceOf(BusinessRuleException.class);

        entries.createIssuedCheck(ledger("RP", REGISTERED, null, "0548"));
        assertThat(jdbc.queryForObject("SELECT numero_cheque FROM suministros WHERE detalle = 'PRUEBA 5B'", String.class))
                .isEqualTo("0548");
    }

    @Test
    void negativeBalancesAreAllowed() {
        // HU-36 CA-2 y HU-48 CA-3: el saldo puede quedar negativo.
        entries.createPettyCashExpense(ledger(null, REGISTERED, null, null));

        assertThat(reports.pettyCashMovement(AGENCY, COLOMBIAN_DAY))
                .filteredOn(row -> "SDBU-7GAS-001".equals(row.transactionId()))
                .singleElement().satisfies(row -> assertThat(row.outflow()).isEqualByComparingTo(AMOUNT));
    }

    @Test
    void aCashSupplyLeavesSuministrosOnlyWhenPaidByCheck() {
        // D3 (como Access, "Efectivo con cheques"): con EFECTIVO queda solo en Caja.
        entries.createCashSupply(ledger("RP", REGISTERED, "CHEQUE", "0550"));
        entries.createCashSupply(ledger("RP", REGISTERED, "EFECTIVO", null));

        assertThat(reports.supplies(AGENCY, "RP", COLOMBIAN_DAY))
                .filteredOn(row -> row.transactionId().startsWith("SDBU-2-"))
                .extracting(row -> row.transactionId()).containsExactly("SDBU-2-001");
    }

    private void assertRejected(Supplier<EntryResponse> save) {
        assertThatThrownBy(save::get).isInstanceOf(BusinessRuleException.class).hasMessage(NOT_REGISTERED);
    }
}
