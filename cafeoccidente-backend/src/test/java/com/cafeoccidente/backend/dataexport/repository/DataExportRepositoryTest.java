package com.cafeoccidente.backend.dataexport.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** markExported concatena el nombre de tabla al SQL: solo acepta las tablas de la lista fija. */
class DataExportRepositoryTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final DataExportRepository repository = new DataExportRepository(jdbc);

    @Test
    void marksAnAllowedTable() {
        repository.markExported("remisiones", List.of(1L));

        verify(jdbc).update(eq("UPDATE remisiones SET exportado = TRUE WHERE id IN (:ids)"), any(SqlParameterSource.class));
    }

    @Test
    void rejectsATableOutsideTheList() {
        assertThatThrownBy(() -> repository.markExported("usuarios; DROP TABLE usuarios", List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.markExported("usuarios", List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }
}
