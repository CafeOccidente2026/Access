package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Action;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Entity;
import com.cafeoccidente.backend.purchases.shared.dto.AgencyRequest;
import com.cafeoccidente.backend.purchases.shared.dto.AgencyResponse;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.service.AgencyService;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgencyServiceImpl implements AgencyService {

    private static final String SELECT = "SELECT a.id, a.nombre,"
            + " EXISTS (SELECT 1 FROM registros_control r WHERE r.agencia_id = a.id AND r.activo) AS has_control_record,"
            + " ps.prefijo FROM agencias a LEFT JOIN prefijos_sugeridos ps ON ps.agencia_id = a.id"
            + " WHERE a.activa";

    private final AgencyRepository agencyRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ChangeHistoryService history;

    public AgencyServiceImpl(AgencyRepository agencyRepository, NamedParameterJdbcTemplate jdbc,
            ChangeHistoryService history) {
        this.agencyRepository = agencyRepository;
        this.jdbc = jdbc;
        this.history = history;
    }

    @Override
    public List<AgencyResponse> list() {
        return jdbc.query(SELECT + " ORDER BY a.id", (rs, i) -> new AgencyResponse(rs.getLong("id"),
                rs.getString("nombre"), rs.getBoolean("has_control_record"), rs.getString("prefijo")));
    }

    @Override
    @Transactional
    public AgencyResponse create(AgencyRequest request) {
        String name = request.name().trim();
        if (agencyRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("Ya existe una agencia con ese nombre");
        }
        Agency agency = new Agency();
        agency.setName(name);
        agency.setActive(true);
        Long id = agencyRepository.saveAndFlush(agency).getId();
        history.record(Entity.AGENCIA, id, Action.CREAR, id, null, Map.of("name", name));
        return new AgencyResponse(id, name, false, null);
    }
}
