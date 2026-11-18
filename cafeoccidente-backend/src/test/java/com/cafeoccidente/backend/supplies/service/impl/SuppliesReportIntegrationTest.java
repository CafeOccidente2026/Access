package com.cafeoccidente.backend.supplies.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.config.ClockConfig;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.remittance.repository.RemittanceRepository;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.supplies.dto.LedgerEntryRequest;
import com.cafeoccidente.backend.supplies.dto.PackagingEntryRequest;
import com.cafeoccidente.backend.supplies.dto.SuppliesReportRow;
import com.cafeoccidente.backend.supplies.repository.PurchasePayment;
import com.cafeoccidente.backend.supplies.repository.SuppliesReadRepository;
import com.cafeoccidente.backend.supplies.service.SuppliesEntryService;
import com.cafeoccidente.backend.supplies.service.SuppliesReportService;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5d (RF-27.2 a RF-27.12.3, HU-37 a HU-49): los informes de Suministros con los ejemplos numericos del RF, sobre la
 * semilla (agencia 1, fondo RP). Los movimientos se dan de alta con el servicio real; la compra de 587.824 en efectivo
 * se inyecta en la lectura de compras (su copia a Caja ya la prueba {@code NewPurchasePaymentIntegrationTest}). El
 * reloj esta en 2031 para que ningun movimiento de otra prueba caiga en el corte por año. Transaccional: no deja
 * movimientos, consecutivos ni marcas de cheques.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class SuppliesReportIntegrationTest {

    private static final long AGENCY = 1L;
    private static final LocalDate TODAY = LocalDate.of(2031, 3, 10);
    private static final String GROWER = "990005201";
    private static final String DETAIL = "PRUEBA 5D";

    @MockitoBean
    private Clock clock;
    @MockitoSpyBean
    private SuppliesReadRepository reads;
    @Autowired
    private SuppliesEntryService entries;
    @Autowired
    private SuppliesReportService reports;
    @Autowired
    private RemittanceRepository remittances;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;

    private String agencyName;

    @BeforeEach
    void signIn() {
        when(clock.instant()).thenReturn(Instant.parse("2031-03-10T15:00:00Z"));
        when(clock.getZone()).thenReturn(ClockConfig.COLOMBIA);

        User user = new User();
        user.setUsername("prueba_5d");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        agencyName = user.getAgency().getName();
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono,"
                + " tipo, asociacion) VALUES (?, 'ANA', 'PRUEBA', ?, 'VEREDA', '3001112233', 'C', false)", GROWER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static LedgerEntryRequest ledger(String fund, String amount, String method, String check) {
        return new LedgerEntryRequest(fund, null, GROWER, DETAIL, new BigDecimal(amount), method, check);
    }

    /** HU-44 y HU-36: Tesoreria consigna 100.000.000 y el fiel saca 97.000.000 en cheque para la caja. */
    private void treasuryAndCashSupply() {
        entries.createSupply(ledger("RP", "100000000", null, null));
        entries.createCashSupply(ledger("RP", "97000000", "CHEQUE", "0548"));
    }

    /** Compra de cafe pagada en efectivo, como la deja ActualizaCaja. */
    private void cashPurchaseOf(String net) {
        PurchasePayment purchase = new PurchasePayment("DRY", 900001L, 990001, AGENCY, agencyName, "RP", TODAY, GROWER,
                new BigDecimal(net), "EFECTIVO", null, Instant.parse("2031-03-10T16:00:00Z"));
        doReturn(List.of(purchase)).when(reads).purchasePayments(any(), any(), any());
    }

    private static BigDecimal lastBalance(List<SuppliesReportRow> rows) {
        return rows.getLast().balance();
    }

    @Test
    void suppliesRpEndsInThreeMillionAndLfShowsNothingFromRp() {
        // HU-46 (RF-27.11): 100.000.000 − 97.000.000 = 3.000.000.
        treasuryAndCashSupply();

        List<SuppliesReportRow> rp = reports.supplies(AGENCY, "RP", TODAY);
        // Vr Suministro de Tesoreria y, como Gastos o Comp, el suministro de efectivo con cheque (D3).
        assertThat(rp).extracting(row -> row.inflow().subtract(row.outflow()).toBigInteger())
                .containsExactlyInAnyOrder(new BigInteger("100000000"), new BigInteger("-97000000"));
        assertThat(lastBalance(rp)).isEqualByComparingTo("3000000");
        assertThat(reports.supplies(AGENCY, "LF", TODAY)).isEmpty();
    }

    @Test
    void cashRpEndsInTheRfExampleAndAnIssuedCheckIsNotInCaja() {
        // HU-37 (RF-27.2): 97.000.000 − 587.824 = 96.412.176.
        treasuryAndCashSupply();
        cashPurchaseOf("587824");
        entries.createIssuedCheck(ledger("RP", "1000000", null, "0549"));

        List<SuppliesReportRow> cash = reports.cashMovement(AGENCY, "RP", TODAY);

        assertThat(cash).extracting(SuppliesReportRow::transactionId).containsExactlyInAnyOrder("SDBU-2-001", "990001");
        assertThat(lastBalance(cash)).isEqualByComparingTo("96412176");
    }

    @Test
    void packagingUsedEndsIn519AndNewIsKeptApart() {
        // HU-43 (RF-27.8): entra el suministro de empaques (HU-41) y sale la remesa con detalle "Remision".
        entries.createPackagingEntry(new PackagingEntryRequest("USADO", GROWER, DETAIL, 555));
        entries.createPackagingEntry(new PackagingEntryRequest("NUEVO", GROWER, DETAIL, 40));
        remittances.addPackagingExit(AGENCY, "SDBU-RP-9001", "USADO", GROWER, 36);

        List<SuppliesReportRow> used = reports.packaging(AGENCY, "USADO", TODAY);

        assertThat(used).extracting(SuppliesReportRow::detail).containsExactlyInAnyOrder(DETAIL, "Remision");
        assertThat(lastBalance(used)).isEqualByComparingTo("519");
        assertThat(lastBalance(reports.packaging(AGENCY, "NUEVO", TODAY))).isEqualByComparingTo("40");
    }

    @Test
    void theCheckRelationShowsNewChecksOnceAndTheSpecialOneShowsThemAgain() {
        // HU-38 (RF-27.3) y HU-39 (RF-27.4).
        entries.createCashSupply(ledger("RP", "97000000", "CHEQUE", "0548"));
        entries.createIssuedCheck(ledger("RP", "1000000", null, "0549"));

        assertThat(reports.checkRelation(AGENCY, null, null, TODAY)).extracting(SuppliesReportRow::checkNumber)
                .containsExactlyInAnyOrder("0548", "0549");
        assertThat(reports.checkRelation(AGENCY, null, null, TODAY)).isEmpty();
        assertThat(reports.checkRelation(AGENCY, TODAY, TODAY, TODAY)).extracting(SuppliesReportRow::checkNumber)
                .containsExactlyInAnyOrder("0548", "0549");
    }

    @Test
    void paymentMethodsShowsEverythingWithAStarAndOnlyChecksWithCheque() {
        // HU-40 (RF-27.5): la entrada a caja queda EFECTIVO (CAMBIO A EFECTIVO); el cheque girado es CHEQUE.
        entries.createCashSupply(ledger("RP", "97000000", "CHEQUE", "0548"));
        entries.createIssuedCheck(ledger("RP", "1000000", null, "0549"));
        cashPurchaseOf("587824");

        assertThat(reports.paymentMethods(AGENCY, TODAY, TODAY, "*")).extracting(SuppliesReportRow::paymentMethod)
                .containsExactlyInAnyOrder("EFECTIVO", "CHEQUE", "EFECTIVO");
        assertThat(reports.paymentMethods(AGENCY, TODAY, TODAY, "CHEQUE")).singleElement()
                .satisfies(row -> {
                    assertThat(row.checkNumber()).isEqualTo("0549");
                    assertThat(row.detail()).isEqualTo("ANA PRUEBA");
                });
    }

    @Test
    void pettyCashCanEndNegative() {
        // HU-49 (RF-27.12.3) y HU-48 CA-3: 500.000 − 650.000 = −150.000.
        entries.createPettyCashSupply(ledger(null, "500000", null, null));
        entries.createPettyCashExpense(ledger(null, "650000", null, null));

        assertThat(lastBalance(reports.pettyCashMovement(AGENCY, TODAY))).isEqualByComparingTo("-150000");
    }
}
