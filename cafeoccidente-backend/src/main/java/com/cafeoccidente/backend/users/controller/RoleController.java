package com.cafeoccidente.backend.users.controller;

import com.cafeoccidente.backend.users.dto.RoleResponse;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo lectura: los roles son fijos (ADMIN/USER, sembrados por Flyway), no hay CRUD. */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream()
                .map(role -> new RoleResponse(role.getId(), role.getName()))
                .toList();
    }
}
