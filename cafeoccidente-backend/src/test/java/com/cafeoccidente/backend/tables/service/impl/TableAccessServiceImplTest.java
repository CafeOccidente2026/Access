package com.cafeoccidente.backend.tables.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.ApiException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.tables.dto.TableInfo;
import com.cafeoccidente.backend.tables.repository.TableAccessRepository;
import com.cafeoccidente.backend.tables.repository.TableCatalog;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;

/** S3: solo el corte por el tope de 5 s (SQLSTATE 57014) se vuelve el aviso claro; los demas errores siguen igual. */
class TableAccessServiceImplTest {

    private final TableAccessRepository repository = mock(TableAccessRepository.class);
    private TableAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        TableCatalog catalog = mock(TableCatalog.class);
        SecurityUtils security = mock(SecurityUtils.class);
        when(security.isAdmin()).thenReturn(true);
        when(catalog.find("fondos")).thenReturn(Optional.of(new TableInfo("fondos", false, "id", List.of())));
        service = new TableAccessServiceImpl(repository, catalog, mock(CurrentAgencyProvider.class), security);
    }

    private void failsWith(DataAccessException error) {
        when(repository.rows(any(), any())).thenThrow(error);
    }

    @Test
    void aCanceledQueryBecomesTheClearMessage() {
        failsWith(new DataAccessResourceFailureException("x", new SQLException("cancelada", "57014")));
        assertThatThrownBy(() -> service.rows("fondos", Map.of()))
                .isInstanceOf(ApiException.class).hasMessage(TableAccessServiceImpl.TIMEOUT_MESSAGE);
    }

    @Test
    void anotherSqlErrorIsNotTheTimeout() {
        DataAccessException error = new DataAccessResourceFailureException("x", new SQLException("otra", "08006"));
        failsWith(error);
        assertThatThrownBy(() -> service.rows("fondos", Map.of())).isSameAs(error);
    }

    @Test
    void anErrorWithoutSqlStateIsNotTheTimeout() {
        DataAccessException error = new DataAccessResourceFailureException("sin causa SQL");
        failsWith(error);
        assertThatThrownBy(() -> service.rows("fondos", Map.of())).isSameAs(error);
    }
}
