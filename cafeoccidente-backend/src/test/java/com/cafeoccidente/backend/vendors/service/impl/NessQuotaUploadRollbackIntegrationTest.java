package com.cafeoccidente.backend.vendors.service.impl;

import static com.cafeoccidente.backend.vendors.service.NessXlsx.HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.vendors.repository.NessQuotaUploadRepository;
import com.cafeoccidente.backend.vendors.service.NessQuotaUploadService;
import com.cafeoccidente.backend.vendors.service.NessXlsx;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * RN-28.4.6: si algo falla despues de borrar y llenar la tabla NESS (aqui, al guardar el historial), no queda
 * nada a medias. Sin @Transactional para ver el rollback real; la tabla queda como estaba.
 */
@SpringBootTest
@SuppressWarnings("null")
class NessQuotaUploadRollbackIntegrationTest {

    private static final String SNAPSHOT = "SELECT count(*) || ':' || coalesce(md5(string_agg(concat_ws('|', id, cedula,"
            + " nombres, programa, cupo), ',' ORDER BY id)), '') FROM cupos_ness";

    @Autowired
    private NessQuotaUploadService uploads;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private NessQuotaUploadRepository repository;
    @MockitoBean
    private SecurityUtils securityUtils;

    @Test
    void aFailureAfterReplacingTheTableLeavesNessAsItWas() {
        when(securityUtils.getCurrentUserId()).thenReturn(jdbc.queryForObject("SELECT min(id) FROM usuarios", Long.class));
        doThrow(new IllegalStateException("falla de prueba")).when(repository)
                .recordUpload(anyLong(), anyString(), any());
        String before = jdbc.queryForObject(SNAPSHOT, String.class);
        long loadsBefore = jdbc.queryForObject("SELECT count(*) FROM cargas_cupos_ness", Long.class);

        assertThatThrownBy(() -> uploads.apply("NESS.xlsx",
                NessXlsx.of(HEADER, new Object[] {990054099, "PRUEBA ROLLBACK", "NESPRESSO", 10})))
                .hasMessageContaining("falla de prueba");

        assertThat(jdbc.queryForObject(SNAPSHOT, String.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cargas_cupos_ness", Long.class)).isEqualTo(loadsBefore);
    }
}
