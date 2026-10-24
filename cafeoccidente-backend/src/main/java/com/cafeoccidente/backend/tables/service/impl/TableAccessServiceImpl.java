package com.cafeoccidente.backend.tables.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.tables.dto.TablePage;
import com.cafeoccidente.backend.tables.entity.QueryableTable;
import com.cafeoccidente.backend.tables.repository.TableAccessRepository;
import com.cafeoccidente.backend.tables.service.TableAccessService;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TableAccessServiceImpl implements TableAccessService {

    private final TableAccessRepository repository;
    private final CurrentAgencyProvider currentAgencyProvider;

    public TableAccessServiceImpl(TableAccessRepository repository, CurrentAgencyProvider currentAgencyProvider) {
        this.repository = repository;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @Override
    public List<QueryableTable> tables() {
        return List.of(QueryableTable.values());
    }

    /** La agencia de trabajo del Administrador, o la asignada a quien tiene el permiso (RF-02). */
    @Override
    public TablePage page(String code, int page, int size) {
        QueryableTable table = Arrays.stream(QueryableTable.values())
                .filter(t -> t.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("La tabla no esta disponible para consulta"));
        return repository.page(table, currentAgencyProvider.currentAgencyId(), page, size);
    }
}
