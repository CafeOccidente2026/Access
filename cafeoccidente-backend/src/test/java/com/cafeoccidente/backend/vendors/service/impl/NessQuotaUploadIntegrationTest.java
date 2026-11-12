package com.cafeoccidente.backend.vendors.service.impl;

import static com.cafeoccidente.backend.vendors.service.NessXlsx.HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import com.cafeoccidente.backend.vendors.dto.NessQuotaPage;
import com.cafeoccidente.backend.vendors.dto.NessQuotaRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaUploadRecord;
import com.cafeoccidente.backend.vendors.dto.NessQuotaUploadResult;
import com.cafeoccidente.backend.vendors.service.NessQuotaUploadService;
import com.cafeoccidente.backend.vendors.service.NessXlsx;
import com.cafeoccidente.backend.vendors.service.VendorReportService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-54 (RF-28.4): Actualiza Cupos NESS sobre una tabla NESS de prueba (A, B y C, guardadas con el ".0" del
 * CSV de Access). La vista previa no toca la tabla; aplicar la reemplaza completa (RN-28.4.5) y deja el
 * historial; con errores no cambia nada (RN-28.4.6). Transaccional: la tabla real queda como estaba.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class NessQuotaUploadIntegrationTest {

    private static final String FILE = "NESS 2026.xlsx";

    @Autowired
    private NessQuotaUploadService uploads;
    @Autowired
    private GrowerService growers;
    @Autowired
    private VendorReportService vendorReports;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;

    private Long adminId;

    @BeforeEach
    void nessWithThreeQuotas() {
        User user = new User();
        user.setUsername("prueba_hu54_admin");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        adminId = principal.getUserId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        jdbc.update("DELETE FROM cupos_ness");
        jdbc.update("INSERT INTO cupos_ness (cedula, nombres, programa, cupo) VALUES"
                + " ('990054001.0', 'ANA', 'NESPRESSO', '1000.0'),"
                + " ('990054002.0', 'BETO', 'NESPRESSO', '500.0'),"
                + " ('990054003.0', 'CARO', 'NESPRESSO', '300.0')");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static byte[] newList() {
        return NessXlsx.of(HEADER,
                new Object[] {990054001, "ANA", "NESPRESSO", 1000},
                new Object[] {990054002, "BETO", "NESPRESSO", 800},
                new Object[] {990054004, "DORA", "NESPRESSO", 200});
    }

    private List<Map<String, Object>> ness() {
        return jdbc.queryForList("SELECT cedula, nombres, programa, cupo FROM cupos_ness ORDER BY id");
    }

    private long loads() {
        return jdbc.queryForObject("SELECT count(*) FROM cargas_cupos_ness", Long.class);
    }

    @Test
    void previewCountsAddedChangedUnchangedAndRemovedWithoutTouchingNess() {
        List<Map<String, Object>> before = ness();

        NessQuotaUploadResult result = uploads.preview(FILE, newList());

        assertThat(result.errors()).isEmpty();
        assertThat(result).extracting(NessQuotaUploadResult::rows, NessQuotaUploadResult::added,
                        NessQuotaUploadResult::changed, NessQuotaUploadResult::unchanged, NessQuotaUploadResult::removed)
                .containsExactly(3, 1, 1, 1, 1);
        assertThat(ness()).isEqualTo(before);
        assertThat(loads()).isZero();
    }

    @Test
    void aDifferentNameCountsAsChanged() {
        NessQuotaUploadResult result = uploads.preview(FILE, NessXlsx.of(HEADER,
                new Object[] {990054001, "ANA MARIA", "NESPRESSO", 1000},
                new Object[] {990054002, "BETO", "NESPRESSO", 500},
                new Object[] {990054003, "CARO", "NESPRESSO", 300}));

        assertThat(result).extracting(NessQuotaUploadResult::added, NessQuotaUploadResult::changed,
                NessQuotaUploadResult::unchanged, NessQuotaUploadResult::removed).containsExactly(0, 1, 2, 0);
    }

    @Test
    void applyReplacesTheWholeTableAndRecordsWhoLoadedIt() {
        NessQuotaUploadResult result = uploads.apply(FILE, newList());

        assertThat(result.errors()).isEmpty();
        assertThat(result).extracting(NessQuotaUploadResult::added, NessQuotaUploadResult::changed,
                NessQuotaUploadResult::unchanged, NessQuotaUploadResult::removed).containsExactly(1, 1, 1, 1);
        assertThat(ness()).containsExactly(
                Map.of("cedula", "990054001", "nombres", "ANA", "programa", "NESPRESSO", "cupo", "1000"),
                Map.of("cedula", "990054002", "nombres", "BETO", "programa", "NESPRESSO", "cupo", "800"),
                Map.of("cedula", "990054004", "nombres", "DORA", "programa", "NESPRESSO", "cupo", "200"));
        assertThat(jdbc.queryForMap("SELECT usuario_id, archivo, filas, agregados, cambiados, iguales, retirados"
                + " FROM cargas_cupos_ness")).containsExactlyInAnyOrderEntriesOf(Map.of("usuario_id", adminId,
                "archivo", FILE, "filas", 3, "agregados", 1, "cambiados", 1, "iguales", 1, "retirados", 1));

        // Compras lee la lista nueva: D ya puede vender NESPRESSO y C ya no esta en el programa.
        // C3 (prueba del 11-nov, punto 3): el bloque Informacion Federacion muestra la cedula y el nombre de NESS.
        assertThat(growers.findProgram("990054004", "NESPRESSO - FTUSA")).get()
                .extracting("programa", "cupo", "cedula", "nombres").containsExactly("NESPRESSO", "200", "990054004", "DORA");
        assertThat(growers.findProgram("990054003", "NESPRESSO - FTUSA")).isEmpty();
    }

    @Test
    void theSameCedulaInTwoProgramsLoadsBothRows() {
        uploads.apply(FILE, NessXlsx.of(HEADER,
                new Object[] {99376216, "Caficultor Ness Dos", "NESPRESSO", 30000},
                new Object[] {99376216, "Caficultor Ness Dos", "NESPRESSO LATE HARVEST", 30000}));

        assertThat(jdbc.queryForList("SELECT programa FROM cupos_ness WHERE cedula = '99376216' ORDER BY id",
                String.class)).containsExactly("NESPRESSO", "NESPRESSO LATE HARVEST");
    }

    @Test
    void aCedulaRepeatedInTheSameProgramWithTheSameQuotaLoadsOnceAndWarns() {
        NessQuotaUploadResult result = uploads.apply(FILE, NessXlsx.of(HEADER,
                new Object[] {99376216, "Caficultor Ness Dos", "NESPRESSO", 30000},
                new Object[] {99376216, "Caficultor Ness Tres", "NESPRESSO", 30000}));

        assertThat(result.warnings()).hasSize(1);
        assertThat(result.rows()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT nombres FROM cupos_ness WHERE cedula = '99376216'", String.class))
                .containsExactly("Caficultor Ness Dos");
    }

    @Test
    void aFileWithErrorsLeavesNessUnchanged() {
        List<Map<String, Object>> before = ness();

        NessQuotaUploadResult missingColumn = uploads.apply(FILE,
                NessXlsx.of(new Object[] {"CEDULA", "NOMBRES", "CUPO"}, new Object[] {990054009, "EVA", 10}));
        NessQuotaUploadResult notExcel = uploads.apply("NESS.csv", "CEDULA;NOMBRES;PROGRAMA;CUPO".getBytes());

        assertThat(missingColumn.errors()).containsExactly("Falta la columna PROGRAMA");
        assertThat(notExcel.errors()).containsExactly("Solo se acepta archivo Excel (.xlsx)");
        assertThat(ness()).isEqualTo(before);
        assertThat(loads()).isZero();
    }

    @Test
    void consultaCuposNessFiltersTheNewListByCedula() {
        uploads.apply(FILE, newList());

        // HU-55 CA-1 y CA-2: la consulta muestra la lista cargada y filtra por la cedula digitada.
        NessQuotaPage page = vendorReports.nessQuotas("990054004", 0);

        assertThat(page.total()).isOne();
        assertThat(page.rows()).containsExactly(
                new NessQuotaRow("990054004", "DORA", "NESPRESSO", new BigDecimal("200")));
    }

    @Test
    void listsThePreviousLoadsNewestFirst() {
        uploads.apply("primera.xlsx", newList());
        uploads.apply("segunda.xlsx", newList());

        List<NessQuotaUploadRecord> history = uploads.history();

        assertThat(history).extracting(NessQuotaUploadRecord::fileName).startsWith("segunda.xlsx", "primera.xlsx");
        assertThat(history.get(0).username()).isEqualTo("prueba_hu54_admin");
        assertThat(history.get(0).unchanged()).isEqualTo(3);
    }
}
