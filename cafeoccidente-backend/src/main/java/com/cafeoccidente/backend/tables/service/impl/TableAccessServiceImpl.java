package com.cafeoccidente.backend.tables.service.impl;

import com.cafeoccidente.backend.common.exception.ApiException;
import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.tables.dto.TableInfo;
import com.cafeoccidente.backend.tables.dto.TableInfo.ColumnInfo;
import com.cafeoccidente.backend.tables.dto.TablePage;
import com.cafeoccidente.backend.tables.dto.TableQuery;
import com.cafeoccidente.backend.tables.dto.TableQuery.Filter;
import com.cafeoccidente.backend.tables.dto.TableQuery.Operator;
import com.cafeoccidente.backend.tables.repository.TableAccessRepository;
import com.cafeoccidente.backend.tables.repository.TableCatalog;
import com.cafeoccidente.backend.tables.service.TableAccessService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TableAccessServiceImpl implements TableAccessService {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 200;
    /** Valor de "agency" para ver todas las agencias juntas (solo el Administrador, respuesta del 12-nov). */
    static final String ALL_AGENCIES = "ALL";
    /** El Usuario con el permiso no ve estas, aunque tengan agencia (respuesta del 11-nov, punto 4). */
    private static final Set<String> ADMIN_ONLY = Set.of("usuarios", "permisos", "historial_cambios");
    private static final String FILTER = "f.";
    private static final String QUERY_CANCELED = "57014";
    static final String TIMEOUT_MESSAGE = "La consulta tardó más de 5 s: agregue filtros o busque algo más preciso";
    private static final String FROM = ".from";
    private static final String TO = ".to";

    private final TableAccessRepository repository;
    private final TableCatalog catalog;
    private final CurrentAgencyProvider currentAgencyProvider;
    private final SecurityUtils securityUtils;

    public TableAccessServiceImpl(TableAccessRepository repository, TableCatalog catalog,
            CurrentAgencyProvider currentAgencyProvider, SecurityUtils securityUtils) {
        this.repository = repository;
        this.catalog = catalog;
        this.currentAgencyProvider = currentAgencyProvider;
        this.securityUtils = securityUtils;
    }

    /** El Administrador ve todas; el Usuario con el permiso, solo las que tienen agencia y no son de administracion. */
    @Override
    public List<TableInfo> tables() {
        boolean admin = securityUtils.isAdmin();
        return catalog.all().stream().filter(t -> admin || visibleToUser(t)).toList();
    }

    private static boolean visibleToUser(TableInfo table) {
        return table.byAgency() && !ADMIN_ONLY.contains(table.name());
    }

    @Override
    public TablePage rows(String name, Map<String, String> params) {
        boolean admin = securityUtils.isAdmin();
        TableInfo table = catalog.find(name).filter(t -> admin || visibleToUser(t))
                .orElseThrow(() -> new ResourceNotFoundException("La tabla no esta disponible para consulta"));
        String sort = blank(params.get("sort"));
        if (sort != null && table.column(sort) == null) {
            throw new BusinessRuleException("No se puede ordenar por " + sort);
        }
        TableQuery query = new TableQuery(agency(admin, params.get("agency")),
                number(params.get("offset"), 0, Integer.MAX_VALUE, 0), number(params.get("limit"), 1, MAX_LIMIT, DEFAULT_LIMIT),
                sort, ascending(params.get("dir")), blank(params.get("q")), filters(table, params));
        try {
            return repository.rows(table, query);
        } catch (DataAccessException e) {
            if (canceledByTimeout(e)) {
                throw new ApiException(TIMEOUT_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE);
            }
            throw e;
        }
    }

    /** S3: el SELECT se corto por el tope de 5 s (SQLSTATE 57014 de Postgres, query_canceled). */
    private static boolean canceledByTimeout(DataAccessException e) {
        return NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql && QUERY_CANCELED.equals(sql.getSQLState());
    }

    /**
     * RN-03.2: la agencia de trabajo. El Administrador puede pedir otra o todas; al Usuario se le ignora lo que pida
     * y ve siempre la suya.
     */
    private Long agency(boolean admin, String requested) {
        String value = blank(requested);
        if (!admin || value == null) {
            return currentAgencyProvider.currentAgencyId();
        }
        if (ALL_AGENCIES.equals(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Agencia no valida: " + value);
        }
    }

    private static boolean ascending(String dir) {
        String value = blank(dir);
        if (value == null || value.equalsIgnoreCase("desc")) {
            return false;
        }
        if (value.equalsIgnoreCase("asc")) {
            return true;
        }
        throw new BusinessRuleException("Orden no valido: " + value);
    }

    private static List<Filter> filters(TableInfo table, Map<String, String> params) {
        List<Filter> filters = new ArrayList<>();
        params.forEach((key, raw) -> {
            String value = blank(raw);
            if (key.startsWith(FILTER) && value != null) {
                filters.add(filter(table, key.substring(FILTER.length()), value));
            }
        });
        return filters;
    }

    private static Filter filter(TableInfo table, String key, String value) {
        Operator operator = Operator.CONTAINS;
        String name = key;
        if (key.endsWith(FROM)) {
            operator = Operator.FROM;
            name = key.substring(0, key.length() - FROM.length());
        } else if (key.endsWith(TO)) {
            operator = Operator.TO;
            name = key.substring(0, key.length() - TO.length());
        }
        ColumnInfo column = table.column(name);
        if (column == null) {
            throw new BusinessRuleException("La tabla no tiene la columna " + name);
        }
        return new Filter(column, operator, operator == Operator.CONTAINS ? value : bound(column, value));
    }

    /** "desde / hasta" solo para numeros y fechas, y con un valor de ese tipo. */
    private static Object bound(ColumnInfo column, String value) {
        try {
            return switch (column.kind()) {
                case NUMBER -> new BigDecimal(value.replace(',', '.'));
                case DATE -> LocalDate.parse(value);
                default -> throw new BusinessRuleException("La columna " + column.name() + " no admite rango");
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new BusinessRuleException("Valor no valido para " + column.name() + ": " + value);
        }
    }

    private static int number(String raw, int min, int max, int fallback) {
        String value = blank(raw);
        if (value == null) {
            return fallback;
        }
        try {
            return (int) Math.clamp(Long.parseLong(value), min, max);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Numero no valido: " + value);
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
